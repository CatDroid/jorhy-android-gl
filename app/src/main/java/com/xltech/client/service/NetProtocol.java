package com.xltech.client.service;

import android.app.Activity;

import com.xltech.client.config.Configer;
import com.xltech.client.config.ConfigTempData;
import com.xltech.client.data.*;
import com.xltech.client.ui.ActivityLogin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Created by JooLiu on 2016/1/11.
 */
public class NetProtocol {
    private static NetProtocol instance = null;
    private Queue<Object> taskQueue = null;
    private HashMap<String, Object> playerMap = null;

    private boolean isStop = false;
    private int sequenceNum = 0;
    private Thread workThread = null;
    private long lastBeatTime = 0;

    /// for test
    private Thread testThread = null;
    private boolean testIsRunning = false;

    public static NetProtocol getInstance() {
        if (instance == null) {
            instance = new NetProtocol();
        }

        return instance;
    }

    public NetProtocol() {
        playerMap = new HashMap<String, Object>();
        taskQueue = new LinkedList<Object>();
    }

    public int Login(String strAddress, int nPort, String strUsername, String strPassword, int nForced) {
        if (Configer.UseTemp()) {
            DataLogin.getInstance().setBody(ConfigTempData.getLoginData());
            Activity activity = ManActivitys.getInstance().currentActivity();
            if (activity.getClass() == ActivityLogin.class) {
                ((ActivityLogin)activity).OnLoginReturn();
            }
        } else {
            DataServerInfo.getInstance().setAddress(strAddress);
            DataServerInfo.getInstance().setPort(nPort);

            DataLogin.getInstance().setUsername(strUsername);
            DataLogin.getInstance().setPassword(strPassword);
            DataLogin.getInstance().setForced(nForced);

            synchronized(taskQueue) {
                taskQueue.offer(DataLogin.getInstance());
            }

            if (workThread == null) {
                isStop = false;
                workThread = new WorkThread();
                workThread.start();
            }
        }
        return 0;
    }

    public int Logout() {
        if (Configer.UseTemp()) {
        } else {
            DataNull logout = new DataNull();
            logout.setCommand(EnumProtocol.xl_logout);
            synchronized (taskQueue) {
                taskQueue.offer(logout);
            }
        }

        return 0;
    }

    public int GetCategory() {
        DataCategory.getInstance().cleanElement();
        DataCategory.getInstance().cleanBody();
        if (Configer.UseTemp()) {
            DataCategory.getInstance().setBody(ConfigTempData.getCategoryData());
            DataCategory.getInstance().parse();
        } else {
            DataNull category = new DataNull();
            category.setCommand(EnumProtocol.xl_category);
            category.setSequence(sequenceNum++);
            synchronized (taskQueue) {
                taskQueue.offer(category);
            }
        }

        return 0;
    }

    public String StartReal(AppPlayer player) {
        String strKey = null;
        if (Configer.UseTemp()) {
            if (player.GetFlag() == AppPlayer.LEFT_PALER) {
                strKey = String.valueOf(DataSelectedVehicle.getInstance().getLeftChannel());
            } else {
                strKey = String.valueOf(DataSelectedVehicle.getInstance().getRightChannel());
            }

            DataRealPlay realPlay = new DataRealPlay();
            realPlay.setPlayer(player);
            realPlay.setStrHostID();
            realPlay.setChannel();
            realPlay.setSequence(0);
            realPlay.setFlag(EnumProtocol.xl_ctrl_start);

            synchronized(playerMap) {
                playerMap.put(strKey, realPlay);
            }

            if (testThread == null) {
                testIsRunning = true;
                testThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (testIsRunning) {
                            StructBlock testFrameData = ConfigTempData.getInstance().getBlockData();
                            if (testFrameData != null) {
                                int channel = testFrameData.getChannel(StructBlock.VOD);
                                synchronized(playerMap) {
                                    String strKey = String.valueOf(channel);
                                    DataRealPlay realPlayer = (DataRealPlay) playerMap.get(strKey);
                                    if (realPlayer != null) {
                                        AppPlayer player = realPlayer.getPlayer();
                                        if (player != null) {
                                            player.InputData(testFrameData.blockData(),
                                                    testFrameData.blockSize());
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
                testThread.start();
            }
        } else {
            int sequence = sequenceNum++;
            strKey = String.valueOf(sequence);
            DataRealPlay realPlay = new DataRealPlay();
            realPlay.setPlayer(player);
            realPlay.setStrHostID();
            realPlay.setChannel();
            realPlay.setSequence(sequence);
            realPlay.setFlag(EnumProtocol.xl_ctrl_start);

            synchronized(playerMap) {
                playerMap.put(strKey, realPlay);
            }

            synchronized (taskQueue) {
                taskQueue.offer(realPlay);
            }
        }
        return strKey;
    }

    public int StopReal(AppPlayer player) {
        if (Configer.UseTemp()) {
            ConfigTempData.getInstance().resetXLFile();
            synchronized (playerMap) {
                playerMap.remove(player.GetKey());
            }

            if (playerMap.isEmpty() && testIsRunning == true) {
                try {
                    testIsRunning = false;
                    testThread.join();
                    testThread = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            DataRealPlay realPlay;
            synchronized (playerMap) {
                realPlay = (DataRealPlay)playerMap.get(player.GetKey());
                playerMap.remove(player.GetKey());
            }

            if (realPlay != null) {
                realPlay.setFlag(EnumProtocol.xl_ctrl_stop);
                synchronized (taskQueue) {
                    taskQueue.offer(realPlay);
                }
            }
        }
        return 0;
    }

    private void Heartbeat() {
        if (System.currentTimeMillis() - lastBeatTime > 1000) {
            lastBeatTime = System.currentTimeMillis();
            DataNull heartBeat = new DataNull();
            heartBeat.setCommand(EnumProtocol.xl_heart_beat);
            heartBeat.setSequence(0);
            synchronized (taskQueue) {
                taskQueue.offer(heartBeat);
            }
        }
    }

    private void processData(DataProtocol dataProtocol, ByteBuffer bodyBuffer) {
        if (!dataProtocol.isCorrect()) {
            return;
        }

        switch (dataProtocol.getCommand()) {
            case EnumProtocol.xl_login:
                DataLogin.getInstance().setBody(bodyBuffer.array());
                Activity activity = ManActivitys.getInstance().currentActivity();
                if (activity.getClass() == ActivityLogin.class) {
                    ((ActivityLogin)activity).OnLoginReturn();
                }
                break;
            case EnumProtocol.xl_logout:
                try {
                    isStop = true;
                    if (workThread != null) {
                        workThread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    workThread = null;
                }
                break;
            case EnumProtocol.xl_category:
                if (dataProtocol.getBodyLen() > 0) {
                    DataCategory.getInstance().setBody(bodyBuffer.array());
                }
                if (dataProtocol.getFlag() == EnumProtocol.xl_ctrl_end) {
                    DataCategory.getInstance().parse();
                }
                break;
            case EnumProtocol.xl_real_play:
                synchronized(playerMap) {
                    String strKey = String.valueOf(dataProtocol.getSequence());
                    DataRealPlay dataRealPlay = (DataRealPlay) playerMap.get(strKey);
                    if (dataRealPlay != null) {
                        AppPlayer player = dataRealPlay.getPlayer();
                        if (player != null) {
                            if (dataProtocol.getFlag() == EnumProtocol.xl_ctrl_stream) {
                                player.InputData(bodyBuffer.array(), dataProtocol.getBodyLen());
                            } /*else if (dataProtocol.getFlag() == EnumProtocol.xl_ctrl_end ||
                                dataProtocol.getFlag() == EnumProtocol.xl_ctrl_stop) {
                            playerMap.remove(player.GetKey());
                        }*/
                        }
                    }
                }
                break;
        }
    }

    private class WorkThread extends Thread {
        @Override
        public void run() {
            try {
                DataProtocol dataProtocol = new DataProtocol();
                Selector selector = Selector.open();
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.connect(
                        new InetSocketAddress(DataServerInfo.getInstance().getAddress(),
                                DataServerInfo.getInstance().getPort()));
                socketChannel.configureBlocking(false);
                socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                while (!isStop) {
                   Heartbeat();
                   int nReadyChannels = selector.select();
                    if (nReadyChannels == 0) {
                        continue;
                    }

                    Set selectedKeys = selector.selectedKeys();
                    Iterator keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = (SelectionKey)keyIterator.next();
                        if (key.isReadable()) {
                            while (dataProtocol.getHeaderBuffer().hasRemaining()) {
                                socketChannel.read(dataProtocol.getHeaderBuffer());
                            }

                            ByteBuffer bodyBuffer = ByteBuffer.allocate(dataProtocol.getBodyLen() +
                                    EnumProtocol.TAIL_LEN);
                            while (bodyBuffer.hasRemaining()) {
                                socketChannel.read(bodyBuffer);
                            }

                            processData(dataProtocol, bodyBuffer);
                            dataProtocol.getHeaderBuffer().clear();
                        } else if (key.isWritable()) {
                            Object task = null;
                            synchronized (taskQueue) {
                                if (taskQueue.size() != 0) {
                                    task = taskQueue.poll();
                                }
                            }

                            if (task != null) {
                                ByteBuffer sendData = null;
                                if (task.getClass() == DataLogin.class) {
                                    sendData = DataProtocol.MakeRequest(EnumProtocol.xl_login,
                                            EnumProtocol.xl_ctrl_start, sequenceNum++,
                                            DataLogin.getInstance().getBody(),
                                            DataLogin.getInstance().getBodyLen());
                                } else if (task.getClass() == DataNull.class) {
                                    DataNull dataNull = (DataNull) task;
                                    sendData = DataProtocol.MakeRequest(dataNull.getCommand(),
                                            EnumProtocol.xl_ctrl_start, dataNull.getSequence(),
                                            null, 0);
                                } else if (task.getClass() == DataRealPlay.class) {
                                    DataRealPlay realPlay = (DataRealPlay) task;
                                    sendData = DataProtocol.MakeRequest(EnumProtocol.xl_real_play,
                                            realPlay.getFlag(), realPlay.getSequence(),
                                            realPlay.getBody(), realPlay.getBodyLen());
                                }

                                if (sendData != null) {
                                    socketChannel.write(sendData);
                                }
                            }
                        }
                        keyIterator.remove();
                    }
                }
            } catch(IOException e){
                e.printStackTrace();
            } finally {
                isStop = true;
                workThread = null;
            }
        }
    }
}

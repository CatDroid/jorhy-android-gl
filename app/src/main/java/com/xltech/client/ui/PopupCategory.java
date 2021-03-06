package com.xltech.client.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.xltech.client.data.DataCategory;
import com.xltech.client.data.EnumMessage;
import com.xltech.client.service.NetProtocol;


/**
 * Created by JooLiu on 2016/1/20.
 */
public class PopupCategory extends PopupWindow{
    private View contentView;
    private TextView btnAll = null;
    private TextView btnOnline = null;
    public static TreeViewAdapter treeViewAdapter;
    public static Handler myHandler = null;
    public static String parentName = "";

    public PopupCategory(Context context, int width) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        contentView = inflater.inflate(R.layout.category_list, null);
        contentView.setAlpha(0.95f);

        // 设置SelectPicPopupWindow的View
        this.setContentView(contentView);
        // 设置SelectPicPopupWindow弹出窗体的宽
        this.setWidth(width / 2);
        // 设置SelectPicPopupWindow弹出窗体的高
        this.setHeight(LayoutParams.MATCH_PARENT);
        // 设置SelectPicPopupWindow弹出窗体可点击
        this.setFocusable(true);
        this.setOutsideTouchable(true);
        // 刷新状态
        this.update();
        // 实例化一个ColorDrawable颜色为半透明
        ColorDrawable dw = new ColorDrawable(0000000000);
        // 点back键和其他地方使其消失,设置了这个才能触发OnDismisslistener ，设置其他控件变化等操作
        this.setBackgroundDrawable(dw);
        // mPopupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        // 设置SelectPicPopupWindow弹出窗体动画效果
        this.setAnimationStyle(R.style.AnimBottom);

        ListView treeView = (ListView) contentView.findViewById(R.id.tree_list);
        treeViewAdapter = new TreeViewAdapter(DataCategory.getInstance().getElements(),
                DataCategory.getInstance().getElementsData(), inflater);
        TreeViewItemClickListener treeViewItemClickListener = new TreeViewItemClickListener(treeViewAdapter);
        treeView.setAdapter(treeViewAdapter);
        treeView.setOnItemClickListener(treeViewItemClickListener);

        TextView btnBack = (TextView) contentView.findViewById(R.id.list_back);
        btnBack.setOnClickListener(new TextView.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPopupWindow(null);
            }
        });

        btnAll = (TextView) contentView.findViewById(R.id.list_all);
        btnOnline = (TextView) contentView.findViewById(R.id.list_online);

        //DataCategory.getInstance().showAll(true);
        btnAll.setOnClickListener(new TextView.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View view) {
                DataCategory.getInstance().showAll(true);
                OnRefreshPopupWindow();
                view.setBackgroundResource(R.drawable.select);
                btnOnline.setBackground(null);
            }
        });

        btnOnline.setOnClickListener(new TextView.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View view) {
                DataCategory.getInstance().showAll(false);
                OnRefreshPopupWindow();
                view.setBackgroundResource(R.drawable.select);
                btnAll.setBackground(null);
            }
        });
        myHandler = new Handler() {
            //接收到消息后处理
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case EnumMessage.REFRESH_CATEGORY:
                        OnRefreshPopupWindow();
                        break;
                }
                super.handleMessage(msg);
            }
        };
        NetProtocol.getInstance().GetCategory();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void showPopupWindow(View parent) {
        if (!this.isShowing() && parent != null) {
            if (DataCategory.getInstance().isShowAll()) {
                btnAll.setBackgroundResource(R.drawable.select);
                btnOnline.setBackground(null);
            } else {
                btnOnline.setBackgroundResource(R.drawable.select);
                btnAll.setBackground(null);
            }
            this.showAtLocation(parent, Gravity.LEFT, 0, 0);
            OnRefreshPopupWindow();
        } else {
            this.dismiss();
        }
    }

    public void OnRefreshPopupWindow() {
        ListView treeView = (ListView) contentView.findViewById(R.id.tree_list);
        ((TreeViewItemClickListener)treeView.getOnItemClickListener()).refreshItems();
    }
}

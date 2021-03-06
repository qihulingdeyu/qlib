package com.qing.qlib;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.LinearLayout;

/**
 * Created by zwq on 2015/09/24 11:11.<br/><br/>
 */
public abstract class LinearLayoutPage extends LinearLayout implements IPage {

    protected static final String TAG = LinearLayoutPage.class.getName();
    protected Context mContext;

    public LinearLayoutPage(Context context) {
        this(context, null);
    }

    public LinearLayoutPage(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LinearLayoutPage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        initView();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LinearLayoutPage(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
    }

    protected abstract void initView();

    @Override
    public Object[] transferPageData() {
        return null;
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    @Override
    public boolean onActivityKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onActivityKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }
}

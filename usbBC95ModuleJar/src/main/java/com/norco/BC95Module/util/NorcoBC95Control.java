package com.norco.BC95Module.util;

import android.content.Context;
import android.hardware.usb.UsbManager;

public class NorcoBC95Control {

    private Context mContext;
    private NorcoBC95ControlReal mNorcoBC95ControlReal;

    //构造函数
    public NorcoBC95Control(Context context)
    {
        mContext = context;
        mNorcoBC95ControlReal = new NorcoBC95ControlReal(mContext);
    }

    public boolean haveBC95Devices(int vid,int pid)
    {

        return mNorcoBC95ControlReal.haveBC95Devices(vid,pid);
    }

    public boolean  writeATCommandString(String cmd)
    {
        return mNorcoBC95ControlReal.writeATCommandString(cmd);
    }

    public boolean  writeATCommandByte(byte[] data)
    {
        return mNorcoBC95ControlReal.writeATCommandByte(data);
    }

    //获取jar库的版本号
    public String getJarVersion()
    {
        return mNorcoBC95ControlReal.getJarVersion();
    }

    //获取最后一次数据读取到的byte数组
    public byte[] getLastReadDataByte()
    {
        return mNorcoBC95ControlReal.getLastReadDataByte();
    }

    //获取最后一次数据读取到的String
    public String getLastReadDataString()
    {
        return mNorcoBC95ControlReal.getLastReadDataString();
    }

    //获取程序执行后的结果信息
    public int getLastErrorCode()
    {
        return mNorcoBC95ControlReal.getLastErrorCode();
    }

    //获取程序执行后的结果信息
    public String getLastErrorString()
    {
        return mNorcoBC95ControlReal.getLastErrorString();
    }

}

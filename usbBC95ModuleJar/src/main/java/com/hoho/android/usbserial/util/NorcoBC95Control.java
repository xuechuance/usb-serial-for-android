package com.hoho.android.usbserial.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NorcoBC95Control {

    private final String TAG = "NorcoBC95Control";

    private String version  = "V1.0.0";

    private static final int READ_WAIT_MILLIS = 200;
    private static final int BUFSIZ = 4096;
    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);

    private Context mContext = null;
    private UsbManager mUsbManager;

    private SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private UsbSerialPort mUsbSerialPort;
    private ReadSerialThread  usbSerialRead = new ReadSerialThread();

    private List<UsbSerialPort> mEntries = new ArrayList<UsbSerialPort>();

    List<UsbSerialPort> mBC95Ports = new ArrayList<UsbSerialPort>();

    //查询出错信息
    private int errorCode_haveBC95Devices_01 = 1001;
    private final String errorString_haveBC95Devices_01 = "Dont have Bc95 module devices !";

    private int errorCode_writeCmd_01 = 2001;
    private final String errorString_writeCmd_01 = " Did not find BC95 devices, Please find it first !";

    private int errorCode_writeCmd_02 = 2002;
    private final String errorString_writeCmd_02 = " Connect BC95 devices failed !";

    private int errorCode_writeCmd_03 = 2003;
    private final String errorString_writeCmd_03 = " Open BC95 devices failed !";

    private int lastErrorCode = 0;
    private String lastErrorString = "";

    //查询数据读的结果
    private byte[] mLastReadDataByte = null;
    private String mLastReadDataString;

    //调试开关
    private boolean debug = false;
    private void logd(String TAG,String msg)
    {
        if(debug)
        {
            Log.d(TAG, msg);
        }
    }


    //构造函数
    public NorcoBC95Control(Context context)
    {
        mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        lastErrorCode = 0;
        lastErrorString = "";
    }


    //判断是否存在设备
    public boolean haveBC95Devices(int vid, int pid)
    {
        boolean ret = false;
        final List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            logd(TAG, String.format("+ %s: %s port %s", driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            //通过判断pid和vid来判断是否找到了特定的设备
            int vendorId = driver.getDevice().getVendorId();
            int productId = driver.getDevice().getProductId();
            if( vendorId == vid && productId == pid ) {

                //保存BC95模块的port信息
                List<UsbSerialPort> mports = driver.getPorts();
                mBC95Ports.clear();
                mBC95Ports.addAll(mports);

                ret = true;
                lastErrorCode = 0;
                lastErrorString = "";
            }else{
                //由于查询到设备后，循环不break掉，所以要先判断一下
                if(!ret)
                {
                    lastErrorCode = errorCode_haveBC95Devices_01;
                    lastErrorString = errorString_haveBC95Devices_01;
                }
            }

            result.addAll(ports);
        }

        mEntries.clear();
        mEntries.addAll(result);

        return ret;

    }


    //写String类型的命令
    public boolean  writeATCommandString(String cmd)
    {
        synchronized (this) {

            if( mBC95Ports.size() == 0 || mBC95Ports.size() > 1 )
            {
                lastErrorCode = errorCode_writeCmd_01;
                lastErrorString = errorString_writeCmd_01;

                return false;
            }

            //获得UsbSerialPort
            mUsbSerialPort = mBC95Ports.get(0);

            //建立连接
            UsbDeviceConnection connection = mUsbManager.openDevice(mUsbSerialPort.getDriver().getDevice());
            if (connection == null) {

                lastErrorCode = errorCode_writeCmd_02;
                lastErrorString = errorString_writeCmd_02;
                return false;
            }

            try {
                //打开usb设备
                mUsbSerialPort.open(connection);
                mUsbSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                usbSerialRead.init();
                usbSerialRead.start();
                int writeCount = mUsbSerialPort.write(cmd.getBytes("UTF8"),100);
                SystemClock.sleep(200);
                usbSerialRead.exit();
                //等待数据读取线程终止
                while(usbSerialRead.isThreadRuning());

                lastErrorCode = 0;
                lastErrorString = "";
                return true;
            } catch (IOException e) {

                Log.e(TAG, "Error setting up device: " + e.getMessage());

                lastErrorCode = errorCode_writeCmd_03;
                lastErrorString = errorString_writeCmd_03;
                return false;
            }finally {
                try {
                    mUsbSerialPort.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error close device: " + e.getMessage());
                }
                mUsbSerialPort = null;
            }

        }

    }


    //写byte数据
    public boolean  writeATCommandByte(byte[] data)
    {
        synchronized (this) {

            if( mBC95Ports.size() == 0 || mBC95Ports.size() > 1 )
            {
                lastErrorCode = errorCode_writeCmd_01;
                lastErrorString = errorString_writeCmd_01;

                return false;
            }

            //获得UsbSerialPort
            mUsbSerialPort = mBC95Ports.get(0);

            //建立连接
            UsbDeviceConnection connection = mUsbManager.openDevice(mUsbSerialPort.getDriver().getDevice());
            if (connection == null) {

                lastErrorCode = errorCode_writeCmd_02;
                lastErrorString = errorString_writeCmd_02;
                return false;
            }

            try {
                //打开usb设备
                mUsbSerialPort.open(connection);
                mUsbSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                usbSerialRead.init();
                usbSerialRead.start();
                int writeCount = mUsbSerialPort.write(data,100);
                SystemClock.sleep(200);
                usbSerialRead.exit();
                //等待数据读取线程终止
                while(usbSerialRead.isThreadRuning());

                lastErrorCode = 0;
                lastErrorString = "";
                return true;
            } catch (IOException e) {

                Log.e(TAG, "Error setting up device: " + e.getMessage());

                lastErrorCode = errorCode_writeCmd_03;
                lastErrorString = errorString_writeCmd_03;
                return false;
            }finally {
                try {
                    mUsbSerialPort.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error close device: " + e.getMessage());
                }
                mUsbSerialPort = null;
            }

        }

    }

    //获取jar库的版本号
    public String getJarVersion()
    {
        return version;
    }

    //获取最后一次数据读取到的byte数组
    public byte[] getLastReadDataByte()
    {
        return mLastReadDataByte;
    }

    //获取最后一次数据读取到的String
    public String getLastReadDataString()
    {
        return mLastReadDataString;
    }

    //数据读取线程
    public class ReadSerialThread extends Thread {

        private boolean running = true;

        public void run() {

            mLastReadDataByte = null;
            mLastReadDataString = "";

            while (running) {

                int len = 0;
                try {
                    len = mUsbSerialPort.read(mReadBuffer.array(), READ_WAIT_MILLIS);
                    if (len > 0) {

                        mLastReadDataByte = new byte[len];
                        mReadBuffer.get(mLastReadDataByte, 0, len);
                        mLastReadDataString = new String(mLastReadDataByte);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }

                SystemClock.sleep(200);
            }
        }

        //查询线程是否正在运行
        public boolean isThreadRuning()
        {
            return running;
        }

        //线程初始化
        public void init()
        {
            running = true;
        }

        //线程退出
        public void exit()
        {
            running = false;
        }

    }






}

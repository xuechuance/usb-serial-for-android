/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.norco.BC95Module.examples;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import com.norco.BC95Module.driver.UsbSerialDriver;
import com.norco.BC95Module.driver.UsbSerialPort;
import com.norco.BC95Module.driver.UsbSerialProber;
import com.norco.BC95Module.util.HexDump;
import com.norco.BC95Module.util.NorcoBC95Control;


import java.util.ArrayList;
import java.util.List;

/**
 * Shows a {@link ListView} of available USB devices.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class DeviceListActivity extends Activity {

    private final String TAG = DeviceListActivity.class.getSimpleName();

    private Context mContext;

    private UsbManager mUsbManager;
    private ListView mListView;
    private TextView mProgressBarTitle;
    private ProgressBar mProgressBar;

    private Button mTestWriteButton;

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    refreshDeviceList();

                    //每次接收到消息后，延迟 REFRESH_TIMEOUT_MILLIS 后重新进行一次刷新操作
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    };

    private List<UsbSerialPort> mEntries = new ArrayList<UsbSerialPort>();
    private ArrayAdapter<UsbSerialPort> mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mContext = getApplicationContext();
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mListView = (ListView) findViewById(R.id.deviceList);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mProgressBarTitle = (TextView) findViewById(R.id.progressBarTitle);
        mTestWriteButton = (Button) findViewById(R.id.write_button);

        mTestWriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //在线程中执行耗时操作
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        NorcoBC95Control mNorcoBC95Control = new NorcoBC95Control(mContext);
                        mNorcoBC95Control.haveBC95Devices(0x1a86,0x7523);
                        mNorcoBC95Control.writeATCommandString("AT+CSQ\r\n");

                        byte[] resultByte = mNorcoBC95Control.getLastReadDataByte();
                        String resultString = mNorcoBC95Control.getLastReadDataString();
                        if(resultByte != null)
                        {
                            Log.d(TAG, "resultByte: "+ HexDump.toHexString(resultByte));
                        }

                        Log.d(TAG, "resultString: "+resultString);
                        Log.d(TAG,"LastErrorCode: "+ mNorcoBC95Control.getLastErrorCode());
                        Log.d(TAG,"LastErrorString: "+ mNorcoBC95Control.getLastErrorString());
                        
                    }
                }).start();
            }
        });

        mAdapter = new ArrayAdapter<UsbSerialPort>(this,
                android.R.layout.simple_expandable_list_item_2, mEntries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TwoLineListItem row;
                if (convertView == null){
                    final LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    row = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = (TwoLineListItem) convertView;
                }

                final UsbSerialPort port = mEntries.get(position);
                final UsbSerialDriver driver = port.getDriver();
                final UsbDevice device = driver.getDevice();

                final String title = String.format("Vendor %s Product %s",
                        HexDump.toHexString((short) device.getVendorId()),
                        HexDump.toHexString((short) device.getProductId()));
                row.getText1().setText(title);

                final String subtitle = driver.getClass().getSimpleName();
                row.getText2().setText(subtitle);

                return row;
            }

        };
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Pressed item " + position);
                if (position >= mEntries.size()) {
                    Log.w(TAG, "Illegal position.");
                    return;
                }

                final UsbSerialPort port = mEntries.get(position);
                showConsoleActivity(port);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        //暂且不启用系统自带
        //mHandler.sendEmptyMessage(MESSAGE_REFRESH);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeMessages(MESSAGE_REFRESH);
    }

    @SuppressLint("StaticFieldLeak")
    private void refreshDeviceList() {
        showProgressBar();

        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                Log.d(TAG, "Refreshing device list ...");
                SystemClock.sleep(1000);

                final List<UsbSerialDriver> drivers =
                        UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                for (final UsbSerialDriver driver : drivers) {
                    final List<UsbSerialPort> ports = driver.getPorts();
                    Log.d(TAG, String.format("+ %s: %s port%s",
                            driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
                    result.addAll(ports);
                }

                return result;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> result) {
                mEntries.clear();
                mEntries.addAll(result);
                mAdapter.notifyDataSetChanged();
                mProgressBarTitle.setText(
                        String.format("%s device(s) found",Integer.valueOf(mEntries.size())));
                hideProgressBar();
                Log.d(TAG, "Done refreshing, " + mEntries.size() + " entries found.");
            }

        }.execute((Void) null);
    }

    private void showProgressBar() {
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBarTitle.setText(R.string.refreshing);
    }

    private void hideProgressBar() {
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    private void showConsoleActivity(UsbSerialPort port) {
        SerialConsoleActivity.show(this, port);
    }

}

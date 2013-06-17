package com.witon.uidReader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.acs.smartcard.Reader;

import android.app.Activity;
import android.app.PendingIntent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private UsbManager mManager;
    private Reader mReader;
    private PendingIntent mPermissionIntent;
    private ArrayAdapter<String> mReaderAdapter;
    private ArrayAdapter<String> mSlotAdapter;

    private TextView logView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        logView = (TextView)findViewById(R.id.log);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) { //when you open the USB device
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Open reader
                            logMsg("Opening reader: " + device.getDeviceName()
                                    + "...");
                            new OpenTask().execute(device);
                        }
                    } else {
                        logMsg("Permission denied for device "
                                + device.getDeviceName());
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {  //detach the USB device
                synchronized (this) {
                    // Update reader list
                    mReaderAdapter.clear();
                    for (UsbDevice device : mManager.getDeviceList().values()) {
                        if (mReader.isSupported(device)) {
                            mReaderAdapter.add(device.getDeviceName());
                        }
                    }
                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && device.equals(mReader.getDevice())) {
                        // Close reader
                        logMsg("Closing reader...");
                        new CloseTask().execute();
                    }
                }
            }
        }
    };
    private class OpenTask extends AsyncTask<UsbDevice, Void, Exception> {
        @Override
        protected Exception doInBackground(UsbDevice... params) {
            Exception result = null;
            try {
                mReader.open(params[0]);
            } catch (Exception e) {
                result = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                logMsg(result.toString());
            } else {
                logMsg("Reader name: " + mReader.getReaderName());
                int numSlots = mReader.getNumSlots();
                logMsg("Number of slots: " + numSlots);
                // Add slot items
                mSlotAdapter.clear();
                for (int i = 0; i < numSlots; i++) {
                    mSlotAdapter.add(Integer.toString(i));
                }
            }
        }
    }
    private class CloseTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            mReader.close();
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
        }
    }

    private void logMsg(String msg) {
        DateFormat dateFormat = new SimpleDateFormat("[dd-MM-yyyy HH:mm:ss]: ");
        Date date = new Date();

        logView.setText(dateFormat.format(date) + msg);
        Log.i("chenguichun", msg);
    }
}

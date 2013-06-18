package com.witon.uidReader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.AsyncTask;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.acs.smartcard.Features;
import com.acs.smartcard.PinProperties;
import com.acs.smartcard.Reader;
import com.acs.smartcard.TlvProperties;

import android.app.Activity;
import android.app.PendingIntent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class Main extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int MAX_LINES = 6;
    private UsbManager mManager;
    private Reader mReader;
    private PendingIntent mPermissionIntent;
    private static final String[] stateStrings = { "Unknown", "Absent",
            "Present", "Swallowed", "Powered", "Negotiable", "Specific" };

    private TextView logView, msgView;
    private String mReaderDeviceName;   //Only one USB device inserted
    private int mSlotNum = 0;               //default slot num is 0
    private int mPowerAction = Reader.CARD_WARM_RESET;           //default mPowerAction
    private Button mOpenButton,mCloseButton, mStartButton;
    private static final String[] powerActionStrings = { "Power Down",
            "Cold Reset", "Warm Reset" };


    //Different states for reading the key automatically
    private static final int STATE_NONE = 0;
    private static final int STATE_KEY_PRESENT = 1;
    private static final int STATE_POWER_ON = 2;
    private int mReaderState = 0;
    private byte[] mUID;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        logView = (TextView)findViewById(R.id.log);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMaxLines(MAX_LINES);
        logView.setText("");
        msgView = (TextView)findViewById(R.id.msg);
        mUID = new byte[8];

        // Get USB manager
        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        // Initialize reader
        mReader = new Reader(mManager);
        mReader.setOnStateChangeListener(new Reader.OnStateChangeListener() {
            @Override
            public void onStateChange(int slotNum, int prevState, int currState) {
                if (prevState < Reader.CARD_UNKNOWN
                        || prevState > Reader.CARD_SPECIFIC) {
                    prevState = Reader.CARD_UNKNOWN;
                }
                if (currState < Reader.CARD_UNKNOWN
                        || currState > Reader.CARD_SPECIFIC) {
                    currState = Reader.CARD_UNKNOWN;
                }
                if(stateStrings[currState].equals("Present")){
                    mReaderState = STATE_KEY_PRESENT;
                }
                if(stateStrings[currState].equals("Absent")){
                    mReaderState = STATE_NONE;
                }

                // Create output string
                final String outputString = "Slot " + slotNum + ": "
                        + stateStrings[prevState] + " -> "
                        + stateStrings[currState];
                // Show output
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        logMsg(outputString);
                    }
                });
            }
        });

        // Register receiver for USB permission
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);

        for (UsbDevice device : mManager.getDeviceList().values()) {
            if (mReader.isSupported(device)) {
                mReaderDeviceName = device.getDeviceName();
            }
        }

        // Initialize open button
        mOpenButton = (Button) findViewById(R.id.button_openUSB);
        mOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean requested = false;
                // Disable open button
                mOpenButton.setEnabled(false);
                String deviceName = mReaderDeviceName;
                logMsg("open:" + deviceName);
                if (deviceName != null) {
                    // For each device
                    for (UsbDevice device : mManager.getDeviceList().values()) {
                        // If device name is found
                        if (deviceName.equals(device.getDeviceName())) {
                            // Request permission
                            mManager.requestPermission(device,
                                    mPermissionIntent);
                            requested = true;


                            break;
                        }
                    }
                }

                if (!requested) {
                    // Enable open button
                    mOpenButton.setEnabled(false);
                    mCloseButton.setEnabled(true);
                    mStartButton.setEnabled(true);
                }
            }
        });

        // Initialize close button
        mCloseButton = (Button) findViewById(R.id.button_closeUSB);
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOpenButton.setEnabled(true);
                mCloseButton.setEnabled(false);
                mStartButton.setEnabled(false);
                // Close reader
                logMsg("Closing reader...");
                new CloseTask().execute();
            }
        });

        // Initialize power button
        mStartButton = (Button) findViewById(R.id.button_start);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //power the reader
                // Set parameters
                PowerParams params = new PowerParams();
                params.slotNum = mSlotNum;
                params.action = mPowerAction;
                // Perform power action
                logMsg("Slot " + mSlotNum + ": "
                        + powerActionStrings[mPowerAction] + "...");
                new PowerTask().execute(params);

                setProtocol();
            }
        });

        // Initialize getUID button
        Button mGetUID = (Button) findViewById(R.id.button_getUID);
        mGetUID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getUID();
            }
        });

        // Initialize authentication button
        Button mAuthen = (Button) findViewById(R.id.button_authen);
        mAuthen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authentication();
            }
        });

        // Initialize writeKey button
        Button mWriteKey = (Button) findViewById(R.id.button_writeKey);
        mWriteKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeKey();
            }
        });
    }

    private void setProtocol(){
        int preferredProtocols = Reader.PROTOCOL_UNDEFINED;
        String preferredProtocolsString = "";

        preferredProtocols |= Reader.PROTOCOL_T0;
        preferredProtocolsString = "T=0";
        preferredProtocols |= Reader.PROTOCOL_T1;
        if (preferredProtocolsString != "") {
            preferredProtocolsString += "/";
        }
        preferredProtocolsString += "T=1";

        if (preferredProtocolsString == "") {
            preferredProtocolsString = "None";
        }

        // Set Parameters
        SetProtocolParams params = new SetProtocolParams();
        params.slotNum = mSlotNum;
        params.preferredProtocols = preferredProtocols;

        // Set protocol
        logMsg("Slot " + mSlotNum + ": Setting protocol to "
                + preferredProtocolsString + "...");
        new SetProtocolTask().execute(params);
    }

    private void getUID(){
        // Set parameters
        TransmitParams params = new TransmitParams();
        params.slotNum = mSlotNum;
        params.controlCode = -1;
        params.commandString = "FFCA000000";

        // Transmit APDU
        logMsg("Slot " + mSlotNum + ": Transmitting APDU...");
        new TransmitTask().execute(params);
    }

    private void authentication(){
        // Set parameters
        TransmitParams params = new TransmitParams();
        params.slotNum = mSlotNum;
        params.controlCode = -1;
        logMsg("LoadKeys");
        params.commandString = "FF82000006201220132014";
        // Transmit APDU
        logMsg("Slot " + mSlotNum + ": Transmitting APDU...");
        new TransmitTask().execute(params);

        logMsg("Authentication3");
        params.commandString = "FF8600000501000F6000";
        // Transmit APDU
        logMsg("Slot " + mSlotNum + ": Transmitting APDU...");
        new TransmitTask().execute(params);
    }

    private void writeKey(){
        // Set parameters
        TransmitParams params = new TransmitParams();
        params.slotNum = mSlotNum;
        params.controlCode = -1;
        logMsg("LoadKeys");
        params.commandString = "FF82000006FFFFFFFFFFFF";
        // Transmit APDU
        logMsg("Slot " + mSlotNum + ": Transmitting APDU...");
        new TransmitTask().execute(params);

        logMsg("Authentication3");
        params.commandString = "FF8600000501000F6000";
        // Transmit APDU
        logMsg("Slot " + mSlotNum + ": Transmitting APDU...");
        new TransmitTask().execute(params);
        logMsg("Write sector3");
        params.commandString = "FFD6000F10201220132014078069FFFFFFFFFFFF";
        // Transmit APDU
        logMsg("Slot " + mSlotNum + ": Transmitting APDU...");
        new TransmitTask().execute(params);

    }

    private class PowerParams {
        public int slotNum;
        public int action;
    }
    private class PowerResult {
        public byte[] atr;
        public Exception e;
    }
    private class PowerTask extends AsyncTask<PowerParams, Void, PowerResult> {
        @Override
        protected PowerResult doInBackground(PowerParams... params) {
            PowerResult result = new PowerResult();
            try {
                result.atr = mReader.power(params[0].slotNum, params[0].action);
            } catch (Exception e) {
                result.e = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(PowerResult result) {
            if (result.e != null) {
                logMsg(result.e.toString());
            } else {
                // Show ATR
                if (result.atr != null) {
                    logMsg("ATR:");
                    logBuffer(result.atr, result.atr.length);
                } else {
                    logMsg("ATR: None");
                }
            }
        }
    }
    private class SetProtocolParams {

        public int slotNum;
        public int preferredProtocols;
    }

    private class SetProtocolResult {

        public int activeProtocol;
        public Exception e;
    }

    private class SetProtocolTask extends
            AsyncTask<SetProtocolParams, Void, SetProtocolResult> {

        @Override
        protected SetProtocolResult doInBackground(SetProtocolParams... params) {
            SetProtocolResult result = new SetProtocolResult();
            try {
                result.activeProtocol = mReader.setProtocol(params[0].slotNum,
                        params[0].preferredProtocols);
            } catch (Exception e) {
                result.e = e;
            }

            return result;
        }

        @Override
        protected void onPostExecute(SetProtocolResult result) {
            if (result.e != null) {
                logMsg(result.e.toString());
            } else {
                String activeProtocolString = "Active Protocol: ";
                switch (result.activeProtocol) {
                    case Reader.PROTOCOL_T0:
                        activeProtocolString += "T=0";
                        break;
                    case Reader.PROTOCOL_T1:
                        activeProtocolString += "T=1";
                        break;
                    default:
                        activeProtocolString += "Unknown";
                        break;
                }
                // Show active protocol
                logMsg(activeProtocolString);
            }
        }
    }

    private class TransmitParams {
        public int slotNum;
        public int controlCode;
        public String commandString;
    }

    private class TransmitProgress {
        public int controlCode;
        public byte[] command;
        public int commandLength;
        public byte[] response;
        public int responseLength;
        public Exception e;
    }

    private class TransmitTask extends
            AsyncTask<TransmitParams, TransmitProgress, Void> {
        @Override
        protected Void doInBackground(TransmitParams... params) {
            TransmitProgress progress = new TransmitProgress();
            byte[] command;
            byte[] response = new byte[300];
            int responseLength;
            int foundIndex;
            int startIndex = 0;

            do {
                // Find carriage return
                foundIndex = params[0].commandString.indexOf('\n', startIndex);
                if (foundIndex >= 0) {
                    command = toByteArray(params[0].commandString.substring(
                            startIndex, foundIndex));
                } else {
                    command = toByteArray(params[0].commandString
                            .substring(startIndex));
                }

                // Set next start index
                startIndex = foundIndex + 1;
                progress.controlCode = params[0].controlCode;
                try {
                    if (params[0].controlCode < 0) {
                        // Transmit APDU
                        responseLength = mReader.transmit(params[0].slotNum,
                                command, command.length, response,
                                response.length);
                    } else {
                        // Transmit control command
                        responseLength = mReader.control(params[0].slotNum,
                                params[0].controlCode, command, command.length,
                                response, response.length);
                    }

                    progress.command = command;
                    progress.commandLength = command.length;
                    progress.response = response;
                    progress.responseLength = responseLength;
                    progress.e = null;

                } catch (Exception e) {
                    progress.command = null;
                    progress.commandLength = 0;
                    progress.response = null;
                    progress.responseLength = 0;
                    progress.e = e;
                }

                publishProgress(progress);

            } while (foundIndex >= 0);
            return null;
        }
        @Override
        protected void onProgressUpdate(TransmitProgress... progress) {
            if (progress[0].e != null) {
                logMsg(progress[0].e.toString());
            } else {
                logMsg("Command:");
                logBuffer(progress[0].command, progress[0].commandLength);

                logMsg("Response:");
                logBuffer(progress[0].response, progress[0].responseLength);

                checkCommand(progress[0].command, progress[0].response);
            }
        }

    }

    /**
     * Check if the authentication process success, if succeed, print the UID
     * @param command
     * @param response
     */
    private void checkCommand(byte[] command, byte[] response){
        //get UID
        byte[] input = toByteArray("FFCA000000");
        if(Arrays.equals(input, command)){
            for(int i=0; i<8; i++){
                mUID[i] = response[i];
            }
            return;
        }
        //check authentication result
        byte[] input2 = toByteArray("FF8600000501000F6000");
        if(Arrays.equals(input2, command)){
            byte[] input3 = {9,0,0,0};
            if(Arrays.equals(input3, response)){
                msgView.setText("Authentication Succeed! UID=" + mUID);
            }else{
                msgView.setText("Authentication Failed! UID=" + mUID);
            }

        }
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
                    for (UsbDevice device : mManager.getDeviceList().values()) {
                        if (mReader.isSupported(device)) {
                            mReaderDeviceName = device.getDeviceName();
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
                mSlotNum = numSlots-1;
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
        String oldMsg = logView.getText().toString();
        logView.setText(oldMsg + "\n" + dateFormat.format(date) + msg);
        if (logView.getLineCount() > MAX_LINES) {
            logView.scrollTo(0,
                    (logView.getLineCount() - MAX_LINES)
                            * logView.getLineHeight());
        }
        Log.i("chenguichun", msg);
    }
    /**
     * Logs the contents of buffer.
     *
     * @param buffer
     *            the buffer.
     * @param bufferLength
     *            the buffer length.
     */
    private void logBuffer(byte[] buffer, int bufferLength) {
        String bufferString = "";
        for (int i = 0; i < bufferLength; i++) {
            String hexChar = Integer.toHexString(buffer[i] & 0xFF);
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }

            if (i % 16 == 0) {
                if (bufferString != "") {
                    logMsg(bufferString);
                    bufferString = "";
                }
            }
            bufferString += hexChar.toUpperCase() + " ";
        }

        if (bufferString != "") {
            logMsg(bufferString);
        }
    }

    /**
     * Converts the HEX string to byte array.
     *
     * @param hexString
     *            the HEX string.
     * @return the byte array.
     */
    private byte[] toByteArray(String hexString) {
        int hexStringLength = hexString.length();
        byte[] byteArray = null;
        int count = 0;
        char c;
        int i;

        // Count number of hex characters
        for (i = 0; i < hexStringLength; i++) {
            c = hexString.charAt(i);
            if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                    && c <= 'f') {
                count++;
            }
        }

        byteArray = new byte[(count + 1) / 2];
        boolean first = true;
        int len = 0;
        int value;
        for (i = 0; i < hexStringLength; i++) {
            c = hexString.charAt(i);
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                value = c - 'A' + 10;
            } else if (c >= 'a' && c <= 'f') {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }

            if (value >= 0) {
                if (first) {
                    byteArray[len] = (byte) (value << 4);
                } else {
                    byteArray[len] |= value;
                    len++;
                }
                first = !first;
            }
        }
        return byteArray;
    }

    /**
     * Converts the integer to HEX string.
     *
     * @param i
     *            the integer.
     * @return the HEX string.
     */
    private String toHexString(int i) {
        String hexString = Integer.toHexString(i);
        if (hexString.length() % 2 != 0) {
            hexString = "0" + hexString;
        }
        return hexString.toUpperCase();
    }

    /**
     * Converts the byte array to HEX string.
     *
     * @param buffer
     *            the buffer.
     * @return the HEX string.
     */
    private String toHexString(byte[] buffer) {
        String bufferString = "";
        for (int i = 0; i < buffer.length; i++) {

            String hexChar = Integer.toHexString(buffer[i] & 0xFF);
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }
            bufferString += hexChar.toUpperCase() + " ";
        }

        return bufferString;
    }

    @Override
    protected void onDestroy() {
        // Close reader
        mReader.close();
        // Unregister receiver
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
}

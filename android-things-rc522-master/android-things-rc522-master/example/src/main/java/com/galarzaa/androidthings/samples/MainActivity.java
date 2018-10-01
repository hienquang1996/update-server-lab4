package com.galarzaa.androidthings.samples;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.galarzaa.androidthings.samples.MVVM.VM.NPNHomeViewModel;
import com.galarzaa.androidthings.samples.MVVM.View.NPNHomeView;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import static android.content.ContentValues.TAG;
import static junit.framework.Assert.assertEquals;

public class MainActivity extends Activity implements NPNHomeView, TextToSpeech.OnInitListener {
    private Rc522 mRc522;
    RfidTask mRfidTask;
    private TextView mTagDetectedView;
    private TextView mTagUidView;
    private TextView mTagResultsView;
    private Button button;

    private TextView start;

    private static final int CHUNK_SIZE = 512;

    private double dblSlope = 16.3;
    private double dblIntercept = 0;

    private static final String TAG = "NPNIoTs";
    private int DATA_CHECKING = 0;
    private TextToSpeech niceTTS;

    NPNHomeViewModel mHomeViewModel; //Request server object


    private SpiDevice spiDevice;
    private Gpio gpioReset;

    private static final String SPI_PORT = "SPI0.0";
    private static final String PIN_RESET = "BCM25";

    private Gpio led1;
    private static String pin1 = "BCM6";
    private Gpio led2;
    private static String pin2 = "BCM5";

    private Gpio led3;
    private static String pin3 = "BCM13";
    boolean s3 = false;
    private Handler mHandler = new Handler();
    private static int b = 100;

    String resultsText = "";

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //do they have the data
        if (requestCode == DATA_CHECKING) {
            //yep - go ahead and instantiate
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
                niceTTS = new TextToSpeech(this, this);
                //no data, prompt to install it
            else {
                Intent promptInstall = new Intent();
                promptInstall.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(promptInstall);
            }
        }
    }

    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            niceTTS.setLanguage(Locale.forLanguageTag("VI"));
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        start = (TextView) findViewById(R.id.start);

        mTagDetectedView = (TextView)findViewById(R.id.tag_read);
        mTagUidView = (TextView)findViewById(R.id.tag_uid);
        mTagResultsView = (TextView) findViewById(R.id.tag_results);
        button = (Button)findViewById(R.id.button);

        Log.d(TAG, "gggggggggggggggggg2");

        mHomeViewModel = new NPNHomeViewModel();
        mHomeViewModel.attach(this, this);
        Log.d(TAG, "gggggggggggggggggg");

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        //create an Intent
        Intent checkData = new Intent();
        //set it up to check for tts data
        checkData.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        //start it so that it returns the result
        startActivityForResult(checkData, DATA_CHECKING);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRfidTask = new RfidTask(mRc522);
                mRfidTask.execute();
                ((Button)v).setText(R.string.reading);
                if(button == v){
                    try {
                        led1.setValue(true);
                        led2.setValue(false);
                        mHandler.removeCallbacks(mBlinkRunnable);
                        led3.setValue(false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        PeripheralManager pioService = PeripheralManager.getInstance();
        try {

            led1 = pioService.openGpio(pin1);
            led1.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            led2 = pioService.openGpio(pin2);
            led2.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            led3 = pioService.openGpio(pin3);
            led3.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            spiDevice = pioService.openSpiDevice(SPI_PORT);
            gpioReset = pioService.openGpio(PIN_RESET);
            mRc522 = new Rc522(spiDevice, gpioReset);
            mRc522.setDebugging(true);
        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove pending blink Runnable from the handler.
        mHandler.removeCallbacks(mBlinkRunnable);

        try{
            if(spiDevice != null){
                spiDevice.close();
                led1.close();
                led2.close();
                led3.close();
            }
            if(gpioReset != null){
                gpioReset.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit Runnable if the GPIO is already closed
            if (led3 == null ) {
                return;
            }
            try {
                // Toggle the GPIO state
                s3 = !s3;
                led3.setValue(s3);
                Log.d(TAG, "State led2 set to " + s3);

                // Reschedule the same runnable in {#INTERVAL_BETWEEN_BLINKS_MS} milliseconds
                mHandler.postDelayed(mBlinkRunnable, b);
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("NPNIoTs", "Key code is: " + keyCode);

        return true;
        //return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onSuccessUpdateServer(String message) {
        Log.d(TAG, "Request server is successful " + message);
//
//        writeUartData(message);
//        String speakWords = "Xin vui lòng đến ô số " + message;
//        niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    public void onErrorUpdateServer(String message) {
        //txtConsole.setText("Request server is fail");
        Log.d(TAG, "Request server is fail");
    }

    private class RfidTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidTask";
        private Rc522 rc522;

        RfidTask(Rc522 rc522){
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            button.setEnabled(false);
            mTagResultsView.setVisibility(View.GONE);
            mTagDetectedView.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            while(true){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if(!rc522.request()){
                    continue;
                }
                //Check for collision errors
                if(!rc522.antiCollisionDetect()){
                    continue;
                }
                byte[] uuid = rc522.getUid();
                return rc522.selectTag(uuid);
            }
        }


        @Override
        protected void onPostExecute(Boolean success) {
            if(!success){
                mTagResultsView.setText(R.string.unknown_error);
                return;
            }
            Log.d(TAG, "\nUUID "+ rc522.getUidString());

            String urlSalinity = "";
            urlSalinity = "http://demo1.chipfc.com/SensorValue/update?sensorid=7&sensorvalue=";
            urlSalinity += rc522.getUidString() ;
            mHomeViewModel.updateToServer(urlSalinity);


           // mTagUidView.setSelection(mTagUidView.getText().length());

            // Try to avoid doing any non RC522 operations until you're done communicating with it.
            byte address = Rc522.getBlockAddress(2,1);
            // Mifare's card default key A and key B, the key may have been changed previously
            byte[] key = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
            // Each sector holds 16 bytes
            // Data that will be written to sector 2, block 1
            byte[] newData = {0x4E,0x47,0x55,0x59,0x45,0x4E,0x2F,0x48,0x49,0x45,0x2F,0x51,0x55,0x41,0x4E,0x47};

            byte address1 = Rc522.getBlockAddress(2,2);
            byte[] key1 = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
            byte[] newData1 = {0x20,0x31,0x34,0x35,0x30,0x31,0x32,0x34,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20};

            /*byte address2 = Rc522.getBlockAddress(2,3);
            byte[] key2 = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
            byte[] newData2 = {0x30,0x35,0x2F,0x30,0x34,0x2F,0x31,0x39,0x39,0x36,0x20,0x20,0x20,0x20,0x20,0x20};*/

            //Log.d(TAG, "\nNEWDATA "+ newData);
            //Log.d(TAG, "Data that will be written to sector 2, block 1" + newData);
            // In this case, Rc522.AUTH_A or Rc522.AUTH_B can be used
            try {
                //We need to authenticate the card, each sector can have a different key
                boolean result = rc522.authenticateCard(Rc522.AUTH_A, address, key);
                boolean result1 = rc522.authenticateCard(Rc522.AUTH_A, address1, key1);
                //boolean result2 = rc522.authenticateCard(Rc522.AUTH_A, address2, key2);

              //  Log.d(TAG, "\nRESULT "+ result);
              //  Log.d(TAG, "\nRESULT1 "+ result1);
              //  Log.d(TAG, "key1: "+key1);

                if (!result || !result1 /*|| !result2*/) {
                    mTagResultsView.setText(R.string.authetication_error);
                    return;
                }
                //result = rc522.writeBlock(address, newData);
                //result1 = rc522.writeBlock(address1, newData1);
                //result2 = rc522.writeBlock(address2, newData2);

                if(!result || !result1 /*|| !result2*/){
                    mTagResultsView.setText(R.string.write_error);
                    return;
                }
                //resultsText += "Sector written successfully";
                byte[] buffer = new byte[16];
                byte[] buffer1 = new byte[16];
                //byte[] buffer2 = new byte[16];

                //Since we're still using the same block, we don't need to authenticate again
                result = rc522.readBlock(address, buffer);
                result1 = rc522.readBlock(address1, buffer1);
                // result2 = rc522.readBlock(address2, buffer2);

             //   Log.d(TAG, "\nRESULT "+ result);
             //   Log.d(TAG, "\nRESULT "+ result1);
                //Log.d(TAG, "\nRESULT "+ result2);

                if(!result || !result1 /*|| !result2*/){
                    mTagResultsView.setText(R.string.read_error);
                    return;
                }
///////////////////////// DATA 1 ///////////////////////////////////
                String s = Rc522.dataToHexString(buffer);
                StringBuilder sb = new StringBuilder();
                String[] components = s.split(" ");

              //  Log.d(TAG, "COMPONENT "+ components);

                for (String component : components) {
                    int ival = Integer.parseInt(component.replace("0x", ""), 16);
                    sb.append((char) ival);
                  //  Log.d(TAG, "ival "+ ival);
                }
                String string = sb.toString();

///////////////////////// DATA 2 ///////////////////////////////////
                String s1 = Rc522.dataToHexString(buffer1);
                StringBuilder sb1 = new StringBuilder();
                String[] components1 = s1.split(" ");

               // Log.d(TAG, "COMPONENT "+ components1);

                for (String component : components1) {
                    int ival1 = Integer.parseInt(component.replace("0x", ""), 16);
                    sb1.append((char) ival1);
                    //Log.d(TAG, "ival1 "+ ival1);
                }
                String string1 = sb1.toString();
                //Log.d(TAG, "\nstring1 "+ string1 +"hi");

                if(string1.equals(" 1450124        ")){
                    try {
                        led1.setValue(false);
                        led2.setValue(true);
                        resultsText += "\nSTUDENT NAME: "+ string;
                        resultsText += "\nID: "+ string1;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else{
                    try {
                        led1.setValue(false);
                        mHandler.post(mBlinkRunnable);
                        resultsText += "\nWRONG ID CARD";
                        resultsText += "\nPLEASE TRY AGAIN";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

///////////////////////// DATA 3 ///////////////////////////////////
             /*   String s2 = Rc522.dataToHexString(buffer2);
                StringBuilder sb2 = new StringBuilder();
                String[] components2 = s2.split(" ");

                Log.d(TAG, "COMPONENT "+ components2);

                for (String component : components2) {
                    int ival2 = Integer.parseInt(component.replace("0x", ""), 16);
                    sb2.append((char) ival2);
                    Log.d(TAG, "ival2 "+ ival2);
                }
                String string2 = sb2.toString();*/


///////////////////////////////////////////////////////////////////
                //resultsText += "\nSector read successfully: "+ Rc522.dataToHexString(buffer);
                //resultsText += "\nSTUDENT NAME: "+ string;
                //resultsText += "\nID: "+ string1;
                //resultsText += "\nDAY OF BIRTH: "+ string2;

                //Log.d(TAG, "\nMAIN STRING "+ Rc522.dataToHexString(buffer));
                //Log.d(TAG, "\nSTRING "+ s);
                //Log.d(TAG, "\nHEX TO STRING 0"+ string);
                //Log.d(TAG, "\nHEX TO STRING 1"+ string1);
                //Log.d(TAG, "\nHEX TO STRING 2"+ string2);

                rc522.stopCrypto();
                mTagResultsView.setText(resultsText);
            }finally{

                //start.setVisibility(View.INVISIBLE);
                button.setEnabled(true);
                button.setText(R.string.start);
                mTagUidView.setText(getString(R.string.tag_uid,rc522.getUidString()));
                mTagResultsView.setVisibility(View.VISIBLE);
                mTagDetectedView.setVisibility(View.VISIBLE);
                mTagUidView.setVisibility(View.VISIBLE);
            }
        }
    }
}
package com.example.donotdisturb;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.BlockedNumberContract;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.github.angads25.toggle.interfaces.OnToggledListener;
import com.github.angads25.toggle.model.ToggleableView;
import com.github.angads25.toggle.widget.LabeledSwitch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;



public class MainActivity extends AppCompatActivity {

    private LabeledSwitch labeledSwitch;
    private Button addToBlockListBtn;//, addToPriorityListBtn;
    private SharedPreferences pref;
    Set<String> DNDnumberSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        labeledSwitch = findViewById(R.id.switchDND);
        pref = getSharedPreferences("settings", 0);

        DNDnumberSet = new HashSet<>();

        try {
            syncSet();
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "cannot sync blocked numbers", Toast.LENGTH_SHORT).show();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_DENIED) {
                String[] permissions = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE};
                requestPermissions(permissions, 1);
            }
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},1);
        }

        labeledSwitch.setOnToggledListener(new OnToggledListener() {
            @Override
            public void onSwitched(ToggleableView toggleableView, boolean isOn) {
                if(isOn){
                    Toast.makeText(MainActivity.this, "DND MODE ON", Toast.LENGTH_SHORT).show();
                    modeon();
                }else{
                    Toast.makeText(MainActivity.this, "DND MODE OFF", Toast.LENGTH_SHORT).show();
                    modeoff();
                }
            }
        });

        addToBlockListBtn = (Button)findViewById(R.id.blockListBtn);

        addToBlockListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), AddToBlockListActivity.class));
            }
        });

    }

    TelephonyManager telephonyManager ;
    PhoneStateListener callStateListener ;
    AudioManager audioManager ;

    private void modeon() {
        telephonyManager = (TelephonyManager)getSystemService(MainActivity.TELEPHONY_SERVICE);
        audioManager = (AudioManager) MainActivity.this.getSystemService(Context.AUDIO_SERVICE);
        callStateListener = new PhoneStateListener(){

            @Override
            public void onCallStateChanged(int state, String phoneNumber) {

                if(state==TelephonyManager.CALL_STATE_RINGING){
//                    Toast.makeText(getApplicationContext(),"Phone is Ringing : "+phoneNumber,
//                            Toast.LENGTH_LONG).show();

                    if(phoneNumber.length()>10 && DNDnumberSet.contains(phoneNumber.substring(phoneNumber.length()-10))){
                        //silent it
                        Toast.makeText(MainActivity.this, "---BLOCKED---", Toast.LENGTH_SHORT).show();
                        System.out.println("___BLOCKED___");

                        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);


                        try {
                            Class clazz = Class.forName(telephonyManager.getClass().getName());
                            Method method = clazz.getDeclaredMethod("getITelephony");
                            method.setAccessible(true);
                            ITelephony telephonyService;
                            telephonyService = (ITelephony) method.invoke(telephonyManager);
//                            telephonyService.silenceRinger();
//                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O
//                                    || checkSelfPermission(Manifest.permission.CALL_PHONE)
//                                    == PackageManager.PERMISSION_DENIED) {
//                                return;
//                            }
//                            System.out.println("Permission toh mila hai");
                            telephonyService.endCall();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }else{
                    audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);
                }
                super.onCallStateChanged(state, phoneNumber);
            }
        };
        telephonyManager.listen(callStateListener,PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void modeoff(){
        telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
        callStateListener = null;
    }

    private void syncSet() throws JSONException {
        System.out.println("SYNCING ");
        JSONArray transactions = new JSONArray(pref.getString("transactions",""));
//        System.out.println(transactions.toString());
        if(transactions!=null && transactions.length()>0){
            for (int i = 0; i < transactions.length(); i++) {
                JSONObject obj = transactions.getJSONObject(i);
                String number = obj.getString("ph_no");
                number = number.substring(number.length()-10);
                DNDnumberSet.add(number);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted: " + 1, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permission NOT granted: " + 1, Toast.LENGTH_SHORT).show();
                }

                return;
            }
        }
    }


}

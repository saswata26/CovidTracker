package com.cs528.covidtracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class BluetoothService extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent){
        Toast.makeText(context, "hmmm",Toast.LENGTH_SHORT).show();
    }

}

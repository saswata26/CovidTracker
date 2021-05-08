package com.cs528.covidtracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.zip.CheckedOutputStream;

public class AlarmHandler {//calls bluetoothservice repeatedly
    private Context context;
    public static final long interval = 2*1000*60;

    public AlarmHandler(Context context){
        this.context=context;
    }
    public void setAlarmManager(){
        Intent intent= new Intent(context, BluetoothService.class);
        PendingIntent sender = PendingIntent.getBroadcast(context,2,intent,0);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, 5000, interval, sender);//INTERVAL MUST BE AT LEAST 1 MINUTE

    }

    public void cancelAlarmManager(){
        Intent intent= new Intent(context, BluetoothService.class);
        PendingIntent sender = PendingIntent.getBroadcast(context,2,intent,0);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
    }
}

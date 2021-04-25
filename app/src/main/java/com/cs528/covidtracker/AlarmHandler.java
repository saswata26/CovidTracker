package com.cs528.covidtracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.zip.CheckedOutputStream;

public class AlarmHandler {
    private Context context;

    public AlarmHandler(Context context){
        this.context=context;
    }
    public void setAlarmManager(){
        Intent intent= new Intent(context, BluetoothService.class);
        PendingIntent sender = PendingIntent.getBroadcast(context,2,intent,0);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long interval=1000*60*1;//in milli seconds
        am.setRepeating(AlarmManager.RTC_WAKEUP, 5000, interval, sender);

    }

    public void cancelAlarmManager(){
        Intent intent= new Intent(context, BluetoothService.class);
        PendingIntent sender = PendingIntent.getBroadcast(context,2,intent,0);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
    }
}

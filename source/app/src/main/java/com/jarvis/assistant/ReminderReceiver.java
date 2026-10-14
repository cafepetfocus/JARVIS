package com.jarvis.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "jarvis_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        if (title == null || title.trim().isEmpty()) title = "Rappel JARVIS";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Rappels JARVIS", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications des rappels créés dans JARVIS");
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder = new Notification.Builder(context, CHANNEL_ID);
        else builder = new Notification.Builder(context);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Rappel JARVIS")
            .setContentText(title)
            .setStyle(new Notification.BigTextStyle().bigText(title))
            .setAutoCancel(true).build();
        manager.notify((int)(System.currentTimeMillis() & 0x7fffffff), notification);
    }
}

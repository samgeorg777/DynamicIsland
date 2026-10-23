package com.dynamicisland;

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.os.Bundle;

public class IslandNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        // Skip our own notifications
        if ("com.dynamicisland".equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = "";
        String body = "";

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence bodyCs = extras.getCharSequence(Notification.EXTRA_TEXT);

        if (titleCs != null) title = titleCs.toString();
        if (bodyCs != null) body = bodyCs.toString();

        if (!title.isEmpty()) {
            DynamicIslandService.showNotification(this, title, body);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: handle notification dismissal
    }
}

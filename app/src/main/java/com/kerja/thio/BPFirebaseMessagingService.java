package com.kerja.thio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class BPFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "kerja_updates";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        TokenRegistrar.register(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        String title = "Kerja";
        String body = "";

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null) {
                body = message.getNotification().getBody();
            }
        } else if (!message.getData().isEmpty()) {
            title = message.getData().getOrDefault("title", title);
            body = message.getData().getOrDefault("body", body);
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        manager.notify(notificationId, notification.build());
    }
}

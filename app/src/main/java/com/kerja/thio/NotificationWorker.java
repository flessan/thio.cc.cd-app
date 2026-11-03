package com.kerja.thio;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NotificationWorker extends Worker {

    private static final String CHANNEL_ID = "kerja_activity";

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences preferences =
                context.getSharedPreferences("bp_prefs", Context.MODE_PRIVATE);

        String cookies = preferences.getString("session_cookies", null);
        String websiteOrigin = preferences.getString("website_url", null);
        int lastNotificationId = preferences.getInt("last_notif_id", 0);

        if (cookies == null || cookies.isEmpty()
                || websiteOrigin == null
                || !websiteOrigin.startsWith("https://")) {
            return Result.success();
        }

        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(websiteOrigin + "/notifications.php?ajax=check");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cookie", cookies);
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED
                    || status == HttpURLConnection.HTTP_FORBIDDEN) {
                return Result.success();
            }
            if (status >= 500 || status == 429) {
                return Result.retry();
            }
            if (status < 200 || status >= 300) {
                return Result.success();
            }

            String response = readResponse(connection.getInputStream());
            JSONObject json = new JSONObject(response);
            int unread = json.optInt("unread", 0);

            if (json.has("latest") && !json.isNull("latest")) {
                JSONObject latest = json.getJSONObject("latest");
                int notificationId = latest.optInt("id", 0);

                if (notificationId > lastNotificationId
                        && lastNotificationId > 0
                        && unread > 0) {
                    showNotification(
                            context,
                            latest.optString("title", context.getString(R.string.app_name)),
                            latest.optString("message", ""));
                }

                if (notificationId > lastNotificationId && notificationId > 0) {
                    preferences.edit()
                            .putInt("last_notif_id", notificationId)
                            .apply();
                }
            }

            return Result.success();
        } catch (Exception exception) {
            return Result.retry();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(InputStream stream) throws Exception {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private void showNotification(Context context, String title, String body) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
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

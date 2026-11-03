package com.kerja.thio;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TokenRegistrar {

    private static final String TAG = "KerjaFCM";
    private static final String TOKEN_ENDPOINT =
            "https://thio-cc-cd.pages.dev/fcm_token.php";

    private TokenRegistrar() {
    }

    public static void register(Context context, String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        Context appContext = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("token", token);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

                connection = (HttpURLConnection) new URL(TOKEN_ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("User-Agent", "Kerja-Android/2.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);

                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    Log.w(TAG, "Token registration returned HTTP " + status);
                }
            } catch (Exception exception) {
                Log.w(TAG, "Token registration failed", exception);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "kerja-fcm-register");

        thread.setDaemon(true);
        thread.start();
    }
}

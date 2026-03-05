package com.example.pc2gboard;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.net.*;

public class SocketService extends Service {
    private static final String CHANNEL_ID = "PC2Gboard_Service_Channel";
    private boolean isRunning = false;
    private WifiManager.MulticastLock multicastLock;

    // =============================
    // 🔥 前台服務在 onCreate 啟動
    // =============================
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
    }

    // =============================
    // 🔥 啟動核心邏輯
    // =============================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            // 1. 在啟動服務時取得 MulticastLock
            acquireMulticastLock();


            // 啟動監聽線程
            new Thread(this::startUdpBeacon).start();
            new Thread(this::startSocketServer).start();
            isRunning = true;
        }
        return START_STICKY;
    }

    // =============================
    // 🔥 通知
    // =============================
    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PC2Gboard 服務運行中")
                .setContentText("正在背景監聽電腦端的圖片推送...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true) // 🔥 不可滑掉
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PC2Gboard 背景服務",
                    NotificationManager.IMPORTANCE_LOW // 🔥 不要用 MIN
            );
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    // =============================
    // 🔥 MulticastLock
    // =============================
    private void acquireMulticastLock() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            // 建立鎖，標籤可以自訂，這裡用類別名稱
            multicastLock = wifi.createMulticastLock("pc2gboard:udp_lock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire(); // 正式鎖定，允許接收廣播
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        // 直接在這裡釋放，不拆 function
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        super.onDestroy();
    }




    private void startUdpBeacon() {
        while (true) {
            try (DatagramSocket socket = new DatagramSocket(9528)) {
                socket.setBroadcast(true);
                byte[] buffer = new byte[1024];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    if ("PC2GBOARD_DISCOVERY".equals(message)) {
                        byte[] response = "PC2GBOARD_HERE".getBytes();
                        DatagramPacket reply = new DatagramPacket(
                                response,
                                response.length,
                                packet.getAddress(),
                                packet.getPort()
                        );
                        socket.send(reply);
                    }
                }

            } catch (IOException e) { e.printStackTrace(); sleepQuietly(); }
        }
    }

    private void startSocketServer() {
        while (true) {
            try (ServerSocket serverSocket = new ServerSocket(9527)) {

                while (true) {
                    try (Socket client = serverSocket.accept()) {

                        File cacheFile = new File(getCacheDir(), "sync.png");

                        try (InputStream is = client.getInputStream();
                             FileOutputStream fos = new FileOutputStream(cacheFile)) {

                            byte[] buffer = new byte[8192];
                            int len;

                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        launchClipboardActivity();
                    } catch (IOException e) { e.printStackTrace(); }
                }
            } catch (IOException e) { e.printStackTrace(); sleepQuietly(); }
        }
    }
    private void launchClipboardActivity() {
        Intent intent = new Intent(this, ClipboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notify = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("收到圖片")
                .setContentText("點擊寫入剪貼簿")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL) // 🔥 CATEGORY_CALL 才能觸發 fullScreenIntent
                .setFullScreenIntent(pendingIntent, true)       // 🔥 這是繞過限制的關鍵
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(2, notify); // 用 id=2，不要蓋掉常駐通知 id=1
    }
    private void sleepQuietly() {
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    // =============================
    // 🔥 防止滑掉最近任務
    // =============================
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartService = new Intent(getApplicationContext(), this.getClass());
        restartService.setPackage(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartService);
        } else {
            startService(restartService);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

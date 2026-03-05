package com.example.pc2gboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager; // 必須引入
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

public class SocketService extends Service {
    private static final String CHANNEL_ID = "PC2Gboard_Service_Channel";
    private boolean isRunning = false;

    // 宣告 MulticastLock 變數
    private WifiManager.MulticastLock multicastLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            // 1. 在啟動服務時取得 MulticastLock
            acquireMulticastLock();

            startForegroundServiceWithNotification();

            // 啟動監聽線程
            new Thread(this::startUdpBeacon).start();
            new Thread(this::startSocketServer).start();
            isRunning = true;
        }
        return START_STICKY;
    }

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
        // 2. 服務停止時釋放鎖，避免耗電
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        super.onDestroy();
    }

    private void startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "PC2Gboard 背景服務",
                    NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PC2Gboard 服務運行中")
                .setContentText("正在背景監聽電腦端的圖片推送...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(1, notification);
    }

    private void startUdpBeacon() {
        // ... (這裡維持你原本的 UDP 代碼) ...
        try (DatagramSocket socket = new DatagramSocket(9528)) {
            socket.setBroadcast(true);
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                if ("PC2GBOARD_DISCOVERY".equals(message)) {
                    byte[] response = "PC2GBOARD_HERE".getBytes();
                    DatagramPacket reply = new DatagramPacket(response, response.length,
                            packet.getAddress(), packet.getPort());
                    socket.send(reply);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void startSocketServer() {
        // ... (這裡維持你原本的 TCP 代碼) ...
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
                        Intent intent = new Intent(this, ClipboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

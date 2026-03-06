package com.example.pc2gboard;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.net.Uri;
import android.content.ClipboardManager;
import android.content.ClipData;
import androidx.core.content.FileProvider;
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
    private static final String TAG = "PC2Gboard";
    private static final String CHANNEL_ID = "PC2Gboard_Service_Channel";
    private boolean isRunning = false;
    private WifiManager.MulticastLock multicastLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== SocketService onCreate ===");
        createNotificationChannel();
        startForeground(1, buildNotification());
        Log.d(TAG, "startForeground 完成");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand 呼叫, isRunning=" + isRunning);
        if (!isRunning) {
            acquireMulticastLock();
            new Thread(this::startUdpBeacon).start();
            new Thread(this::startSocketServer).start();
            isRunning = true;
            Log.d(TAG, "監聽執行緒已啟動");
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PC2Gboard 服務運行中")
                .setContentText("正在背景監聽電腦端的圖片推送...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PC2Gboard 背景服務",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
            Log.d(TAG, "通知頻道建立完成");
        }
    }

    private void acquireMulticastLock() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("pc2gboard:udp_lock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
            Log.d(TAG, "MulticastLock 已取得");
        } else {
            Log.e(TAG, "MulticastLock 取得失敗：WifiManager 為 null");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "=== SocketService onDestroy ===");
        isRunning = false;
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            Log.d(TAG, "MulticastLock 已釋放");
        }
        super.onDestroy();
    }

    private void startUdpBeacon() {
        Log.d(TAG, "UDP Beacon 執行緒啟動，監聽 Port 9528");
        while (true) {
            try (DatagramSocket socket = new DatagramSocket(9528)) {
                socket.setBroadcast(true);
                byte[] buffer = new byte[1024];
                Log.d(TAG, "UDP Socket 已開啟，等待廣播...");

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    Log.d(TAG, "UDP 收到封包：'" + message + "' 來自 " + packet.getAddress().getHostAddress());

                    if ("PC2GBOARD_DISCOVERY".equals(message)) {
                        byte[] response = "PC2GBOARD_HERE".getBytes();
                        DatagramPacket reply = new DatagramPacket(
                                response, response.length,
                                packet.getAddress(), packet.getPort());
                        socket.send(reply);
                        Log.d(TAG, "UDP 已回覆 PC2GBOARD_HERE 給 " + packet.getAddress().getHostAddress());
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "UDP 異常，2秒後重試: " + e.getMessage());
                sleepQuietly();
            }
        }
    }

    private void startSocketServer() {
        Log.d(TAG, "Socket Server 執行緒啟動，監聽 Port 9527");
        while (true) {
            try (ServerSocket serverSocket = new ServerSocket(9527)) {
                Log.d(TAG, "ServerSocket 已開啟，等待連線...");

                while (true) {
                    try (Socket client = serverSocket.accept()) {
                        Log.d(TAG, ">>> 收到連線！來自: " + client.getInetAddress().getHostAddress());

                        File cacheFile = new File(getCacheDir(), "sync.png");

                        try (InputStream is = client.getInputStream();
                             FileOutputStream fos = new FileOutputStream(cacheFile)) {

                            byte[] buffer = new byte[8192];
                            int len;
                            int totalBytes = 0;

                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                                totalBytes += len;
                            }
                            Log.d(TAG, "圖片接收完成，共 " + totalBytes + " bytes，儲存至: " + cacheFile.getAbsolutePath());
                        }
                        writeToClipboard(cacheFile);
                        Log.d(TAG, "準備呼叫 launchClipboardActivity()...");
                        //launchClipboardActivity();
                        //Log.d(TAG, "launchClipboardActivity() 呼叫完畢");

                    } catch (IOException e) {
                        Log.e(TAG, "處理連線時發生異常: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "ServerSocket 異常，2秒後重試: " + e.getMessage());
                sleepQuietly();
            }
        }
    }
    private void writeToClipboard(File imageFile) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", imageFile);
            grantUriPermission("com.google.android.inputmethod.latin",
                    contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newUri(getContentResolver(), "Image", contentUri);
            if (cb != null) {
                cb.setPrimaryClip(clip);
                Log.d(TAG, "✅ 剪貼簿寫入成功！");
            } else {
                Log.e(TAG, "❌ ClipboardManager 為 null");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 剪貼簿寫入失敗: " + e.getMessage());
        }
    }
    private void launchClipboardActivity() {
        Log.d(TAG, "launchClipboardActivity 開始執行");

        // 🔍 檢查 canDrawOverlays 權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean canOverlay = android.provider.Settings.canDrawOverlays(this);
            Log.d(TAG, "canDrawOverlays: " + canOverlay);
        }

        Intent intent = new Intent(this, ClipboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        Log.d(TAG, "startActivity 已呼叫");
    }

    private void sleepQuietly() {
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved 觸發，嘗試重啟服務");
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

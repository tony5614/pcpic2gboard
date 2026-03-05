package com.example.pc2gboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView; // 必須引入
import androidx.appcompat.app.AppCompatActivity;
import android.net.wifi.WifiManager;
import android.os.Build;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress; // 如果你有用到地址綁定


public class MainActivity extends AppCompatActivity {

    private TextView debugTextView; // 宣告變數
    private WifiManager.MulticastLock multicastLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
        // 啟動前台服務
        Intent serviceIntent = new Intent(this, SocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // 原本在 onCreate 裡的 startUdpBeacon 和 startSocketServer 執行緒都可以刪掉了
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }

    // 封裝一個可以在任何地方呼叫的 Log 方法
    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = "[" + time + "] " + message + "\n";

        // 強制回到 UI 執行緒更新畫面，不然會當機
        runOnUiThread(() -> {
            if (debugTextView != null) {
                debugTextView.append(line);
            }
        });
    }

}

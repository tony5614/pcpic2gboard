package com.example.pc2gboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView; // 必須引入
import androidx.appcompat.app.AppCompatActivity;
import android.net.wifi.WifiManager;

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

        // 綁定 XML 中的 TextView
        debugTextView = findViewById(R.id.debug_log);

        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);

        if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
            int ip = wifiManager.getConnectionInfo().getIpAddress();
            String ipString = String.format(
                    Locale.getDefault(),
                    "%d.%d.%d.%d",
                    (ip & 0xff),
                    (ip >> 8 & 0xff),
                    (ip >> 16 & 0xff),
                    (ip >> 24 & 0xff)
            );
            addLog("手機 IP: " + ipString);
        } else {
            addLog("無法取得 WiFi 資訊");
        }

        new Thread(this::startUdpBeacon).start();

        addLog("App 已啟動，準備初始化伺服器...");

        new Thread(this::startSocketServer).start();

        addLog("手機 IP: " +
                ((WifiManager)getApplicationContext()
                        .getSystemService(WIFI_SERVICE))
                        .getConnectionInfo().getIpAddress());
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }

    private void startUdpBeacon() {
        try (DatagramSocket socket = new DatagramSocket(9528)) {
            socket.setBroadcast(true); // 確保允許廣播
            byte[] buffer = new byte[1024];
            addLog("UDP 監聽中... (Port: 9528)");

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // 收到東西會往下跑

                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIP = packet.getAddress().getHostAddress();

                // 只要收到任何 UDP，先打 Log再說
                addLog("收到 UDP 封包來自: " + senderIP + " 內容: " + message);

                if ("PC2GBOARD_DISCOVERY".equals(message)) {
                    addLog("確認身份成功，正在回覆 PC...");
                    byte[] response = "PC2GBOARD_HERE".getBytes();
                    DatagramPacket reply = new DatagramPacket(
                            response, response.length, packet.getAddress(), packet.getPort());
                    socket.send(reply);
                }
            }
        } catch (IOException e) {
            addLog("UDP 錯誤: " + e.getMessage());
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

    private void startSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(9527)) {
            addLog("伺服器已啟動，監聽 Port: 9527");

            while (true) {
                try (Socket client = serverSocket.accept()) {
                    String clientIP = client.getInetAddress().getHostAddress();
                    addLog("收到連線，來源 IP: " + clientIP);

                    File cacheFile = new File(getCacheDir(), "sync.png");

                    try (InputStream is = client.getInputStream();
                         FileOutputStream fos = new FileOutputStream(cacheFile)) {

                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }

                        addLog("圖片接收完畢 (" + cacheFile.length() + " bytes)");

                        Intent intent = new Intent(this, ClipboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);

                        addLog("已跳轉至 ClipboardActivity");

                    } catch (IOException e) {
                        addLog("傳輸出錯: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            addLog("Socket 啟動失敗: " + e.getMessage());
        }
    }
}

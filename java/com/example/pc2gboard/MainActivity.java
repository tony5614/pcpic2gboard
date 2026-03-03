package com.example.pc2gboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView; // 必須引入
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView debugTextView; // 宣告變數

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 綁定 XML 中的 TextView
        debugTextView = findViewById(R.id.debug_log);

        addLog("App 已啟動，準備初始化伺服器...");

        new Thread(this::startSocketServer).start();
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

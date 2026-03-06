package com.example.pc2gboard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;

public class ClipboardActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clipboard); // 綁定剛才做的佈局

        ImageView preview = findViewById(R.id.clip_preview);
        TextView status = findViewById(R.id.clip_status);
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        File imageFile = new File(getCacheDir(), "sync.png");

        if (imageFile.exists()) {
            // 顯示預覽圖，讓你確認圖片沒傳壞
            preview.setImageBitmap(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));

            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", imageFile);

            // 授權
            grantUriPermission("com.google.android.inputmethod.latin",
                    contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 寫入剪貼簿
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newUri(getContentResolver(), "Image", contentUri);
            if (cb != null) {
                cb.setPrimaryClip(clip);
                android.util.Log.d("PC2Gboard", "✅ 剪貼簿寫入成功！");
                status.setText("成功：已寫入剪貼簿！");
            } else {
                android.util.Log.e("PC2Gboard", "❌ ClipboardManager 為 null");
            }
        } else {
            status.setText("錯誤：找不到圖片檔案");
        }

        // 注意：調適階段先不呼叫 finish()，手動按按鈕再關閉
    }
}

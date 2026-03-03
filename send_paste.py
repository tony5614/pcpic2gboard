import socket
from PIL import ImageGrab
import io
import time

# 手機的 IP 與 Port
PHONE_IP = '192.168.137.92' 
PORT = 9527

last_hash = None

while True:
    img = ImageGrab.grabclipboard()
    if img:
        # 將圖片轉為 Byte 流
        img_byte_arr = io.BytesIO()
        img.save(img_byte_arr, format='PNG')
        img_data = img_byte_arr.getvalue()
        
        current_hash = hash(img_data)
        if current_hash != last_hash:
            try:
                # 建立 Socket 連線並發送
                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                    s.connect((PHONE_IP, PORT))
                    s.sendall(img_data) # 直接灌入所有數據
                print("圖片已成功推送")
                last_hash = current_hash
            except Exception as e:
                print(f"發送失敗: {e}")
    time.sleep(1)

import socket
from PIL import ImageGrab
import io
import time
import socket
import struct




PORT = 9527
UDP_PORT = 9528

def get_broadcast_ip():
    hostname = socket.gethostname()
    local_ip = socket.gethostbyname(hostname)

    ip_parts = local_ip.split('.')
    ip_parts[-1] = '255'
    return '.'.join(ip_parts)



def find_phone_ip():
    print(f"\n[DEBUG] 開始搜尋手機... 目標 Port: {UDP_PORT}")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.settimeout(2.0)
        
        message = "PC2GBOARD_DISCOVERY".encode('utf-8')
        
        # 測試發送
        try:
            broadcast_ip = get_broadcast_ip()
            s.sendto(message, (broadcast_ip, UDP_PORT))
        except Exception as e:
            print(f"[ERROR] 廣播發送失敗: {e}")
            return None
        
        # 測試接收
        try:
            print("[DEBUG] 等待手機回報 (Timeout: 2s)...")
            data, addr = s.recvfrom(1024)
            resp = data.decode('utf-8')
            print(f"[DEBUG] 收到回應！來源: {addr[0]}, 內容: {resp}")
            if resp == "PC2GBOARD_HERE":
                print(f"✅ 成功對齊手機 IP: {addr[0]}")
                return addr[0]
        except socket.timeout:
            print("[DEBUG] 搜尋逾時：沒有收到任何手機回包。")
        except Exception as e:
            print(f"[ERROR] 接收過程出錯: {e}")
            
        return None

PHONE_IP = None
last_hash = None

while True:
    if PHONE_IP is None:
        PHONE_IP = find_phone_ip()
        if PHONE_IP is None:
            print("[DEBUG] 本輪搜尋失敗，1秒後重試...")
            time.sleep(1)
            continue

    # 圖片檢查邏輯... (略，保持你原本的即可)
    try:
        img = ImageGrab.grabclipboard()
        if img and hasattr(img, 'save'):
            img_byte_arr = io.BytesIO()
            img.save(img_byte_arr, format='PNG')
            img_data = img_byte_arr.getvalue()
            current_hash = hash(img_data)
            
            if current_hash != last_hash:
                print(f"[DEBUG] 偵測到新圖片，嘗試推送至 {PHONE_IP}...")
                try:
                    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                        s.settimeout(5)
                        s.connect((PHONE_IP, PORT))
                        s.sendall(img_data)
                    print("✨ 圖片已成功推送")
                    last_hash = current_hash
                except Exception as e:
                    print(f"❌ 推送失敗: {e}")
                    PHONE_IP = None 
    except Exception as e:
        print(f"[DEBUG] 剪貼簿讀取異常: {e}")
        
    time.sleep(1)

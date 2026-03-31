"""
Smart Museum — User Simulator
==============================
Олон хэрэглэгч нэгэн зэрэг дараах үйлдлүүдийг random хийнэ:
  - BLE signal  → POST /ble/readings
  - NFC scan    → POST /proximity/scan (source: NFC)
  - QR scan     → POST /proximity/scan (source: QR)

Ажиллуулах:
  pip install requests websocket-client
  python simulate_users.py
"""

import random
import time
import threading
import requests
import json
import websocket
from datetime import datetime

# ── Config ─────────────────────────────────────────────────
CORE_URL        = "http://localhost:8080"
NUM_USERS       = 5       # нэгэн зэрэг ажиллах хэрэглэгчийн тоо
SIM_DURATION    = 60      # секунд (0 = infinite)
MIN_INTERVAL    = 2       # хэрэглэгч бүрийн request хоорондын хамгийн бага хугацаа
MAX_INTERVAL    = 6       # хамгийн их хугацаа

# ── Museum config ───────────────────────────────────────────
FLOOR_BEACONS = [
    "floor-beacon-f1",
    "floor-beacon-f2",
    "floor-beacon-f3",
]

POSITIONING_BEACONS = [f"beacon-{i}" for i in range(17)]
ALL_BEACONS = FLOOR_BEACONS + POSITIONING_BEACONS  # нийт 20

# ArtInfo seed-с авах art ID-үүд (MongoDB-д байгаа)
# Эхлээд fetch хийнэ, олдохгүй бол random string ашиглана
ART_IDS = []

# ── Stats ────────────────────────────────────────────────────
stats = {
    "ble_sent": 0,
    "ble_ok": 0,
    "proximity_sent": 0,
    "proximity_ok": 0,
    "ws_connected": 0,
    "ws_messages": 0,
    "errors": 0,
}
stats_lock = threading.Lock()

def inc(key):
    with stats_lock:
        stats[key] += 1

# ── Helpers ──────────────────────────────────────────────────
def log(user_id, msg):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] User-{user_id:02d} | {msg}")

def random_ble_readings(num_beacons=3):
    """Random beacon-уудаас сигнал үүсгэнэ"""
    # Floor beacon заавал нэг ороно
    floor_beacon = random.choice(FLOOR_BEACONS)
    chosen = random.sample(POSITIONING_BEACONS, min(num_beacons - 1, len(POSITIONING_BEACONS)))
    readings = [{"beaconId": floor_beacon, "rssi": random.randint(-75, -50)}]
    for b in chosen:
        readings.append({"beaconId": b, "rssi": random.randint(-100, -40)})
    return readings

def fetch_art_ids():
    """ArtInfo service-с бүх art ID-үүд авна"""
    global ART_IDS
    try:
        r = requests.get(f"http://localhost:8082/internal/art", timeout=3)
        if r.status_code == 200:
            arts = r.json()
            ART_IDS = [a["artId"] for a in arts if a.get("artId")]
            print(f"[INIT] Art IDs loaded: {len(ART_IDS)} arts")
            if ART_IDS:
                print(f"[INIT] Sample IDs: {ART_IDS[:3]}")
            return
        else:
            print(f"[WARN] ArtInfo returned {r.status_code}")
    except Exception as e:
        print(f"[WARN] Could not fetch art IDs: {e}")

    # Fallback — MongoDB-с авч чадахгүй үед
    print("[WARN] Using dummy art IDs")
    ART_IDS = []  # хоосон бол proximity scan хийхгүй

# ── Actions ──────────────────────────────────────────────────
def send_ble(user_id):
    """BLE signal явуулна"""
    payload = {
        "deviceId": f"sim-user-{user_id:02d}",
        "timestamp": int(time.time() * 1000),
        "readings": random_ble_readings(random.randint(2, 5))
    }
    try:
        inc("ble_sent")
        r = requests.post(f"{CORE_URL}/ble/readings", json=payload, timeout=3)
        if r.status_code in (200, 202):
            inc("ble_ok")
            log(user_id, f"BLE → {r.json().get('status', 'ok')}")
        else:
            inc("errors")
            log(user_id, f"BLE → {r.status_code}")
    except Exception as e:
        inc("errors")
        log(user_id, f"BLE error: {e}")

def send_proximity(user_id, source):
    """QR эсвэл NFC scan явуулна"""
    if not ART_IDS:
        return
    art_id = random.choice(ART_IDS)
    payload = {
        "deviceId": f"sim-user-{user_id:02d}",
        "artId": art_id,
        "source": source
    }
    try:
        inc("proximity_sent")
        r = requests.post(f"{CORE_URL}/proximity/scan", json=payload, timeout=3)
        if r.status_code in (200, 202):
            inc("proximity_ok")
            log(user_id, f"{source} → artId={art_id[:8]}...")
        else:
            inc("errors")
            log(user_id, f"{source} → {r.status_code}: {r.text[:80]}")
    except Exception as e:
        inc("errors")
        log(user_id, f"{source} error: {e}")

def websocket_listener(user_id):
    """WebSocket холбоод message хүлээнэ"""
    device_id = f"sim-user-{user_id:02d}"
    url = f"ws://localhost:8080/ws?deviceId={device_id}"

    def on_open(ws):
        inc("ws_connected")
        log(user_id, "WebSocket connected")

    def on_message(ws, message):
        inc("ws_messages")
        try:
            msg = json.loads(message)
            msg_type = msg.get("type", "?")
            payload  = msg.get("payload", {})
            if msg_type == "location_update":
                floor = payload.get("floorId", "?")
                x     = payload.get("x", "?")
                y     = payload.get("y", "?")
                arts  = payload.get("arts", [])
                art_titles = [a.get("title", "?")[:20] for a in arts[:2]]
                log(user_id, f"Floor={floor} ({x},{y}) Arts={art_titles}")
            else:
                log(user_id, f"{msg_type}")
        except Exception:
            pass

    def on_error(ws, error):
        log(user_id, f"WS error: {error}")

    def on_close(ws, *args):
        log(user_id, "WebSocket closed")

    ws = websocket.WebSocketApp(
        url,
        on_open=on_open,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close
    )
    ws.run_forever()

# ── User simulation ──────────────────────────────────────────
def simulate_user(user_id, stop_event):
    """Нэг хэрэглэгчийн random үйлдлүүд"""
    # WebSocket-г тусдаа thread-д асаана
    ws_thread = threading.Thread(
        target=websocket_listener,
        args=(user_id,),
        daemon=True
    )
    ws_thread.start()

    # WebSocket холбогдох хүртэл хүлээ
    time.sleep(random.uniform(0.5, 2.0))

    actions = ["ble", "ble", "ble", "qr", "nfc"]  # BLE илүү байх магадлалтай

    while not stop_event.is_set():
        action = random.choice(actions)

        if action == "ble":
            send_ble(user_id)
        elif action == "qr":
            send_proximity(user_id, "QR")
        elif action == "nfc":
            send_proximity(user_id, "NFC")

        # Random хугацаа хүлээнэ
        wait = random.uniform(MIN_INTERVAL, MAX_INTERVAL)
        stop_event.wait(wait)

# ── Main ─────────────────────────────────────────────────────
def print_stats():
    print("\n" + "="*50)
    print("SIMULATION STATS")
    print("="*50)
    print(f"  BLE requests:       {stats['ble_sent']} sent, {stats['ble_ok']} ok")
    print(f"  Proximity requests: {stats['proximity_sent']} sent, {stats['proximity_ok']} ok")
    print(f"  WebSocket:          {stats['ws_connected']} connected, {stats['ws_messages']} messages")
    print(f"  Errors:             {stats['errors']}")
    print("="*50)

def main():
    print("="*50)
    print("Smart Museum User Simulator")
    print(f"   Users: {NUM_USERS} | Duration: {SIM_DURATION}s | Core: {CORE_URL}")
    print("="*50)

    # Art ID-үүд fetch хийнэ
    fetch_art_ids()

    stop_event = threading.Event()
    threads    = []

    # Хэрэглэгч бүрийг тусдаа thread-д ажиллуулна
    for i in range(1, NUM_USERS + 1):
        t = threading.Thread(
            target=simulate_user,
            args=(i, stop_event),
            daemon=True
        )
        t.start()
        threads.append(t)
        time.sleep(0.3)  # Thread-үүд нэгэн зэрэг эхлэхгүй байхад

    print(f"\nSimulation started with {NUM_USERS} users...\n")

    try:
        if SIM_DURATION > 0:
            time.sleep(SIM_DURATION)
            stop_event.set()
        else:
            # Infinite — Ctrl+C дарж зогсооно
            while True:
                time.sleep(1)
    except KeyboardInterrupt:
        print("\nStopping simulation...")
        stop_event.set()

    time.sleep(1)
    print_stats()

if __name__ == "__main__":
    main()

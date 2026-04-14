"""
Smart Museum — User Simulator
==============================
Олон хэрэглэгч нэгэн зэрэг дараах үйлдлүүдийг random хийнэ:
  - BLE signal  → POST /ble/readings
  - NFC scan    → POST /proximity/scan (source: NFC)
  - QR scan     → POST /proximity/scan (source: QR)

Ажиллуулах:
  pip install requests websocket-client pyyaml
  python simulate_users.py
"""

import json
import os
import random
import threading
import time
from datetime import datetime

import requests
import websocket

try:
    import yaml
except Exception:
    yaml = None


# ── Config ─────────────────────────────────────────────────
CORE_URL = os.getenv("SIM_CORE_URL", "http://localhost:8080")
ARTINFO_URL = os.getenv("SIM_ARTINFO_URL", "http://localhost:8082")
WS_URL = os.getenv("SIM_WS_URL", CORE_URL.replace("http://", "ws://").replace("https://", "wss://"))

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)


def resolve_layout_path(path_value):
    """Resolve layout path robustly for both project root and simulator cwd runs."""
    if not path_value:
        return None

    raw = path_value.strip().replace("/", os.sep)
    candidates = []

    if os.path.isabs(raw):
        candidates.append(raw)
    else:
        candidates.append(raw)
        candidates.append(os.path.join(PROJECT_ROOT, raw))
        candidates.append(os.path.join(SCRIPT_DIR, raw))
        # When value starts with config/... and cwd is simulator/, this resolves ../config/...
        candidates.append(os.path.join(SCRIPT_DIR, "..", raw))

    for c in candidates:
        normalized = os.path.abspath(c)
        if os.path.exists(normalized):
            return normalized

    return None


DEFAULT_LAYOUT_CANDIDATE = os.path.join("config", "smart_museum", "layout.yml")
SIM_LAYOUT_FILE = (
    os.getenv("SIM_LAYOUT_FILE")
    or os.getenv("MUSEUM_LAYOUT_FILE")
    or DEFAULT_LAYOUT_CANDIDATE
)

NUM_USERS = int(os.getenv("SIM_NUM_USERS", "50"))
SIM_DURATION = int(os.getenv("SIM_DURATION", "30"))  # секунд (0 = infinite)
MIN_INTERVAL = float(os.getenv("SIM_MIN_INTERVAL", "2"))
MAX_INTERVAL = float(os.getenv("SIM_MAX_INTERVAL", "6"))


# ── Site config (layout-аас уншина) ─────────────────────────
SITE_NAME = "Unknown Site"
SITE_ID = "unknown-site"
FLOORS = [1]
FLOOR_BEACONS_BY_FLOOR = {}
POSITIONING_BEACONS_BY_FLOOR = {}
ALL_BEACONS = []


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


def _set_default_layout():
    global SITE_NAME, SITE_ID, FLOORS
    global FLOOR_BEACONS_BY_FLOOR, POSITIONING_BEACONS_BY_FLOOR, ALL_BEACONS

    SITE_NAME = "Smart Museum (default)"
    SITE_ID = "smart-museum-default"
    FLOOR_BEACONS_BY_FLOOR = {1: ["floor-beacon-f1"], 2: ["floor-beacon-f2"], 3: ["floor-beacon-f3"]}
    POSITIONING_BEACONS_BY_FLOOR = {
        1: [f"beacon-{i}" for i in range(3, 9)],
        2: [f"beacon-{i}" for i in range(9, 15)],
        3: [f"beacon-{i}" for i in range(15, 20)],
    }
    FLOORS = sorted(FLOOR_BEACONS_BY_FLOOR.keys())
    ALL_BEACONS = [b for arr in FLOOR_BEACONS_BY_FLOOR.values() for b in arr] + [
        b for arr in POSITIONING_BEACONS_BY_FLOOR.values() for b in arr
    ]


def load_layout_config():
    """SIM_LAYOUT_FILE-с site/floor/beacon config ачаална."""
    global SITE_NAME, SITE_ID, FLOORS
    global FLOOR_BEACONS_BY_FLOOR, POSITIONING_BEACONS_BY_FLOOR, ALL_BEACONS

    if yaml is None:
        print("[WARN] PyYAML алга байна. Default museum beacon config ашиглана.")
        _set_default_layout()
        return

    try:
        resolved_layout = resolve_layout_path(SIM_LAYOUT_FILE)
        if not resolved_layout:
            raise FileNotFoundError(f"Layout file not found: {SIM_LAYOUT_FILE}")

        with open(resolved_layout, "r", encoding="utf-8") as f:
            cfg = yaml.safe_load(f) or {}

        site = cfg.get("site", {})
        museum = cfg.get("museum", {})
        beacons = museum.get("beacons", {})
        positions = beacons.get("positions", []) or []

        SITE_NAME = site.get("name", SITE_NAME)
        SITE_ID = site.get("id", SITE_ID)

        floor_map = {}
        pos_map = {}
        all_beacons = []

        for p in positions:
            beacon_id = p.get("id")
            floor = int(p.get("floor", 1))
            role = p.get("role", "")
            if not beacon_id:
                continue

            all_beacons.append(beacon_id)
            if role == "floor-anchor":
                floor_map.setdefault(floor, []).append(beacon_id)
            else:
                pos_map.setdefault(floor, []).append(beacon_id)

        floors = sorted(set(list(floor_map.keys()) + list(pos_map.keys())))
        if not floors:
            raise ValueError("Layout дээр beacon positions байхгүй байна.")

        FLOOR_BEACONS_BY_FLOOR = floor_map
        POSITIONING_BEACONS_BY_FLOOR = pos_map
        FLOORS = floors
        ALL_BEACONS = all_beacons
        print(f"[INIT] Layout loaded from: {resolved_layout}")
    except Exception as e:
        print(f"[WARN] Layout уншихад алдаа гарлаа ({SIM_LAYOUT_FILE}): {e}")
        print("[WARN] Default museum beacon config ашиглана.")
        _set_default_layout()


def maybe_change_floor(current_floor):
    """Хэрэглэгч бага магадлалаар давхар солино."""
    if len(FLOORS) <= 1:
        return current_floor
    if random.random() < 0.15:
        candidates = [f for f in FLOORS if f != current_floor]
        return random.choice(candidates)
    return current_floor


def random_ble_readings(current_floor, num_beacons=4):
    """Тухайн floor-т ойр beacon-уудыг илүү хүчтэй, бусдыг сул үүсгэнэ."""
    readings = []

    anchors = FLOOR_BEACONS_BY_FLOOR.get(current_floor, [])
    if anchors:
        readings.append({"beaconId": random.choice(anchors), "rssi": random.randint(-70, -45)})

    same_floor = POSITIONING_BEACONS_BY_FLOOR.get(current_floor, [])
    other_floors = []
    for floor, arr in POSITIONING_BEACONS_BY_FLOOR.items():
        if floor != current_floor:
            other_floors.extend(arr)

    remaining = max(1, num_beacons - len(readings))
    strong_count = min(len(same_floor), max(1, int(remaining * 0.7)))
    weak_count = max(0, remaining - strong_count)

    if same_floor and strong_count > 0:
        for beacon_id in random.sample(same_floor, strong_count):
            readings.append({"beaconId": beacon_id, "rssi": random.randint(-78, -48)})

    weak_pool = other_floors if other_floors else same_floor
    if weak_pool and weak_count > 0:
        for beacon_id in random.sample(weak_pool, min(weak_count, len(weak_pool))):
            readings.append({"beaconId": beacon_id, "rssi": random.randint(-98, -75)})

    if not readings and ALL_BEACONS:
        readings.append({"beaconId": random.choice(ALL_BEACONS), "rssi": random.randint(-90, -55)})

    return readings


def fetch_art_ids():
    """ArtInfo service-с бүх art ID-үүд авна."""
    global ART_IDS
    try:
        r = requests.get(f"{ARTINFO_URL}/internal/art", timeout=3)
        if r.status_code == 200:
            arts = r.json()
            ART_IDS = [a["artId"] for a in arts if a.get("artId")]
            print(f"[INIT] Art IDs loaded: {len(ART_IDS)} arts")
            if ART_IDS:
                print(f"[INIT] Sample IDs: {ART_IDS[:3]}")
            return
        print(f"[WARN] ArtInfo returned {r.status_code}")
    except Exception as e:
        print(f"[WARN] Could not fetch art IDs: {e}")

    # Fallback — MongoDB-с авч чадахгүй үед
    print("[WARN] Using dummy art IDs")
    ART_IDS = []  # хоосон бол proximity scan хийхгүй


# ── Actions ──────────────────────────────────────────────────
def send_ble(user_id, current_floor):
    """BLE signal явуулна."""
    payload = {
        "deviceId": f"sim-user-{user_id:02d}",
        "timestamp": int(time.time() * 1000),
        "readings": random_ble_readings(current_floor, random.randint(3, 6)),
    }
    try:
        inc("ble_sent")
        r = requests.post(f"{CORE_URL}/ble/readings", json=payload, timeout=3)
        if r.status_code in (200, 202):
            inc("ble_ok")
            status = "ok"
            try:
                status = r.json().get("status", "ok")
            except Exception:
                status = "ok"
            log(user_id, f"BLE(f{current_floor}) -> {status}")
        else:
            inc("errors")
            log(user_id, f"BLE -> {r.status_code}")
    except Exception as e:
        inc("errors")
        log(user_id, f"BLE error: {e}")


def send_proximity(user_id, source):
    """QR эсвэл NFC scan явуулна."""
    if not ART_IDS:
        return
    art_id = random.choice(ART_IDS)
    payload = {
        "deviceId": f"sim-user-{user_id:02d}",
        "artId": art_id,
        "source": source,
    }
    try:
        inc("proximity_sent")
        r = requests.post(f"{CORE_URL}/proximity/scan", json=payload, timeout=3)
        if r.status_code in (200, 202):
            inc("proximity_ok")
            log(user_id, f"{source} -> artId={art_id[:8]}...")
        else:
            inc("errors")
            log(user_id, f"{source} -> {r.status_code}: {r.text[:80]}")
    except Exception as e:
        inc("errors")
        log(user_id, f"{source} error: {e}")


def websocket_listener(user_id):
    """WebSocket холбоод message хүлээнэ."""
    device_id = f"sim-user-{user_id:02d}"
    url = f"{WS_URL}/ws?deviceId={device_id}"

    def on_open(ws):
        inc("ws_connected")
        log(user_id, "WebSocket connected")

    def on_message(ws, message):
        inc("ws_messages")
        try:
            msg = json.loads(message)
            msg_type = msg.get("type", "?")
            payload = msg.get("payload", {})
            if msg_type == "location_update":
                floor = payload.get("floorId", "?")
                x = payload.get("x", "?")
                y = payload.get("y", "?")
                arts = payload.get("arts", [])
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
        on_close=on_close,
    )
    ws.run_forever()


# ── User simulation ──────────────────────────────────────────
def simulate_user(user_id, stop_event):
    """Нэг хэрэглэгчийн random үйлдлүүд."""
    current_floor = random.choice(FLOORS)

    # WebSocket-г тусдаа thread-д асаана
    ws_thread = threading.Thread(target=websocket_listener, args=(user_id,), daemon=True)
    ws_thread.start()

    # WebSocket холбогдох хүртэл хүлээ
    time.sleep(random.uniform(0.5, 2.0))

    actions = ["ble", "ble", "ble", "qr", "nfc"]  # BLE илүү байх магадлалтай

    while not stop_event.is_set():
        current_floor = maybe_change_floor(current_floor)
        action = random.choice(actions)

        if action == "ble":
            send_ble(user_id, current_floor)
        elif action == "qr":
            send_proximity(user_id, "QR")
        elif action == "nfc":
            send_proximity(user_id, "NFC")

        # Random хугацаа хүлээнэ
        wait = random.uniform(MIN_INTERVAL, MAX_INTERVAL)
        stop_event.wait(wait)


# ── Main ─────────────────────────────────────────────────────
def print_stats():
    print("\n" + "=" * 50)
    print("SIMULATION STATS")
    print("=" * 50)
    print(f"  BLE requests:       {stats['ble_sent']} sent, {stats['ble_ok']} ok")
    print(f"  Proximity requests: {stats['proximity_sent']} sent, {stats['proximity_ok']} ok")
    print(f"  WebSocket:          {stats['ws_connected']} connected, {stats['ws_messages']} messages")
    print(f"  Errors:             {stats['errors']}")
    print("=" * 50)


def main():
    load_layout_config()

    print("=" * 50)
    print("Smart Museum User Simulator")
    print(f"   Users: {NUM_USERS} | Duration: {SIM_DURATION}s")
    print(f"   Core: {CORE_URL} | ArtInfo: {ARTINFO_URL} | WS: {WS_URL}")
    print(f"   Site: {SITE_NAME} ({SITE_ID}) | Floors: {FLOORS} | Beacons: {len(ALL_BEACONS)}")
    print(f"   Layout: {SIM_LAYOUT_FILE}")
    print("=" * 50)

    # Art ID-үүд fetch хийнэ
    fetch_art_ids()

    stop_event = threading.Event()
    threads = []

    # Хэрэглэгч бүрийг тусдаа thread-д ажиллуулна
    for i in range(1, NUM_USERS + 1):
        t = threading.Thread(target=simulate_user, args=(i, stop_event), daemon=True)
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

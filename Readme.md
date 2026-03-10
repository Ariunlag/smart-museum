# 🏛️ Smart Indoor Positioning System

A microservices-based indoor real-time location system (RTLS) using BLE fingerprinting and Vector Database for approximate nearest neighbor search. Built with Spring Boot 3, Java 21, and Docker.

> **Novel contribution:** First known application of a Vector Database (Qdrant) for BLE RSSI fingerprint storage and ANN-based indoor positioning — replacing traditional O(n) KNN file-based approaches with scalable O(log n) vector similarity search.

---

## 📐 Architecture

```
Mobile Device (BLE Scan)
        │
        ▼ POST /ble/readings
  ┌─────────────┐
  │ Core Service│ :8080
  └──────┬──────┘
         │
    ┌────┴─────┬──────────┐
    ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌─────────┐
│Position│ │ArtInfo │ │Mosquitto│
│Service │ │Service │ │  MQTT   │
│ :8081  │ │ :8082  │ └────┬────┘
└───┬────┘ └───┬────┘      │
    │          │       ┌───▼─────┐
  Qdrant    MongoDB   │ Heatmap │
            (arts)    │ Service │
                      │  :8083  │
                      └─────────┘
        │
        ▼ WebSocket
  Mobile Device
  { floorId, x, y, arts[] }
```

---

## 🚀 Quick Start

### Prerequisites
- Docker Desktop
- Java 21
- Maven 3.9+

### 1. Start Infrastructure
```bash
docker compose up -d
```

### 2. Start Services
```bash
# Terminal 1
cd services/positioning-service && mvn spring-boot:run

# Terminal 2
cd services/artinfo-service && mvn spring-boot:run

# Terminal 3
cd services/heatmap-service && mvn spring-boot:run

# Terminal 4
cd services/core-service && mvn spring-boot:run
```

### 3. Test
```bash
# Send BLE readings
curl -X POST http://localhost:8080/ble/readings \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "phone-1",
    "timestamp": 1700000000000,
    "readings": [
      { "beaconId": "floor-beacon-f1", "rssi": -65 },
      { "beaconId": "beacon-3", "rssi": -72 },
      { "beaconId": "beacon-7", "rssi": -58 }
    ]
  }'
```

```javascript
// WebSocket (Browser Console)
const ws = new WebSocket("ws://localhost:8080/ws?deviceId=phone-1");
ws.onmessage = (e) => console.log(JSON.parse(e.data));
```

---

## 🗂️ Project Structure

```
smart-museum/
├── docker-compose.yml
├── mosquitto/
│   └── mosquitto.conf
├── pom.xml                          # Parent POM
└── services/
    ├── core-service/                # :8080 — Gateway, WebSocket, MQTT publish
    ├── positioning-service/         # :8081 — BLE → Grid, Qdrant ANN
    ├── artinfo-service/             # :8082 — MongoDB, nearest art
    └── heatmap-service/             # :8083 — MQTT subscribe, presence tracking
```

---

## ⚙️ Configuration

All system parameters are externalized to `application.yml` — no code changes required:

```yaml
# positioning-service/src/main/resources/application.yml
museum:
  building:
    floors: 3              # Number of floors
    grid-rows: 10          # Grid rows
    grid-cols: 10          # Grid columns
    cell-size-meters: 2    # Cell size in meters

  beacons:
    total: 20              # Total beacon count
    default-rssi: -100     # RSSI for undetected beacons
    floor-beacons:
      - id: "floor-beacon-f1"
        floor: 1

  floor:
    strategy: beacon       # beacon | manual | qr

  art:
    nearby-range: 2        # Search radius in grid cells
    max-results: 2         # Max arts to return
```

---

## 🔬 Algorithm

### BLE Fingerprinting Pipeline

```
1. Floor Detection
   BLE signals → floor beacon ID → floorId
   Strategy Pattern: beacon (now) → QR, manual (extensible)

2. Vector Construction
   20 beacons → float[20] vector
   Missing beacon RSSI = -100 dBm (default)
   Normalize: (rssi + 100) / 100 → [0.0 .. 1.0]

3. ANN Search (Qdrant)
   Filter: floorId = detected floor
   Similarity: Cosine distance
   Returns: gridId + confidence score

4. Grid → Coordinates
   "B3" → x = 2, y = 1
```

### Heatmap Logic

```
User enters grid C3 (was at B2):
  MQTT publish → { gridId: "C3", prevGridId: "B2", floorId: 1 }
  Heatmap: B2 count -1, C3 count +1

User disconnects:
  MQTT publish → { gridId: null, prevGridId: "C3" }
  Heatmap: C3 count -1
```

---

## 🌐 API Reference

### Core Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/ble/readings` | Submit BLE scan readings |
| WS | `/ws?deviceId=xxx` | WebSocket connection |

**WebSocket Response:**
```json
{
  "type": "location_update",
  "deviceId": "phone-1",
  "payload": {
    "floorId": 1,
    "x": 2,
    "y": 3,
    "arts": [
      {
        "artId": "...",
        "title": "Starry Night",
        "artist": "Vincent van Gogh",
        "description": "..."
      }
    ]
  }
}
```

### Positioning Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/internal/positioning/readings` | Locate device |

### ArtInfo Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/internal/art/nearest?gridId=B3&floorId=1` | Find nearest arts |

### Heatmap Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/heatmap/{floorId}` | Get heatmap for floor |

---

## 🏗️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.5, Java 21, Maven |
| Vector DB | Qdrant 1.9 (REST API) |
| Document DB | MongoDB 7.0 |
| Messaging | Eclipse Mosquitto 2.0 (MQTT) |
| Real-time | Spring WebSocket |
| Infrastructure | Docker, Docker Compose |
| Architecture | Microservices, Hexagonal (Ports & Adapters) |

---

## 🔭 Novelty

A systematic review of 1,600+ indoor positioning studies published between 2020–2024 (PRISMA methodology) found **no prior use of Vector Databases for BLE fingerprint storage and retrieval**. Existing approaches store fingerprints in flat files or relational databases and apply O(n) KNN search.

This system introduces:
- **Vector DB-backed fingerprinting** — O(log n) ANN search via Qdrant Cosine similarity
- **Dynamic fingerprint updates** — upsert without retraining
- **Microservice architecture** — independent scaling per service
- **Strategy Pattern floor detection** — extensible without core changes

---

## 🌍 Public Safety Applications

The domain-agnostic architecture supports extension to:

- 🏥 **Hospital** — Patient & staff real-time tracking
- 🚒 **Emergency Response** — Rescue team positioning
- 🏭 **Industrial** — Worker safety monitoring
- 🏫 **Schools** — Evacuation route guidance
- ✈️ **Airports** — Crowd flow management
- 🏟️ **Stadiums** — Panic & anomaly detection

---

## 📋 Roadmap

- [x] Core Service — BLE ingest, WebSocket, MQTT publish
- [x] Positioning Service — Floor detection, Vector builder, Qdrant ANN
- [x] ArtInfo Service — MongoDB, nearest art search
- [ ] Heatmap Service — Real-time presence, Admin API
- [ ] Docker — Full containerization
- [ ] JWT Security — Device authentication
- [ ] Accuracy benchmarking — Real-world testing
- [ ] Academic publication

---

## 📄 License

MIT License
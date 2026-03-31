ёүтт# Smart Museum

Microservice-based indoor positioning and exhibit interaction platform.

The system accepts BLE readings and proximity scans (QR/NFC), estimates user location by ANN search in Qdrant, pushes real-time updates over WebSocket, and keeps floor heatmap occupancy via MQTT.

## Overview

### Services

1. `core-service` (`:8080`)
   - Public API gateway for BLE and proximity events
   - WebSocket endpoint for real-time client updates
   - Orchestrates calls to Positioning and ArtInfo services
   - Publishes heatmap events to MQTT
   - Handles device inactivity timeout cleanup

2. `positioning-service` (`:8081`)
   - Converts BLE RSSI readings to vectors
   - Detects floor and grid location
   - Uses Qdrant ANN search

3. `artinfo-service` (`:8082`)
   - Provides nearest artworks by grid/floor
   - Provides artwork details by `artId`

4. `heatmap-service` (`:8083`)
   - Subscribes to MQTT movement events
   - Maintains in-memory occupancy counts
   - Persists heatmap snapshots to MongoDB
   - Removes zero-count records during persist

5. Infrastructure
   - `qdrant` (`:6333`)
   - `mongodb` (`:27017`)
   - `mosquitto` (`:1883`)

### Data flow

1. Device sends BLE to `POST /ble/readings`
2. Core calls Positioning for grid/floor
3. Core calls ArtInfo for nearby artworks
4. Core pushes location update via WebSocket
5. Core publishes movement event (`prev -> current`) to MQTT
6. Heatmap service updates occupancy and persists periodically

For QR/NFC:

1. Device sends `POST /proximity/scan`
2. Core calls ArtInfo `GET /internal/art/{artId}`
3. Core pushes exact artwork location and updates heatmap movement

## Repository structure

```text
smart-museum/
  docker-compose.yml
  pom.xml
  Readme.md
  mosquitto/
    mosquitto.conf
  services/
    core-service/
    positioning-service/
    artinfo-service/
    heatmap-service/
  simulator/
    simulate_users.py
  test/
```

## Quick start

### Prerequisites

1. Docker Desktop
2. Java 21
3. Maven 3.9+
4. Python 3.13+ (for simulator)

### Run everything with Docker

```bash
docker compose up --build -d
```

### Build from source manually

```bash
mvn clean package -DskipTests
docker compose up --build -d
```

### Check running containers

```bash
docker ps
```

## API reference

### Core service (`http://localhost:8080`)

1. `POST /ble/readings`
   - Accepts BLE readings from client device
   - Returns `202 Accepted`

Request:

```json
{
  "deviceId": "phone-1",
  "timestamp": 1700000000000,
  "readings": [
    { "beaconId": "floor-beacon-f1", "rssi": -65 },
    { "beaconId": "beacon-3", "rssi": -72 },
    { "beaconId": "beacon-7", "rssi": -58 }
  ]
}
```

2. `POST /proximity/scan`
   - Accepts QR/NFC scan with `artId`
   - Returns `202 Accepted`

Request:

```json
{
  "deviceId": "phone-1",
  "artId": "69a744a1169fd98c914457c4",
  "source": "QR"
}
```

3. `GET /services`
   - Returns active services from registry

4. `GET /admin/services`
   - Returns all registry entries

5. `PATCH /admin/services/{serviceId}/disable?adminId=admin`
6. `PATCH /admin/services/{serviceId}/enable`
7. `DELETE /admin/services/{serviceId}?adminId=admin`

8. `WS /ws?deviceId=phone-1`
   - Real-time push channel

Location update sample:

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
        "artId": "69a744a1169fd98c914457c4",
        "title": "Starry Night",
        "artist": "Vincent van Gogh",
        "description": "..."
      }
    ]
  }
}
```

### Positioning service (`http://localhost:8081`)

1. `POST /internal/positioning/readings`
   - Internal endpoint used by core-service
   - Returns grid/floor/coordinates/confidence

### ArtInfo service (`http://localhost:8082`)

1. `GET /internal/art/nearest?gridId=B3&floorId=1`
2. `GET /internal/art/{artId}`
3. `GET /internal/art`

### Heatmap service (`http://localhost:8083`)

1. `GET /api/heatmap/{floorId}`
   - Real-time in-memory map for floor
2. `GET /api/heatmap/{floorId}/history`
   - Persisted MongoDB records for floor

## Simulator

Path: `simulator/simulate_users.py`

### Install dependencies

```bash
cd simulator
pip install requests websocket-client
```

If using `uv` Python, use a local virtual environment:

```bash
cd simulator
uv venv
uv pip install requests websocket-client
.venv\Scripts\activate
python simulate_users.py
```

### Run

```bash
cd simulator
python simulate_users.py
```

It simulates multiple users sending:

1. BLE readings
2. QR scans
3. NFC scans
4. WebSocket listening for updates

## Heatmap behavior and anti-accumulation logic

### Movement updates

For each location change, core publishes both previous and current location:

```json
{
  "gridId": "C3",
  "floorId": 2,
  "prevGridId": "A7",
  "prevFloorId": 1,
  "timestamp": 1700000000000
}
```

Heatmap service decrements previous cell and increments new cell.

### User leaving museum

Handled in three ways:

1. WebSocket disconnect
   - Core publishes leave event for last known cell

2. Inactivity timeout (no BLE/proximity for configured time)
   - Core scheduled cleanup removes stale device
   - Core publishes leave event automatically

3. Device reconnect
   - Last location state is preserved correctly to avoid double counting

### MongoDB cleanup

Heatmap persistence now deletes records with `count <= 0`, preventing stale growth across repeated simulations.

## Key configuration

### Core service (`services/core-service/src/main/resources/application.yml`)

```yaml
museum:
  websocket:
    path: /ws
    inactivity-timeout-ms: 180000
    cleanup-interval-ms: 30000
```

### Heatmap service (`services/heatmap-service/src/main/resources/application.yml`)

```yaml
museum:
  heatmap:
    persist-interval-minutes: 1
    crowd-threshold: 10
```

## Troubleshooting

1. BLE/QR/NFC accepted but no WebSocket updates
   - Verify `core-service` is running on `8080`
   - Check WebSocket path `/ws`

2. Proximity requests return errors
   - Verify `artinfo-service` is running on `8082`
   - Confirm `GET /internal/art/{artId}` works

3. Heatmap totals look wrong
   - Wait one persist interval (default 1 minute) for MongoDB cleanup
   - Check core logs for `Heatmap published` transitions
   - Check inactivity timeout values

4. Simulator import errors in Python
   - Ensure the package is installed in the same Python interpreter used to run script

## Technology stack

1. Java 21
2. Spring Boot 3.5.x
3. Maven (multi-module)
4. MongoDB 7
5. Qdrant 1.9
6. Eclipse Mosquitto MQTT
7. Docker and Docker Compose

## License

MIT
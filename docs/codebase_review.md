# Smart Museum Codebase Review

This review is based on the actual repository structure and source code in the project. Where behavior is inferred rather than directly implemented, I label it as inference. Where something is not visible in the codebase, I say so explicitly.

## A. Project Summary

Smart Museum is a Spring Boot microservice system for indoor positioning and visitor context in a museum-like space. It ingests BLE readings and QR/NFC proximity scans, resolves a device location, pushes real-time updates to connected clients over WebSocket, and records movement events into a heatmap service through MQTT.

The main actors are:

- Mobile clients or the simulator in [simulator/simulate_users.py](../simulator/simulate_users.py)
- The gateway/orchestration service in [services/core-service](../services/core-service)
- The positioning service in [services/positioning-service](../services/positioning-service)
- The art metadata service in [services/artinfo-service](../services/artinfo-service)
- The heatmap service in [services/heatmap-service](../services/heatmap-service)
- Infrastructure components: MongoDB, Qdrant, and Mosquitto, configured in [docker-compose.yml](../docker-compose.yml)

Main engineering purpose:

- Convert noisy BLE signals into deterministic location results using fingerprint matching rather than training a model.
- Keep site-specific layout data in YAML so the same code can be redeployed to a different building by changing configuration.
- Separate concerns across services so location inference, art lookup, registry state, and heatmap persistence do not live in one monolith.

## B. Architecture Overview

### High-level components

- [core-service](../services/core-service): ingress gateway, orchestration, WebSocket push, service registry, and MQTT publish for heatmap updates.
- [positioning-service](../services/positioning-service): BLE vector building, floor detection, and Qdrant nearest-neighbor lookup.
- [artinfo-service](../services/artinfo-service): Mongo-backed art lookup by grid/floor or art ID.
- [heatmap-service](../services/heatmap-service): MQTT subscriber, in-memory occupancy counters, and periodic Mongo persistence.
- [simulator](../simulator/simulate_users.py): generates BLE and QR/NFC traffic and listens to WebSocket output.
- Static test UIs in [test.html](../test.html), [test-ui.html](../test-ui.html), and [admin-panel.html](../admin-panel.html).

### Responsibilities and connections

- The core service owns the user-facing API: `POST /ble/readings` and `POST /proximity/scan` in [BleIngestController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/BleIngestController.java) and [ProximityScanController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/ProximityScanController.java).
- Core calls positioning over HTTP through [PositioningHttpClient](../services/core-service/src/main/java/com/smartmuseum/core/integration/positioning/http/PositioningHttpClient.java).
- Core calls art lookup over HTTP through [ArtInfoHttpClient](../services/core-service/src/main/java/com/smartmuseum/core/integration/artinfo/http/ArtInfoHttpClient.java).
- Core sends occupancy transitions over MQTT through [HeatmapPublisher](../services/core-service/src/main/java/com/smartmuseum/core/integration/mqtt/HeatmapPublisher.java).
- Positioning uses Qdrant through [QdrantRepository](../services/positioning-service/src/main/java/com/smartmuseum/positioning/qdrant/QdrantRepository.java).
- Artinfo and heatmap services use MongoDB via Spring Data repositories.
- Service liveness is MQTT-based: each service publishes register and heartbeat messages from its own [ServiceRegistrar](../services/positioning-service/src/main/java/com/smartmuseum/positioning/registry/ServiceRegistrar.java), [ServiceRegistrar](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/registry/ServiceRegistrar.java), and [ServiceRegistrar](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/registry/ServiceRegistrar.java).

### Architectural style

This is a microservice architecture with a thin orchestration gateway and three focused backend services. The code is not using a message bus for everything; it mixes synchronous HTTP for request/response and MQTT for asynchronous movement events and registry health.

Inference:

- The architecture is intentionally split to keep the positioning algorithm, museum content, and crowd tracking independently deployable.
- The core service acts like an application gateway rather than a domain owner for all state.

## C. End-to-End Flow

### Flow 1: BLE ingestion to location update

1. A device or simulator sends `POST /ble/readings` to the core service through [BleIngestController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/BleIngestController.java).
2. The request is validated as a `BleIngestRequest` record in [BleIngestRequest](../services/core-service/src/main/java/com/smartmuseum/core/client/web/dto/BleIngestRequest.java).
3. [ProcessBleUseCase](../services/core-service/src/main/java/com/smartmuseum/core/orchestration/application/ProcessBleUseCase.java) first checks configuration and registry status:
   - positioning must be enabled in `museum.services.positioning.enabled`
   - `positioning-service` must be ACTIVE in Mongo registry
4. Core posts the BLE payload to positioning through [PositioningHttpClient](../services/core-service/src/main/java/com/smartmuseum/core/integration/positioning/http/PositioningHttpClient.java).
5. Positioning detects the floor using [BeaconFloorStrategy](../services/positioning-service/src/main/java/com/smartmuseum/positioning/floor/BeaconFloorStrategy.java), builds a normalized vector using [VectorBuilder](../services/positioning-service/src/main/java/com/smartmuseum/positioning/vector/VectorBuilder.java), and searches Qdrant through [QdrantRepository](../services/positioning-service/src/main/java/com/smartmuseum/positioning/qdrant/QdrantRepository.java).
6. The positioning response returns `floorId`, `gridId`, `x`, `y`, and `confidence` in [PositioningResponse](../services/positioning-service/src/main/java/com/smartmuseum/positioning/web/dto/PositioningResponse.java).
7. If art lookup is enabled and `artinfo-service` is ACTIVE, core asks artinfo for nearby art through [ArtInfoHttpClient](../services/core-service/src/main/java/com/smartmuseum/core/integration/artinfo/http/ArtInfoHttpClient.java).
8. Core pushes a `location_update` message to the device over WebSocket through [DeviceWebSocketHandler](../services/core-service/src/main/java/com/smartmuseum/core/client/ws/DeviceWebSocketHandler.java).
9. Core updates session state in [SessionRegistry](../services/core-service/src/main/java/com/smartmuseum/core/client/ws/SessionRegistry.java).
10. If heatmap is enabled and the location changed, core publishes a heatmap event over MQTT through [HeatmapPublisher](../services/core-service/src/main/java/com/smartmuseum/core/integration/mqtt/HeatmapPublisher.java).

Why this flow matters:

- The synchronous part ends with a user-visible response and WebSocket push.
- The heatmap update is decoupled so crowd counting does not block the user location response.

### Flow 2: QR/NFC scan to exhibit context

1. A device sends `POST /proximity/scan` to the core service through [ProximityScanController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/ProximityScanController.java).
2. [ProximityScanUseCase](../services/core-service/src/main/java/com/smartmuseum/core/orchestration/application/ProximityScanUseCase.java) checks whether `artinfo-service` is ACTIVE.
3. If the registry says artinfo is unavailable, the core pushes a `service_unavailable` WebSocket message and stops.
4. If artinfo is available, core fetches the art by ID using [ArtInfoHttpClient](../services/core-service/src/main/java/com/smartmuseum/core/integration/artinfo/http/ArtInfoHttpClient.java).
5. The response is transformed into a `location_update` message that includes a single art payload and the exhibit grid coordinates.
6. If the device location changed, core publishes a heatmap transition event and updates the session registry.

### Flow 3: Heatmap event processing

1. The core publishes a retained MQTT message to the configured heatmap topic in [HeatmapPublisher](../services/core-service/src/main/java/com/smartmuseum/core/integration/mqtt/HeatmapPublisher.java).
2. The heatmap service subscribes in [HeatmapSubscriber](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/mqtt/HeatmapSubscriber.java).
3. [HeatmapService](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/service/HeatmapService.java) applies the delta:
   - decrement the previous grid if one exists
   - increment the new grid
4. The in-memory store in [HeatmapStore](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/store/HeatmapStore.java) is periodically flushed to MongoDB in the `heatmap` collection.

### Flow 4: Service registry lifecycle

1. Each service publishes a register message on startup from its [ServiceRegistrar](../services/positioning-service/src/main/java/com/smartmuseum/positioning/registry/ServiceRegistrar.java).
2. It also publishes heartbeats on a fixed schedule.
3. The core service listens to register and heartbeat topics in [RegistryMqttSubscriber](../services/core-service/src/main/java/com/smartmuseum/core/registry/RegistryMqttSubscriber.java).
4. [RegistryService](../services/core-service/src/main/java/com/smartmuseum/core/registry/RegistryService.java) stores records in MongoDB and marks stale ACTIVE services OFFLINE.
5. Admin actions via [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java) can disable, enable, or soft-delete services.

## D. API / Endpoint Analysis

### Core service endpoints

- `POST /ble/readings` in [BleIngestController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/BleIngestController.java)
  - Purpose: ingest BLE scans from devices or the simulator.
  - Pattern: accepted immediately, then orchestrates downstream calls.
- `POST /proximity/scan` in [ProximityScanController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/ProximityScanController.java)
  - Purpose: handle QR/NFC/manual exhibit scans.
  - Pattern: accepted immediately, then resolves art and location.
- `GET /services` in [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java)
  - Purpose: public list of ACTIVE services.
- `GET /admin/services` in [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java)
  - Purpose: full registry view for admin tooling.
- `GET /admin/site-config` in [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java)
  - Purpose: expose site identity so the admin panel can choose the correct layout file.
- `PATCH /admin/services/{serviceId}/disable` in [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java)
- `PATCH /admin/services/{serviceId}/enable` in [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java)
- `DELETE /admin/services/{serviceId}` in [AdminController](../services/core-service/src/main/java/com/smartmuseum/core/registry/AdminController.java)

### Positioning service endpoints

- `POST /internal/positioning/readings` in [PositioningController](../services/positioning-service/src/main/java/com/smartmuseum/positioning/web/PositioningController.java)
  - Purpose: convert BLE readings into a predicted grid.
  - This endpoint is internal to the service mesh, not user-facing.

### Art info service endpoints

- `GET /internal/art/nearest` in [ArtInfoController](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/web/ArtInfoController.java)
  - Purpose: return nearby exhibits for a grid/floor pair.
- `GET /internal/art/{artId}` in [ArtInfoController](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/web/ArtInfoController.java)
  - Purpose: return one exhibit with location metadata for QR/NFC flows.
- `GET /internal/art` in [ArtInfoController](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/web/ArtInfoController.java)
  - Purpose: list all seeded art entries, used by the simulator.

### Heatmap service endpoints

- `GET /api/heatmap/{floorId}` in [HeatmapController](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/web/HeatmapController.java)
  - Purpose: return the current in-memory visitor counts for a floor.
- `GET /api/heatmap/{floorId}/history` in [HeatmapController](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/web/HeatmapController.java)
  - Purpose: return persisted MongoDB history for the floor.

### Request/response shape

- BLE requests use `deviceId`, `timestamp`, and a list of beacon RSSI values in [BleIngestRequest](../services/core-service/src/main/java/com/smartmuseum/core/client/web/dto/BleIngestRequest.java) and [BleReadingsRequest](../services/positioning-service/src/main/java/com/smartmuseum/positioning/web/dto/BleReadingsRequest.java).
- Positioning returns a simple location DTO with confidence in [PositioningResponse](../services/positioning-service/src/main/java/com/smartmuseum/positioning/web/dto/PositioningResponse.java).
- Art lookup returns a list of art DTOs or a single art with coordinates in [ArtInfoResponse](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/web/dto/ArtInfoResponse.java) and [ArtByIdResponse](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/web/dto/ArtByIdResponse.java).
- Core WebSocket pushes use the small envelope `type`, `deviceId`, and arbitrary `payload` in [PushMessage](../services/core-service/src/main/java/com/smartmuseum/core/client/ws/dto/PushMessage.java).

## E. Data Model / State Flow

### Main entities

- `ServiceRecord` in [ServiceRecord](../services/core-service/src/main/java/com/smartmuseum/core/registry/domain/ServiceRecord.java)
  - Mongo collection: `service_registry`
  - Status enum: `ACTIVE`, `OFFLINE`, `DISABLED`, `REMOVED`
  - Tracks first seen time, last heartbeat, last update, and admin audit fields.
- `Art` in [Art](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/domain/Art.java)
  - Mongo collection: `arts`
  - Stores exhibit identity, title, artist, description, grid, floor, and coordinates.
- `GridCount` in [GridCount](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/store/GridCount.java)
  - Mongo collection: `heatmap`
  - Stores the current crowd count per `floorId:gridId`.
- `SessionRegistry.DeviceState` in [SessionRegistry](../services/core-service/src/main/java/com/smartmuseum/core/client/ws/SessionRegistry.java)
  - In-memory state only.
  - Tracks the WebSocket session, last grid, last floor, and last seen time for a device.

### Relationships and lifecycle

- Service registry lifecycle:
  - startup register event creates or updates a record
  - heartbeat keeps ACTIVE services fresh
  - stale ACTIVE services become OFFLINE
  - admin can DISABLE or REMOVED a record
- Art lifecycle:
  - seed once at startup if the collection is empty in [ArtSeeder](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/seed/ArtSeeder.java)
  - read-only afterward from the service perspective
- Heatmap lifecycle:
  - event-driven increments and decrements in memory
  - periodic persistence to MongoDB
  - no per-device historical session model beyond the current count state
- Device session lifecycle:
  - WebSocket connect adds a session entry
  - movement updates touch or update the entry
  - inactivity cleanup removes the device and may emit a heatmap leave event

### State transition detail

The system uses a hybrid state model:

- authoritative service status is persisted in MongoDB
- real-time device presence is in memory in `SessionRegistry`
- crowd counts are in memory first, then persisted periodically

This is pragmatic, but it means core live state is not fully durable across restarts.

## F. Key Design Decisions

### Why the system appears to be designed this way

1. BLE fingerprinting over pure triangulation

   Indoor RSSI is noisy. The positioning code and README show a deterministic radio model plus Qdrant cosine search rather than a geometric solver. That reduces sensitivity to multipath and missing beacons.

2. Configuration-driven deployment

   Site-specific geometry is externalized in [config/smart_museum/layout.yml](../config/smart_museum/layout.yml) and [config/smart_university/layout.yml](../config/smart_university/layout.yml). The same code can be switched between sites through `.env.*` and layout selection.

3. Service registry as data, not hard-coded knowledge

   Each service self-registers via MQTT. Core reads status from MongoDB and the admin panel manipulates registry records instead of using static service URLs only.

4. Mixed sync and async communication

   HTTP is used for request/response operations where a user needs an immediate answer.
   MQTT is used for decoupled occupancy events and registry health.

5. In-memory first heatmap

   Counting visitors in memory is fast, and periodic persistence reduces write amplification. The tradeoff is durability.

### Strengths

- Good separation between orchestration, positioning, art metadata, and occupancy.
- Low operational complexity compared with a trained ML pipeline.
- Environment switching is mostly configuration-only.
- The registry gives the operator a visible operational model instead of implicit service discovery.

### Tradeoffs

- Positioning accuracy depends heavily on beacon placement and the YAML model.
- In-memory state means a restart can lose active session context and transient heatmap counts.
- The core gateway is doing a lot: orchestration, registry checks, WebSocket, and MQTT publishing.
- There is no message broker abstraction beyond MQTT topic conventions.

### Possible alternatives

- Replace retained MQTT register/heartbeat traffic with a dedicated service discovery system.
- Move heatmap state to an event-sourced store if restart durability is important.
- Add OpenAPI and typed client generation to reduce manual HTTP coupling.
- Replace the custom location push protocol with a standard event schema if multiple frontends grow.

## G. Risks / Weaknesses

### Operational and scaling risks

- The core service is a coordination hotspot. If it becomes slow or unavailable, both BLE and proximity flows stop.
- `SessionRegistry` and `HeatmapStore` are memory resident. They do not survive process restarts.
- MQTT topics are hard-coded conventions. A topic mismatch breaks the flow silently at runtime.
- Qdrant search and Mongo access are synchronous `.block()` calls in the HTTP path, which can limit throughput under load.

### Reliability risks

- Heartbeat and register handling assume the broker is reachable at startup.
- The registry marks services OFFLINE based on timeout, but if the service emits no heartbeats due to a runtime bug, the system only notices by timeout.
- Heatmap persistence is scheduled; a crash before the next persist interval loses recent counts.

### Maintainability risks

- Core has several responsibilities in one service: routing, session management, registry state, and event publishing.
- There is no visible API contract file such as OpenAPI or AsyncAPI. That makes integration more dependent on reading code.
- There is no evident authentication or authorization layer in the backend services.
- CORS is permissive in [core-service CorsConfig](../services/core-service/src/main/java/com/smartmuseum/core/common/config/CorsConfig.java) and [heatmap-service CorsConfig](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/config/CorsConfig.java).

### Testing gaps

- The repository has unit tests for core business logic, but no obvious controller, broker, or end-to-end integration tests.
- There is no visible contract test coverage between core and the downstream services.
- The simulator is helpful, but it is not a substitute for automated integration verification.

## H. Defense Preparation

### Core service

What to say:

- The core service is the orchestration boundary. It validates requests, checks service liveness, routes to positioning and art lookup, pushes WebSocket updates, and publishes movement events for heatmap processing.

Why this design is reasonable:

- It keeps the user-facing API in one place while the specialized services remain focused.

Likely questions and strong answers:

- Why is core doing so much?
  - Because it is the coordination point between user traffic and multiple downstream services. The tradeoff is higher responsibility, but the benefit is a simple external entry point.
- Why use both HTTP and MQTT?
  - HTTP is best for synchronous query/response. MQTT is better for decoupled movement and registry events.

### Positioning service

What to say:

- Positioning is deterministic. BLE readings are normalized into a fixed-order vector, floor detection is strategy-based, and Qdrant cosine similarity finds the best fingerprint for the current floor.

Why this design is reasonable:

- It avoids retraining and works well when the environment changes only by config.

Likely questions and strong answers:

- Why not pure triangulation?
  - RSSI is too noisy indoors. Fingerprint matching is more robust.
- Why not ML?
  - The code intentionally favors repeatability and deployment simplicity over model management.
- Why Qdrant?
  - It gives vector search with cosine distance and payload filtering by floor.

### Art info service

What to say:

- Art info is a simple Mongo-backed lookup service. For BLE flows it returns nearby artworks by Manhattan distance in grid space; for QR/NFC it returns an exact exhibit by ID.

Why this design is reasonable:

- It keeps exhibit metadata independent from the positioning engine and from the core orchestrator.

Likely questions and strong answers:

- Why nearest by Manhattan distance?
  - The grid is discrete, so Manhattan distance is a simple and explainable proximity heuristic.
- Why seed data at startup?
  - It makes the demo reproducible and keeps the service self-contained for fresh deployments.

### Heatmap service

What to say:

- Heatmap is event-driven and optimized for fast updates. It keeps active counts in memory, then persists snapshots to MongoDB on a schedule.

Why this design is reasonable:

- It avoids writing on every movement and keeps the runtime path cheap.

Likely questions and strong answers:

- What happens on restart?
  - In-memory counts are lost until new events arrive. That is an accepted tradeoff in the current design.
- Why not persist every event?
  - The design prioritizes lower write load and simpler runtime behavior.

### Registry and admin panel

What to say:

- The registry is a lightweight operational catalog backed by MongoDB and fed by MQTT register/heartbeat events. The admin panel reads and changes service state via HTTP.

Why this design is reasonable:

- It makes service health visible without requiring an external discovery stack.

Likely questions and strong answers:

- How do you know a service is alive?
  - It must heartbeat on schedule. If it stops, the core marks it OFFLINE.
- Can an admin override service status?
  - Yes. That is intentional for operational control.

## I. Short SWE Defense Script

Smart Museum is a microservice-based indoor positioning system. The core service is the orchestration boundary: it accepts BLE and QR/NFC requests, checks service liveness through a Mongo-backed registry fed by MQTT heartbeats, calls the positioning service to resolve a floor and grid, calls the art service for nearby exhibit context, then pushes the result to the device over WebSocket. Positioning is deterministic and configuration-driven: BLE readings are normalized into a fixed-order vector, Qdrant cosine search finds the best fingerprint, and site-specific geometry lives in YAML so the same code can run in a different building without retraining. Heatmap updates are decoupled through MQTT and persisted periodically, which keeps the hot path fast at the cost of some durability in memory-only state. The main tradeoff is that the core and heatmap services hold important transient state in memory, but that was chosen to keep the system simple, responsive, and easy to redeploy.

## Evidence Pointers

- Core entry points: [BleIngestController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/BleIngestController.java), [ProximityScanController](../services/core-service/src/main/java/com/smartmuseum/core/client/web/ProximityScanController.java)
- Orchestration logic: [ProcessBleUseCase](../services/core-service/src/main/java/com/smartmuseum/core/orchestration/application/ProcessBleUseCase.java), [ProximityScanUseCase](../services/core-service/src/main/java/com/smartmuseum/core/orchestration/application/ProximityScanUseCase.java)
- Registry and MQTT subscriber: [RegistryService](../services/core-service/src/main/java/com/smartmuseum/core/registry/RegistryService.java), [RegistryMqttSubscriber](../services/core-service/src/main/java/com/smartmuseum/core/registry/RegistryMqttSubscriber.java)
- Positioning pipeline: [PositioningService](../services/positioning-service/src/main/java/com/smartmuseum/positioning/service/PositioningService.java), [VectorBuilder](../services/positioning-service/src/main/java/com/smartmuseum/positioning/vector/VectorBuilder.java), [QdrantRepository](../services/positioning-service/src/main/java/com/smartmuseum/positioning/qdrant/QdrantRepository.java)
- Art lookup: [ArtInfoService](../services/artinfo-service/src/main/java/com/smartmuseum/artinfo/service/ArtInfoService.java)
- Heatmap processing: [HeatmapService](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/service/HeatmapService.java), [HeatmapStore](../services/heatmap-service/src/main/java/com/smartmuseum/heatmap/store/HeatmapStore.java)
- Deployment wiring: [docker-compose.yml](../docker-compose.yml), [pom.xml](../pom.xml), [config/smart_museum/layout.yml](../config/smart_museum/layout.yml), [config/smart_university/layout.yml](../config/smart_university/layout.yml)

## Notes on missing pieces

- No explicit authentication or authorization layer is evident in the backend codebase.
- No OpenAPI or AsyncAPI definition is evident in the repository.
- No controller-level integration tests or broker-level contract tests are evident.
- Dockerfiles copy prebuilt JARs from `target/`, so source changes require a Maven package step before rebuilding images.
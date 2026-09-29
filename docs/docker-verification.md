# Docker end-to-end verification

Verified on 2026-09-28 using the project's unmodified `compose.yml` and `Dockerfile`.

## Environment

- Docker Desktop engine: 29.8.0; Docker Compose: v5.5.1.
- Kafka: `apache/kafka:3.9.1`, single-node KRaft broker.
- Application: built from source inside Docker, running the `kafka` profile on Temurin Java 21.0.12.1.
- Runtime user: `jackpot`, UID/GID 999 (non-root).
- HTTP: `http://localhost:8080`; Kafka host listener: `localhost:9092`.

## Results

| Check | Result |
| --- | --- |
| Image build and Compose startup | Passed; Kafka reported healthy and application health was UP |
| HTTP publication → Kafka → consumer → persistence | Passed; 100.00 fixed-pool bet contributed 5.00 |
| Variable reward at/above the limit | Passed; 100000.00 bet contributed 10000.00 to the initial 1000.00 pool, paid 11000.00, and reset the pool to 1000.00 |
| Duplicate HTTP publication | Passed; original decision and pool balances were unchanged |
| Repeated evaluation | Passed; returned the original reward without another draw |
| Direct Kafka redelivery | Passed; original reward was preserved and the following bet used the correct pool balance |
| Validation and lookup errors | Passed; invalid amounts returned 400, unknown jackpot/bet returned 404, conflicting ID returned 409 |
| Malformed Kafka payload | Passed; payload was observed in `jackpot-bets.DLT`, and the next record was processed |
| Concurrent publication | Passed; all 20 concurrent HTTP bets were consumed and each contributed 0.50 on a 10.00 stake |
| Broker outage | Passed; stopping Kafka produced HTTP 503 with `Retry-After: 1` and same-ID retry guidance after approximately 10.08 seconds |
| Broker recovery | Passed; restarting Kafka and resubmitting the same ID succeeded and produced one stored result |
| Final state | Application UP, Kafka healthy, zero lag on the populated topic partition; other partitions were empty |

The normal-flow test run used ID prefix `docker-a93dcbf3c3`; the recovery bet was `outage-5b129779f0`. Tests created demo bets and one malformed message in the DLT. The containers were left running after verification.

This verifies a single local application instance and broker. It does not establish durability across application restarts: the assignment intentionally uses in-memory H2. Probability-boundary and concurrent cross-jackpot collision tests remain covered by the 45-test Maven suite.

## Commands

```sh
docker compose up --build -d
docker compose ps
curl -s http://localhost:8080/actuator/health
docker compose logs -f app
```

If Docker reports `docker-credential-desktop` is missing on macOS, use Docker Desktop's bundled helper for the command:

```sh
PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH" docker compose up --build -d
```

Stop this project's containers with `docker compose stop`. For a fresh demo, recreate the application and broker together using `docker compose down` followed by `docker compose up -d`.

# Jackpot contribution and reward service

Java 21 / Spring Boot 3.5.16 backend implementing the supplied Jackpot Backend Engineer assignment. It publishes bets to `jackpot-bets`, consumes them, contributes to the matching pool, makes one reward decision, records the result, and resets winning pools. H2 is intentionally in memory, as required by the assignment.

## Run

Requires JDK 21–25 and Maven 3.6.3+. Java 21 is the compilation target and the CI/container runtime. Spring Boot's compatibility reference: https://docs.spring.io/spring-boot/3.5/system-requirements.html.

### Without Docker (default local mode)

```sh
mvn clean verify
mvn spring-boot:run
```

Alternatively, after building:

```sh
java -jar target/jackpot-service-1.0.0-SNAPSHOT.jar
```

Local mode logs the bet payload and immediately invokes the same transactional processor used by the Kafka listener. This is the assignment's permitted broker mock, with a working end-to-end persistence flow. A `202` response still uses the same API contract as Kafka mode.

### With real Kafka

With Docker Engine / Docker Desktop running:

```sh
docker compose up --build
```

This starts a single-node Kafka broker in KRaft mode and the application. The application runs as a non-root container user. Ports are bound to localhost. No separate database is needed.

To run only Kafka in Docker and the application on your host:

```sh
docker compose up -d kafka
mvn spring-boot:run -Dspring-boot.run.profiles=kafka
```

For an existing broker, set `KAFKA_BOOTSTRAP_SERVERS` and activate the `kafka` profile. The service creates `jackpot-bets` and `jackpot-bets.DLT`, with three partitions each and a development replication factor of one. The broker must allow topic creation; otherwise provision both topics before startup and disable `spring.kafka.admin.auto-create`.

```sh
docker compose down
```

**Restart semantics:** application restart loses all H2 data, including idempotency history and rewards. Kafka offsets can outlive that data. For a fresh demo, recreate the Kafka container together with the application. This assignment configuration is one application instance; separate in-memory databases cannot safely share a consumer group.

## Use the API

Swagger UI is available at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).
Expand an operation, use **Try it out**, edit its example values, and click **Execute**.
Publish with `POST /api/v1/bets`, then retrieve or evaluate the same bet ID. Use a new `betId` for each new bet; reusing one returns its original result.

Swagger UI loads the documented contract from [http://localhost:8080/openapi.yml](http://localhost:8080/openapi.yml), packaged directly from `docs/openapi.yml` to keep one copy of the curated specification. Its relative server URL targets the application serving the page.
Springdoc also exposes controller-generated JSON at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).


```sh
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/v1/jackpots

curl -i -X POST http://localhost:8080/api/v1/bets \
  -H 'Content-Type: application/json' \
  -d '{"betId":"bet-001","userId":"user-42","jackpotId":"fixed","amount":100.00}'

curl -s -X POST http://localhost:8080/api/v1/bets/bet-001/evaluation
curl -s http://localhost:8080/api/v1/bets/bet-001
```

The publish endpoint returns `202 Accepted` with a `Location` header pointing to the result. In Kafka mode, acceptance means the broker acknowledged the publication; it does not mean the consumer has completed. Poll the result endpoint. Before processing (or for an unknown bet) it returns `404` with `code: BET_NOT_PROCESSED`; the API has no separate pending-bet store.

A typical losing result:

```json
{
  "betId": "bet-001",
  "userId": "user-42",
  "jackpotId": "fixed",
  "stakeAmount": 100.00,
  "contributionAmount": 5.00,
  "poolAfterContribution": 1005.00,
  "poolAfterEvaluation": 1005.00,
  "rewardChancePercent": 1.000000000000,
  "won": false,
  "rewardAmount": 0.00,
  "evaluatedAt": "2026-09-26T12:00:00Z"
}
```

Reward decisions are random, so this example is illustrative. To demonstrate a guaranteed win with the default configuration, publish a bet of `10000.00` to `variable`: its 10% contribution takes the initial 1000 pool to the 2000 reward limit. Use a fresh bet ID.

| Operation | Endpoint | Success |
| --- | --- | --- |
| Publish a bet | `POST /api/v1/bets` | 202 |
| Retrieve the persisted decision | `GET /api/v1/bets/{betId}` | 200 |
| Evaluate a contributed bet / retrieve its one-time decision | `POST /api/v1/bets/{betId}/evaluation` | 200 |
| Inspect pools | `GET /api/v1/jackpots` | 200 |

The consumer evaluates every bet automatically. The evaluation API returns that persisted decision; repeated calls never reroll or pay again. A reward record is an accounting decision, not an external wallet transfer.

IDs contain 1–64 ASCII letters, digits, underscores or hyphens. A bet amount must be positive, at most 12 integer digits and two fractional digits. Unknown JSON fields are rejected. `betId` is globally unique. An identical replay succeeds without another contribution or draw; changing its user, jackpot or amount returns `409` once the original has been processed. Conflicting events already queued in Kafka are rejected to the DLT by the consumer. In a race, the first committed payload wins. Clients must reuse the same ID after an uncertain publication or timeout.

Errors use RFC 9457 `application/problem+json`: validation/malformed JSON `400`, unknown pool/result `404`, conflicting ID `409`, and unconfirmed publication/transient DB contention `503` with `Retry-After: 1`. See [OpenAPI specification](docs/openapi.yml).

## Rules and assumptions

All monetary values use `BigDecimal` and `DECIMAL(24,2)`. Contributions round once to two decimal places using `HALF_UP`. Very small bets can contribute zero after rounding and still receive one reward evaluation. There is one implicit currency; no FX conversion or multi-currency pooling. All timestamps are UTC.

Pool configuration is in `src/main/resources/application.yml`; custom Spring configuration can override the definitions at startup. There is no unauthenticated administration API. Definitions are validated before pools or listeners start: duplicate IDs, unknown policy names, invalid percentages, an inverted contribution range and invalid variable reward limits fail startup.

The document specifies direction and bounds but not exact variable formulas. This implementation chooses:

- Fixed contribution: `betAmount × initialPercent / 100`.
- Variable contribution: calculate growth from the initial pool **before** this bet contributes. `steps = floor(max(pool - initialPool, 0) / poolStep)`. Contribution percent is `max(minimumPercent, initialPercent - steps × decreasePercentPerStep)`. Decrements are percentage points per complete pool step.
- Fixed reward: the configured chance, independent of pool size.
- Variable reward: after contribution, linearly interpolate from `initialChancePercent` at the initial pool to 100% at `poolLimit`. Below the initial pool the starting chance applies; at or above the limit the chance is exactly 100%. The limit applies to variable rewards only.
- Draw once against that probability using an injectable `SecureRandom` boundary. A winner receives the **entire post-contribution pool**, then the pool resets to its configured initial value in the same transaction. No excess carries over. Initial pool funding is assumed external and is outside this assignment.
- Pool resets also reset the growth used by variable policies. "Over time" is interpreted as pool growth, not elapsed wall-clock time, as described in the assignment.

Default pools:

| Jackpot | Initial pool | Contribution | Reward |
| --- | --- | --- | --- |
| `fixed` | 1000.00 | 5% | 1% chance |
| `variable` | 1000.00 | Starts at 10%, drops 1 point per 100 pool growth, floor 1% | Starts at 0.1%, reaches 100% at 2000 |

## Design and consistency

```text
REST -> BetService -> BetPublisher -> Kafka jackpot-bets -> KafkaBetListener
                           | local                            |
                           +-------------> BetProcessor <-----+
                                                |
                                  transaction + jackpot row lock
                                                |
                                  contribution/reward policies
                                                |
                             bets + contribution/reward ledger + pool
```

- API, messaging, application service, policy and persistence concerns are separate. Records represent immutable inputs/results; constructor injection makes dependencies explicit.
- `ContributionPolicy` and `RewardPolicy` are independent strategy interfaces. Add a Spring bean with a unique `type()` and configure it to extend the behavior. `PolicyRegistry` validates configured policies during startup. New policy parameters require an explicit configuration/schema extension.
- Spring Data JPA handles persistence; Flyway owns schema creation and Hibernate validates the mappings at startup. Open Session in View is disabled, and reads assemble results within a service-side transaction. Money never uses floating point arithmetic.
- `SELECT ... FOR UPDATE` locks one jackpot for the complete contribution, draw, reward insert and reset. Different jackpots can proceed independently. A second idempotency check after obtaining the lock handles concurrent duplicate deliveries. Ledger writes use `EntityManager.persist`, not merge-based repository saves. Global primary keys reject concurrent reuse of a bet ID across different pools; the conflicting transaction rolls back entirely and cannot overwrite a committed contribution or reward.
- Audit entities expose no public setters, and ledger repository interfaces expose only reads. Required contribution/reward records must exist; inconsistent data raises an error instead of returning a fabricated zero.
- `bets` stores the input and both win/loss decisions. `jackpot_contributions` and `jackpot_rewards` contain every audit field requested by the assignment. The contribution's current jackpot amount is the snapshot **after contribution and before any payout**. The result's post-evaluation pool is a historical snapshot, not the pool's current balance.
- Kafka records are keyed by jackpot ID. Producer idempotence and `acks=all` are enabled. The HTTP request waits for broker acknowledgement with bounded producer/response timeouts. Both synchronous Spring/Apache Kafka exceptions and asynchronous delivery failures are translated to retryable `503` responses.
- The listener returns only after the database transaction commits. Offsets are committed per record. A crash after database commit but before offset commit causes safe replay while the same database survives. This is at-least-once delivery with idempotent database effects, not a distributed exactly-once guarantee.
- Transient processing failures receive three retries, one second apart. Invalid, unknown-pool and conflicting events go directly to `jackpot-bets.DLT`. If publishing to the DLT fails, the original record remains eligible for redelivery. DLT records carry the original payload and failure metadata; inspect and correct the cause before manually replaying. There is no automatic DLT replay loop.
- Graceful shutdown, bounded database pooling and Actuator health probes are configured. Only health/info endpoints are exposed.

## Tests

```sh
mvn clean verify
```

The suite uses H2 and an **actual embedded Kafka broker**; Docker is unnecessary. It exercises fixed/variable boundaries, monetary rounding, winning and losing outcomes, configuration rejection, duplicate IDs and deterministically synchronized cross-pool races for both winning and losing bets, HTTP validation/errors, transaction rollback after a ledger write fails, concurrent distinct/duplicate bets, Kafka delivery, poison-message dead-lettering, synchronous/asynchronous publication failures, interrupt preservation and HTTP retry guidance. Tests replace randomness at the `RewardDraw` boundary. GitHub Actions runs the suite on Java 21.

Local verification: `mvn clean verify` passes all 45 tests on JDK 24, compiling for Java 21, including embedded Kafka. Lombok is explicitly configured as a compile-time annotation processor so clean builds also work on newer JDKs; it is excluded from the executable JAR. Spotless enforces Google Java Format during Maven validation (`mvn spotless:apply` fixes formatting). A packaged-JAR HTTP smoke test verified health, publication, a guaranteed 2000.00 payout and reset to 1000.00. Docker Compose was also verified on 2026-09-28 with the real Kafka broker and Java 21 runtime, including concurrent publication, direct Kafka replay, dead-letter handling, and broker outage/recovery. See [Docker verification results](docs/docker-verification.md).

## Production deployment boundary

The implementation uses production-oriented transaction, validation and retry patterns while keeping the assignment's in-memory constraint. Before handling real money, replace H2 with a shared durable database and test its locking semantics; retain idempotency records across restarts. Deploy authenticated/authorized APIs, TLS and Kafka ACLs, configure replicated Kafka topics, add rate limits, metrics/alerts and audited configuration changes. A real payout integration needs a transactional outbox and an idempotent wallet consumer; returning a reward does not transfer funds. The demo has no authentication and should remain on a trusted/local network.

## Submission

Public repository: [sandeepnk/jackpot-service-backend](https://github.com/sandeepnk/jackpot-service-backend). Source, tests, Docker configuration, Swagger UI and usage documentation are included. Build outputs, editor files and temporary artifacts are excluded from version control.

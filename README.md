# ServiceDesk: multi-service Spring Boot app for container practice

A support-ticket system built from **8 moving parts**. The application code is finished.
**All container work is yours:** Dockerfiles, nginx, compose, networks, volumes, healthchecks, CI.

```
 browser ──► frontend (static files, needs a reverse proxy for /api)
                │
                ▼
              api  (Spring Boot, :8080) ──► MySQL    (users, tickets, comments)
                │                      ├──► Redis    (cache for /tickets/stats, 60s TTL)
                │                      ├──► MinIO    (S3-compatible file attachments)
                │                      └──► RabbitMQ (publishes ticket events)
                                                │
                                                ▼
              worker (Spring Boot, :8081) ◄─ queue notifications.email  (+ dead-letter queue)
                │
                ▼
             MailHog (SMTP :1025, web UI :8025)
```

## Layout

```
api/        Spring Boot 3.3 / Java 17, Maven, JWT security, JPA, Redis, RabbitMQ, MinIO
worker/     Spring Boot 3.3 / Java 17, Maven, RabbitMQ listener, sends mail
frontend/   index.html (vanilla JS), calls relative /api/... URLs
```

Both Java projects build to `target/app.jar` (`mvn -B package -DskipTests`).

## Services your stack needs

| Service  | Suggested image         | Ports        | Notes |
|----------|-------------------------|--------------|-------|
| mysql    | mysql:8.4               | 3306         | DB `servicedesk` is auto-created by the JDBC URL |
| redis    | redis:7-alpine          | 6379         | |
| rabbitmq | rabbitmq:3.13-management| 5672 / 15672 | UI on 15672 |
| minio    | minio/minio             | 9000 / 9001  | `server /data --console-address ":9001"`; bucket is auto-created by the API |
| mailhog  | mailhog/mailhog         | 1025 / 8025  | inbox UI on 8025 |
| api      | build from `api/`       | 8080         | Swagger UI at `/swagger-ui.html` |
| worker   | build from `worker/`    | 8081         | no public API, only health/metrics |
| frontend | nginx                   | 80           | must proxy `/api/` to the api service |

## Environment variables (no config lives in the images)

**api**: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `RABBIT_HOST`, `RABBIT_PORT`,
`RABBIT_USER`, `RABBIT_PASSWORD`, `MINIO_ENDPOINT` (e.g. `http://minio:9000`), `MINIO_ACCESS_KEY`,
`MINIO_SECRET_KEY`, `MINIO_BUCKET`, `JWT_SECRET` (at least 32 characters)

**worker**: `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`

Every variable has a localhost default, so you can also run both apps from your IDE against local services.

## Health and metrics endpoints

- `GET /actuator/health/liveness` and `/actuator/health/readiness` on api (8080) and worker (8081)
- api readiness includes **db, redis and rabbit**; worker readiness includes **rabbit**
- `GET /actuator/prometheus` on both

## Try it

1. Open the frontend, log in as `agent@servicedesk.local` / `Agent@123` (or register a normal user).
2. Create a ticket, then check MailHog (:8025) for the "We received your request" email.
3. As agent, change the ticket status, then check MailHog again.
4. Attach a file to a ticket, then download it. The file lives in the MinIO console (:9001).
5. Dev accounts are seeded on first start: `admin@servicedesk.local / Admin@123`, `agent@servicedesk.local / Agent@123`.

## Your container work

**Level 1: images**
- Multi-stage Dockerfile for `api` and `worker` (Maven build stage, slim JRE runtime stage)
- Cache the Maven dependency layer so code changes don't re-download everything
- Run as a non-root user, add a `.dockerignore`
- Frontend image: nginx serving `index.html`, with a config that proxies `/api/` to the api container and allows uploads up to ~6 MB (`client_max_body_size`)

**Level 2: compose**
- All 8 services in one `docker-compose.yml`, secrets and settings in a `.env` file
- Two networks: `frontend-net` (frontend + api) and `backend-net` (everything else + api). Databases must not be reachable from the frontend.
- Named volumes for MySQL, Redis, RabbitMQ and MinIO data
- Healthchecks on every service that supports one, and `depends_on` with `condition: service_healthy`
- `restart: unless-stopped`; publish only the ports you actually need on the host

**Level 3: prove it works**
- `docker compose down` then `up`: tickets and attachments must survive. `down -v` should wipe them.
- Stop `rabbitmq`: the API must keep creating tickets (events are best effort). Start it again and confirm the worker reconnects.
- Stop `mysql`: api readiness should go DOWN, and recover when it returns.
- Scale the worker to 2 replicas and watch RabbitMQ share messages between them.
- Create a ticket titled `[FAIL] test`. After 3 attempts the message should land in `notifications.email.dlq` (see the RabbitMQ UI).

**Level 4: stretch**
- A `monitoring` compose profile with Prometheus + Grafana scraping both `/actuator/prometheus` endpoints
- GitHub Actions: build all three images, scan with Trivy, push to GHCR
- Shrink the Java images with Spring Boot layered jars
- Replace the hand-written MySQL tests with Testcontainers integration tests
- Translate the compose file to Kubernetes manifests

## Known simplifications (good follow-ups)

- Schema is created by `ddl-auto: update`; a real project would use Flyway migrations
- Events are published inside the DB transaction; the outbox pattern would make this reliable
- Seeded dev passwords are hard-coded in `DataSeeder`

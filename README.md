# On‑Prem → GCS Transfer Service (Quarkus)

A small, pragmatic service for moving files from various on‑prem sources into Google Cloud Storage (GCS), with tracking in a database (BigQuery in prod, H2 in dev/test) and optional event emission to kick off downstream ingest/ETL (via Eventarc → Pub/Sub → your pipelines).

> Design goals: simple, stream‑based, resumable, idempotent, observable. Short methods, small classes, interface‑driven where we have multiple impls.

---

## Contents

* [High‑level architecture](#high-level-architecture)
* [Key features](#key-features)
* [Data model](#data-model)
* [Configuration](#configuration)
* [Profiles & environments](#profiles--environments)
* [Runbook](#runbook)
* [Local dev](#local-dev)
* [Production](#production)
* [Interfaces & class layout](#interfaces--class-layout)
* [Resumable streaming to GCS](#resumable-streaming-to-gcs)
* [Eventing (Eventarc / PubSub)](#eventing-eventarc--pubsub)
* [Idempotency & dedupe](#idempotency--dedupe)
* [Observability](#observability)
* [Security & auth](#security--auth)
* [Error handling & retries](#error-handling--retries)
* [Testing strategy](#testing-strategy)
* [Why not Apache Camel?](#why-not-apache-camel)
* [Roadmap / TODO](#roadmap--todo)

---

## High‑level architecture

```
On‑prem sources ──> SourceProvider ─┐
                                    │   (streaming, zero/low buffering)
                          TransferOrchestrator ──> Sink (GCS / LocalFs)
                                    │
                                    ├─> Tracker (DB: H2/BigQuery)
                                    └─> EventEmitter (Pub/Sub via Eventarc)
```

* **Source providers**: Local filesystem, SFTP/FTPS, SMB/NFS (pluggable).
* **Sinks**: GCS (prod), Local folder (dev/test). Both use streaming IO.
* **Tracker**: records seen files, versions, checksums, sizes, offsets for resume, and run history.
* **Event emitter**: optional; publishes file‑ingested events to Pub/Sub to trigger downstream ETL.

## Key features

* **Streaming transfers** end‑to‑end: no large file buffering in memory.
* **Resumable uploads** to GCS (resumable session + offset tracking).
* **Resume from partial** on source read if supported (range/seek) or restart chunk.
* **Idempotent ingestion** keyed by `(feed_id, source_path, mtime, size, checksum?)`.
* **Pluggable sources/sinks** hidden behind small interfaces.
* **Environment‑specific impls** chosen by Quarkus profiles.
* **Dry‑run** mode: discover & log without copying.
* **Back‑pressure & parallelism controls** via config.
* **Integrity**: optional MD5/SHA‑256 validation; compare to GCS hashes.

## Data model

Minimal, split for simplicity:

**feeds**

* `feed_id` (string, PK) — logical feed name
* `source_uri` (string) — e.g. `sftp://host/path` or `file:///mnt/drop/xyz`
* `schedule` (string) — cron or external
* `active` (bool)
* `metadata` (json)

**files** (one row per discovered file version)

* `file_id` (string, PK) — stable hash of `(feed_id, source_path, mtime, size)`
* `feed_id` (FK)
* `source_path` (string)
* `size_bytes` (int64)
* `mtime_epoch_ms` (int64)
* `checksum_md5` (nullable string)
* `status` (enum: DISCOVERED, COPYING, COPIED, FAILED, SKIPPED)
* `gcs_uri` (string, nullable)
* `copied_at` (timestamp, nullable)
* `attempts` (int)

**runs** (execution history)

* `run_id` (string, PK)
* `feed_id` (FK)
* `started_at` / `ended_at` (timestamp)
* `status` (enum)
* `files_discovered` / `files_copied` / `files_failed` (int)
* `metrics` (json)

**resumes** (only if resumable uploads enabled)

* `file_id` (FK)
* `sink_session_id` (string) — GCS resumable session URI
* `bytes_committed` (int64)
* `updated_at` (timestamp)

> **Prod**: BigQuery tables; **Dev/Test**: H2 (file mode) with identical schema.

## Configuration

Use `application.yaml` for common values; overlay with per‑env YAML. Secrets come from env vars.

```yaml
# application.yaml (common)
transfer:
  parallelism: 4
  chunkSizeBytes: 8_388_608   # 8 MiB default
  dryRun: false
  checksum: md5               # md5 | sha256 | none
  maxRetries: 5
  backoff:
    initialMs: 500
    maxMs: 30_000
  emitEvents: true

sources:
  # Example logical feed configs
  feeds:
    - id: daily_ops
      uri: ${FEED_DAILY_URI}     # e.g. sftp://user@host:/path
      include: ["**/*.csv"]
      exclude: ["**/tmp/**"]
      destinationPrefix: daily/ops/
    - id: images
      uri: ${FEED_IMAGES_URI}
      include: ["**/*.jpg", "**/*.png"]
      destinationPrefix: raw/images/

sink:
  type: gcs                     # gcs | local
  local:
    path: /tmp/dev-sink
  gcs:
    bucket: ${GCS_BUCKET}
    basePath: ${GCS_BASE_PATH:/ingest}
    resumable: true
    preconditionIfGenerationMatch: true

tracker:
  type: jdbc                    # jdbc | bigquery

quarkus:
  http:
    port: 8080
  log:
    category:
      "app.transfer":
        level: INFO
  smallrye-metrics:
    enabled: true
```

Profiles:

```yaml
# application-%dev.yaml
sink:
  type: local
tracker:
  type: jdbc
  jdbc:
    url: jdbc:h2:file:./.data/h2db;MODE=PostgreSQL;AUTO_SERVER=TRUE
    username: sa
    password: ""

# application-%test.yaml
transfer:
  dryRun: true

# application-%prod.yaml
sink:
  type: gcs
tracker:
  type: bigquery
bigquery:
  dataset: file_ingest
  tablePrefix: core_
  # Auth via ADC; service account injected via env/Workload Identity
```

Environment variables (examples):

* `FEED_DAILY_URI`, `FEED_IMAGES_URI`
* `GCS_BUCKET`, `GCS_BASE_PATH`
* `GOOGLE_APPLICATION_CREDENTIALS` (if not using Workload Identity)

## Profiles & environments

* **dev**: H2 + Local sink (copy to a folder you can inspect). Optional GCS smoke tests.
* **test**: dry‑run by default; uses temp dirs; mocks for remote sources.
* **prod**: BigQuery + GCS with resumable uploads. Events enabled.

## Runbook

* `GET /health/ready` / `.../live` — readiness/liveness
* `POST /feeds/{id}/run` — trigger a one‑off pull & copy
* `GET /feeds` — list configured feeds
* `GET /runs/{runId}` — status of a run
* `GET /metrics` — Prometheus

## Local dev

1. **Java 21 + Quarkus**. `./mvnw quarkus:dev`.
2. Create `.env` with feed URIs and local sink path.
3. Verify H2 file created under `./.data/`.
4. Trigger a run: `curl -X POST :8080/feeds/daily_ops/run`.
5. Inspect `sink.local.path` for outputs.

### Sample source URIs

* Local: `file:///Users/joe/dropzone/daily_ops`
* SFTP: `sftp://user@host:/export/daily_ops` (key auth recommended)
* SMB/NFS: mount locally and use `file://` to keep it simple.

## Production

* Run in GKE or Cloud Run. Store config as ConfigMap/Secrets or Cloud Run env vars.
* **Auth**: Use Workload Identity / service accounts for GCS & BigQuery.
* **BigQuery**: create datasets/tables upfront or auto‑migrate on boot (toggleable).
* **Eventing**: enable Pub/Sub topic; use Eventarc if you want routing/fan‑out.

## Interfaces & class layout

Keep classes small; methods ≤ 20 lines. One responsibility each.

```java
public interface SourceProvider {
  Stream<FileDescriptor> list(Feed feed);
  InputStream open(FileDescriptor file, long offset) throws IOException;
}

public interface Sink {
  /** returns committed bytes so far (for resume) */
  long write(String destPath, InputStream in, long offset, long length, Map<String,String> meta);
}

public interface Tracker {
  void upsertFile(FileRecord rec);
  Optional<FileRecord> findByIdentity(FileIdentity id);
  void markStatus(String fileId, Status status, @Nullable String gcsUri);
  ResumeState loadResume(String fileId);
  void saveResume(String fileId, ResumeState state);
}

public final class TransferOrchestrator {
  // orchestrates list → filter → dedupe → copy(stream) → verify → mark → emit
}
```

**Impls:**

* `LocalFsSource`, `SftpSource` (later), `MountedShareSource`.
* `GcsSink` (resumable), `LocalFsSink`.
* `JdbcTracker` (H2 & Postgres‑compatible), `BigQueryTracker`.
* `PubSubEventEmitter` (optional).

## Resumable streaming to GCS

* Use GCS **resumable upload** sessions; store session URI + committed bytes in `resumes`.
* Write in configured chunks (e.g. 8–32 MiB). Flush per chunk.
* If interrupted, retrieve committed size, seek source via `open(file, offset)`, continue.
* Validate end‑to‑end with GCS‑reported MD5/SHA256 (if provided) or re‑hash stream while writing.
* Use `ifGenerationMatch` precondition to avoid overwriting unexpectedly (configurable).

## Eventing (Eventarc / PubSub)

* On `COPIED`, emit a small JSON message:

```json
{
  "feedId":"daily_ops",
  "sourcePath":"/export/daily/file1.csv",
  "gcsUri":"gs://bucket/ingest/daily_ops/2025/09/30/file1.csv",
  "sizeBytes":123456,
  "checksumMd5":"...",
  "copiedAt":"2025-09-30T21:55:00Z"
}
```

* Eventarc can route that Pub/Sub topic to downstream Cloud Run/Workflows/Dataflow jobs.
* Keep this service dumb; downstream app owns ETL logic.

## Idempotency & dedupe

* **Identity**: stable hash of `(feed_id, source_path, mtime, size)`; optionally include checksum.
* If identity already `COPIED`, skip.
* If identity `COPYING` and resume info exists, attempt resume; otherwise safe‑restart.
* Optionally enforce **GCS object naming** as `basePath/feed_id/YYYY/MM/DD/{filename}` to avoid collisions.

## Observability

* **Metrics (Micrometer/Prometheus)**

    * `transfer_files_total{status=...}`
    * `transfer_bytes_total`
    * `transfer_duration_seconds`
    * `resume_events_total`
    * `source_list_duration_seconds`
* **Structured logs** (JSON) with `runId`, `feedId`, `fileId` for correlation.
* **Tracing** (OpenTelemetry) optional; useful if SFTP or large fan‑out.

## Security & auth

* **GCP**: use ADC/Workload Identity in prod. In dev, service account key via env var.
* **Source auth**: SFTP key files via mounted secret; SMB/NFS handled by mount.
* No credentials in YAML; all via env/secrets.

## Error handling & retries

* Retry transient source/sink IO with exponential backoff (bounded).
* Circuit‑break a feed if many consecutive failures; alert via metrics.
* Mark `FAILED` with reason; next run re‑evaluates and may resume.
* Hard‑fail a file if checksum mismatch after max retries.

## Testing strategy

* **Unit**: interfaces with in‑memory fakes; small methods for easy coverage.
* **Contract tests**: ensure `SourceProvider` and `Sink` obey chunking/offset semantics.
* **Integration**: Local GCS emulator (if desired) or real GCS in a test project behind a flag.
* **E2E**: dev profile with sample dataset; assert files, sizes, checksums, events.

## Why not Apache Camel?

* You *can* do this in Camel (routes + camel‑quarkus components for SFTP/GCS). It’s fine when you need many protocols and declarative routing.
* Here the logic is simple and opinionated (resume, idempotency, specific DB tracking). Plain Quarkus with small interfaces keeps it tighter, easier to reason about, and faster to test.
* **When to revisit Camel**: if protocol explosion happens (AS2, JMS, MQTT, etc.), or you need EIPs (splitter/aggregator/content‑based router) you don’t want to hand‑roll.

## Roadmap / TODO

* [ ] Implement `GcsSink` with resumable sessions and MD5 verification.
* [ ] Implement `JdbcTracker` (H2/Postgres) + schema migration.
* [ ] Implement `BigQueryTracker` using official client; batch upserts.
* [ ] Implement `LocalFsSource` and optional `SftpSource`.
* [ ] Basic REST endpoints + OpenAPI.
* [ ] Pub/Sub emitter and Eventarc guide.
* [ ] Configurable object naming strategy (date‑partitioned).
* [ ] Optional file manifest (.json) alongside each batch in GCS.
* [ ] Concurrency guardrails to avoid saturating slow source shares.
* [ ] GCS lifecycle policy docs (auto‑tiering/retention) — out of scope of service.

---

## Appendix: Example object naming

```
/ingest/{feed_id}/{yyyy}/{MM}/{dd}/{hhmm}/{original_filename}
```

Pros: chronological partitioning, easier downstream windowing. Cons: duplicates if the same file reappears; your identity/dedupe handles that.

## Appendix: Minimal Make targets

```makefile
run: ## Run local dev
	./mvnw quarkus:dev

test: ## Run tests
	./mvnw test

fmt: ## Format
	./mvnw spotless:apply
```

## Appendix: Example docker run (dev)

```bash
docker run --rm -it \
  -e QUARKUS_PROFILE=dev \
  -e FEED_DAILY_URI=file:///data/daily_ops \
  -e GCS_BUCKET=dev-bucket \
  -v $PWD/sample-data:/data/daily_ops \
  -v $PWD/.data:/work/.data \
  -p 8080:8080 your/org/transfer:dev
```

---

### Anything else to consider?

* **POSIX atomicity**: prefer temp names + rename on complete when writing to local sink.
* **Clock skew**: don’t trust mtime alone; combine with size and optional checksum.
* **Large files**: tune chunk size; avoid too many concurrent large transfers.
* **Cold GCS buckets**: consider dual‑region or turbo replication if needed by RPO.
* **Access control**: object‑level ACLs vs uniform bucket‑level; default to uniform.
* **Downstream schema registry**: if you emit events, keep the payload small and versioned.
* **Backfill mode**: separate flag to allow re‑copy even if previously seen.
* **Delete policy**: this service **does not delete** source files; document retention separately.

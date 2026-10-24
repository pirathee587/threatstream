# Phase 16 test infrastructure audit

## Observed environment

The repository is intentionally a dirty worktree containing Phase 11–15 changes; no existing changes were reset. Java is OpenJDK 21.0.12. Docker Compose is available through the host Docker daemon. Node.js, npm, pnpm, yarn, and Corepack are absent.

The Kafka container was already healthy and `kafka-init` had completed successfully. `docker compose ... config --quiet` validates the base Compose file plus `docker-compose.integration.yml` and lists all expected services. The isolated profile uses `mongo-test`, database `threatstream_integration`, an isolated named volume, a loopback-only `127.0.0.1:27018` mapping for host-run Maven tests, and no Atlas URI.

## Blockers and resolutions

| Area | Result | Cause / resolution |
| --- | --- | --- |
| Mockito | Resolved for this repository | Mockito 5.17.0 attempted inline agent attachment under the restricted JVM. A test-resource `mock-maker-subclass` setting in each Mockito-using module selects Mockito's supported subclass mock maker. The full backend reactor then passed. |
| MongoDB integration | Blocked on this host | `mongo:8.0` refuses Linux 7.0.0. MongoDB documents a 6.19–7.0.13 kernel incompatibility. Run the isolated profile on a compatible host/kernel (or 7.0.14+) before asserting database/E2E results. |
| Application Compose build | Not completed | The attempted service build could not proceed once its required isolated MongoDB dependency exited. No app container was created. |
| Frontend | Blocked | No Node/package manager executable exists. The existing `package-lock.json` identifies npm as the compatible manager; no dependency or lockfile change was made. |

The MongoDB restriction is host-level, not an application defect; see the [MongoDB production notes](https://www.mongodb.com/docs/manual/administration/production-notes/) for the kernel limitation.

## Test levels

- Unit: standard Surefire `*Test`; no live Kafka or database required.
- Integration: the `integration-tests` profile invokes Failsafe for `*IT` classes only after the isolated profile is healthy. URI/database inputs are explicit `it.mongo.*` properties or `IT_MONGODB_*` variables; tests do not read a developer `.env` or `MONGODB_URI`.
- End-to-end: requires all three application services, Kafka topics, and `mongo-test`; use bounded polling against health/REST conditions, not sleeps.

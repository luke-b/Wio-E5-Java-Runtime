# Wio-E5 Java Runtime Production Readiness Implementation Plan

## 1) Project Goal
Deliver a production-ready embedded Java runtime for Seeed Studio Wio-E5 (STM32WLE5JC) that securely and reliably runs ROMized Java applications over LoRaWAN under strict Flash/SRAM/power constraints, with a validated runtime core, native hardware bindings, OTA lifecycle, and manufacturing-grade operational tooling.

## 2) Current State Summary (Architecture vs Codebase)
- **Available now:** Java API surface stubs (`static native`) for `Power`, `GPIO`, `LoRaWAN`, `I2C`, `UART`, and `NVConfig`.
- **Missing for production:** JVM/interpreter implementation, GC/runtime memory manager, ROMizer pipeline, C native bridge implementations, OTA update pipeline, secure boot/slot controls, provisioning flow, test strategy, CI/CD, and release/operations hardening.

## 3) Scope and Readiness Outcomes
Production readiness is achieved when:
1. Runtime architecture is implemented and memory-budget compliant.
2. Native APIs are complete, validated on hardware, and failure-safe.
3. OTA updates are atomic, authenticated, and rollback-capable.
4. Security controls (key handling, integrity checks, anti-replay) are enforced end-to-end.
5. Build, test, release, and field observability workflows are repeatable.

## 4) Epic Plan (Epics, Stories, DoD, Tracking)

### Epic 1 — Runtime Core (JVM + Execution Model)
**Objective:** Implement the minimal CLDC 1.1-compatible embedded runtime defined in architecture.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E1-S1 | Define runtime module boundaries and interfaces | Runtime components (interpreter, heap, frame stack, native dispatch) are split into clear modules with documented ownership and integration points | ⬜ Not Started |
| E1-S2 | Implement bytecode interpreter subset | Supported opcode subset executes deterministic sample programs; unsupported opcodes fail with explicit runtime errors | ⬜ Not Started |
| E1-S3 | Implement frame/stack model | Max frame depth, operand/local slot rules, and overflow behavior are enforced and tested | ⬜ Not Started |
| E1-S4 | Implement mark-sweep GC with bounded pauses | Heap allocation, mark roots, sweep, and pause-time metrics meet architecture targets on reference workloads | ⬜ Not Started |
| E1-S5 | Runtime stability testing on long-running loops | 24h soak run with no crashes/memory corruption and bounded GC behavior | ⬜ Not Started |

### Epic 2 — Native Binding Layer and HAL Integration
**Objective:** Provide complete, robust native implementations for current Java APIs.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E2-S1 | Implement native dispatch table and symbol mapping | Stable `(class,method)` to native index mapping exists, with versioned compatibility checks | ✅ Completed |
| E2-S2 | Implement `wioe5.system.Power` natives | Deep sleep, wake restore, watchdog, battery read, timing APIs validated on target board | ✅ Completed |
| E2-S3 | Implement `wioe5.io.GPIO`, `wioe5.bus.I2C`, `wioe5.bus.UART` natives | Pin/bus correctness validated with hardware loopback and sensor tests; error codes standardized | ✅ Completed |
| E2-S4 | Implement `wioe5.lora.LoRaWAN` natives and process loop contract | Join/send/downlink/status flow stable under packet loss and duty-cycle constraints | ✅ Completed |
| E2-S5 | Implement `wioe5.storage.NVConfig` with wear-aware writes | Key/value persistence survives reset/power cycle with data integrity checks | ✅ Completed |

### Epic 3 — ROMizer and Build Pipeline
**Objective:** Enable deterministic compilation from Java source to deployable runtime image.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E3-S1 | Implement ROMizer for class table/bytecode/native/static sections | ROMized artifact format matches runtime loader expectations and passes validation tools | ✅ Completed |
| E3-S2 | Deterministic build orchestration for Java + C outputs | Reproducible builds generate identical artifacts from same input/toolchain versions | ⬜ Not Started |
| E3-S3 | Memory budget enforcement gates | Build fails when flash/sram thresholds are exceeded; reports include per-section usage | ⬜ Not Started |
| E3-S4 | Developer onboarding build docs | End-to-end build/flash workflow is documented and repeatable on clean environment | ⬜ Not Started |

### Epic 4 — OTA Update System (A/B Slots + FDBT)
**Objective:** Deliver safe, recoverable LoRaWAN application updates.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E4-S1 | Implement fragmented download/reassembly to inactive slot | Fragment loss/retry handling verified; partial updates do not affect active slot | ⬜ Not Started |
| E4-S2 | Implement artifact integrity/authenticity checks | CRC + keyed signature/HMAC checks must pass before activation | ⬜ Not Started |
| E4-S3 | Atomic slot switch and rollback logic | Boot switches only on verified image; auto-fallback works on failed boot | ⬜ Not Started |
| E4-S4 | End-to-end OTA campaign rehearsal | Simulated fleet update demonstrates completion, failure recovery, and version tracking | ⬜ Not Started |

### Epic 5 — Security Hardening
**Objective:** Enforce secure device lifecycle and runtime controls.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E5-S1 | Secure provisioning path for identities/keys | DevEUI/AppKey provisioning is authenticated, auditable, and not exposed in logs/artifacts | ⬜ Not Started |
| E5-S2 | Anti-replay and version monotonicity | Update/install paths reject non-forward versions and replayed payloads | ⬜ Not Started |
| E5-S3 | Boot/runtime tamper and fault behavior policy | Clear secure-fail states and recovery procedures are implemented and tested | ⬜ Not Started |
| E5-S4 | Security verification sweep | Threat-model controls mapped to tests and all high-severity findings remediated | ⬜ Not Started |

### Epic 6 — Verification, CI/CD, and Quality Gates
**Objective:** Establish automated confidence for regressions, performance, and reliability.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E6-S1 | Test strategy across unit/integration/HIL levels | Written test matrix covers runtime, native APIs, OTA, security, and performance | ⬜ Not Started |
| E6-S2 | CI pipeline for compile + validation + artifact checks | CI runs on every change with mandatory pass gates | ⬜ Not Started |
| E6-S3 | Hardware-in-the-loop smoke/regression suite | Automated board-level tests run on schedule and for release candidates | ⬜ Not Started |
| E6-S4 | Performance and power acceptance gates | Measured thresholds (latency, GC pause, sleep current) are tracked and enforced | ⬜ Not Started |

### Epic 7 — Operations, Release, and Support Readiness
**Objective:** Make runtime maintainable for manufacturing and field operations.

| Story ID | Story | DoD | Status |
|---|---|---|---|
| E7-S1 | Release versioning and compatibility policy | Version schema and compatibility guarantees documented and enforced in release checks | ⬜ Not Started |
| E7-S2 | Factory provisioning and bring-up SOP | Repeatable factory checklist exists with pass/fail criteria and trace logs | ⬜ Not Started |
| E7-S3 | Field diagnostics and telemetry conventions | Standard diagnostic counters/events exposed for remote troubleshooting | ⬜ Not Started |
| E7-S4 | Incident response and rollback playbook | On-call/ops runbook for OTA failures, join issues, and brownout scenarios is complete | ⬜ Not Started |

## 5) Program Itinerary and Progress Tracking

### Milestone Itinerary
| Milestone | Exit Criteria | Progress |
|---|---|---|
| M1: Runtime foundation complete | Epic 1 and core parts of Epic 2 green on target hardware | 0% |
| M2: Build + native integration complete | Epic 2 and Epic 3 DoD achieved | 40% |
| M3: OTA + security baseline complete | Epic 4 and Epic 5 DoD achieved | 0% |
| M4: Quality gates and operations complete | Epic 6 and Epic 7 DoD achieved | 0% |
| M5: Production candidate | All epics accepted; release checklist fully signed off | 0% |

### Epic Progress Dashboard
| Epic | Owner | Status | Completion |
|---|---|---|---|
| Epic 1 — Runtime Core | TBD | ⬜ Not Started | 0% |
| Epic 2 — Native Binding/HAL | TBD | ✅ Completed | 100% |
| Epic 3 — ROMizer/Build | TBD | 🟨 In Progress | 25% |
| Epic 4 — OTA | TBD | ⬜ Not Started | 0% |
| Epic 5 — Security | TBD | ⬜ Not Started | 0% |
| Epic 6 — Verification/CI | TBD | ⬜ Not Started | 0% |
| Epic 7 — Operations/Release | TBD | ⬜ Not Started | 0% |

### Update Cadence
- Weekly: Story status updates and blocker review.
- Bi-weekly: Milestone health review and risk burndown.
- Per release candidate: full DoD re-validation and sign-off.

## 6) Cross-Cutting Risks and Mitigations
- **Resource overrun (Flash/SRAM):** enforce build-time memory gates and profile-based optimization.
- **LoRaWAN timing regressions:** keep cooperative `process()` contract and test under worst-case radio conditions.
- **OTA integrity failure:** require pre-activation verification plus rollback.
- **Hardware variability in production:** include board revision matrix in HIL test coverage.

## 7) Definition of Ready (for any Story)
- Clear acceptance criteria linked to architecture constraints.
- Required hardware/tooling dependencies identified.
- Testability path defined (host, integration, or hardware-in-loop).
- Security and failure behavior considered up front.

## 8) Production Readiness Exit Checklist
- [ ] All epic stories satisfy DoD and are accepted.
- [ ] Security controls validated with no unresolved critical/high issues.
- [ ] OTA update and rollback validated in representative network conditions.
- [ ] CI, HIL, and release gates consistently green.
- [ ] Factory and field operational runbooks approved.
- [ ] Final release artifacts versioned, reproducible, and documented.

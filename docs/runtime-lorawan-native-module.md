# Runtime LoRaWAN Native Module (E2-S4)

This document defines the deterministic host-side behavior for
`wioe5.lora.LoRaWAN` natives implemented by `DeterministicLoRaNativeModule`.

## Goals

- Provide deterministic coverage for LoRaWAN init/join/send/process/downlink APIs.
- Keep failure paths explicit with stable return-code semantics.
- Preserve cooperative process-loop behavior, packet-loss handling, and duty-cycle-safe send gating.

## Implemented Native Behaviors

- `init(region)`
  - Accepts only `EU868`, `US915`, `AS923`.
  - Resets runtime state and enters `IDLE`.
- `joinOTAA(devEUI, appEUI, appKey)`
  - Enforces fixed credential lengths (`8/8/16`).
  - Enters `JOINING`; completion occurs through `process()`.
- `joinABP(devAddr, nwkSKey, appSKey)`
  - Enforces fixed credential lengths (`4/16/16`).
  - Immediately transitions to `JOINED`.
- `send(data, len, port, confirmed)`
  - Requires initialized + joined state, valid payload length (`1..242`), and valid LoRaWAN port (`1..223`).
  - Rejects sends during MAC busy or duty-cycle hold windows.
  - Uses deterministic TX-busy ticks (`confirmed` adds one extra processing tick).
- `process()`
  - Advances cooperative LoRa state machine.
  - Resolves join progression, TX completion/retry under packet-loss budgets, and duty-cycle cooldown ticks.
  - Drives `RX_PENDING` status when queued downlinks exist.
- `getStatus()`
  - Returns deterministic status (`IDLE`, `JOINING`, `JOINED`, `TX_BUSY`, `RX_PENDING`).
- `readDownlink(buffer, portOut)`
  - Reads queued downlink payload/port into caller buffers.
  - Updates `getLastRSSI()` and `getLastSNR()` metrics from the consumed downlink.
  - Returns `0` when no downlink is pending.
- `setTxPower(dbm)`
  - Enforces bounded TX power range (`-9..22 dBm`).
- `setADR(enabled)`
  - Stores ADR setting as deterministic module state.
- `getLastRSSI()`, `getLastSNR()`
  - Return last-consumed downlink radio metrics.

## Dispatch Integration

`createDefaultDispatchHandlers()` returns a handler array matching
`VersionedNativeDispatchTable.defaultBindingCount()`.

- Implemented LoRa native indexes: `10..20`
- Non-LoRa indexes intentionally return `ERROR_SYMBOL_NOT_FOUND`.
- Dispatch marshalling uses fixed-capacity handle registries for:
  - byte buffers (`registerDispatchByteBuffer`)
  - int buffers (`registerDispatchIntBuffer`) for `readDownlink` port-out semantics

## Result Codes

- `0`: success
- `-1`: invalid argument
- `-40..-52`: LoRaWAN init/join/state/gating/handle/tx-power validation errors

## Determinism and Safety Notes

- All mutable state is fixed-capacity and array-backed.
- `process()` is the only state-advancement API for join/TX progression.
- Packet loss and duty-cycle constraints are explicit and testable through deterministic budgets.

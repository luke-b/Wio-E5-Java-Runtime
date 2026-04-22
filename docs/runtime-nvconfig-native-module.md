# Deterministic NVConfig Native Module (E2-S5)

`DeterministicNvConfigNativeModule` provides a deterministic host-side implementation of `wioe5.storage.NVConfig` aligned to constrained embedded behavior.

## Goals

- Fixed-capacity, deterministic key/value persistence for keys `0..5`.
- Wear-aware writes via round-robin slot rotation per key.
- Explicit data-integrity checks before values are returned to callers.
- Stable native-dispatch handlers for NVConfig native indexes:
  - `32`: `NVConfig.read(int key, byte[] buffer)`
  - `33`: `NVConfig.write(int key, byte[] data, int len)`

## Storage Model

- `MAX_VALUE_LENGTH` is 64 bytes per key/value entry.
- Each key owns a fixed number of slots (default: 4).
- A slot stores:
  - `generation` (monotonic per key)
  - `length`
  - `payload[64]`
  - `checksum` (CRC16 over key + generation + length + payload bytes)
- Reads choose the highest-generation slot that passes checksum validation.

## Wear-Aware Behavior

- Every successful write advances to the next slot for that key:
  - `targetSlot = (latestSlot + 1) % slotsPerKey`
- Target slot is erased before programming and its erase counter increments.
- Repeated writes distribute erase pressure across all slots for the key.

## Integrity Behavior

- Corrupted or malformed slots are ignored during read selection.
- If no valid slot remains for a key, `read` returns `ERROR_VALUE_NOT_FOUND`.
- Integrity is deterministic and independent of wall clock/hardware timing.

## Error Contract

- `STATUS_OK = 0`
- `ERROR_INVALID_ARGUMENT = -1`
- `ERROR_KEY_OUT_OF_RANGE = -60`
- `ERROR_LENGTH_OUT_OF_RANGE = -61`
- `ERROR_BUFFER_HANDLE_INVALID = -62`
- `ERROR_DISPATCH_STORAGE_FULL = -63`
- `ERROR_VALUE_NOT_FOUND = -64`

## Dispatch Integration

- `createDefaultDispatchHandlers()` returns a full handler array sized to
  `VersionedNativeDispatchTable.defaultBindingCount()`.
- Non-NVConfig symbols resolve to `ERROR_SYMBOL_NOT_FOUND`.
- NVConfig symbols validate argument counts and dispatch handle validity before
  invoking `read`/`write`.

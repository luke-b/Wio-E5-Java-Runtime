# Deterministic NVConfig Native Module

## Purpose

`DeterministicNvConfigNativeModule` is the host-side deterministic implementation of
`wioe5.storage.NVConfig` native behavior. It models a 4 KB Flash sector partitioned into
six fixed key slots (IDs 0–5), each storing up to 64 bytes. The module provides
wear-tracking instrumentation (write counts per key) and supports a configurable write
budget for deterministic test-time enforcement.

---

## Key Constants

| Constant | Value | Meaning |
|---|---|---|
| `KEY_COUNT` | 6 | Number of defined NVConfig keys (0–5) |
| `MAX_VALUE_BYTES` | 64 | Maximum byte length per key value |
| `STATUS_OK` | 0 | Operation succeeded |
| `ERROR_INVALID_ARGUMENT` | -1 | Null pointer or invalid field value |
| `ERROR_KEY_INVALID` | -60 | Key ID is outside `[0, KEY_COUNT)` |
| `ERROR_DATA_TOO_LARGE` | -61 | `len` exceeds `MAX_VALUE_BYTES` |
| `ERROR_WRITE_BUDGET_EXCEEDED` | -62 | Write count for the key reached the configured budget |
| `ERROR_BUFFER_HANDLE_INVALID` | -63 | Dispatch byte-buffer handle does not resolve |
| `ERROR_DISPATCH_STORAGE_FULL` | -64 | Byte-buffer dispatch registry is exhausted |

---

## Operations

### `read(int key, byte[] buffer) → int`

Copies the stored value for `key` into `buffer`. Returns the number of bytes copied.
If the key has never been written, returns 0. If `buffer` is smaller than the stored
value, only `buffer.length` bytes are copied (truncation).

### `write(int key, byte[] data, int len) → int`

Models erase-before-write semantics: all 64 bytes of the key slot are zeroed before
the new `len` bytes from `data` are committed. Returns `STATUS_OK` on success.
Rejects the write if:
- `key` is invalid
- `data` is null, or `len < 0`, or `len > data.length`
- `len > MAX_VALUE_BYTES`
- the write count for the key equals or exceeds the configured write budget

---

## Wear Tracking

Each call to `write()` that succeeds increments a per-key write counter. The counter
is observable via `writeCountForKey(int key)`. A test-injectable write budget is set
via `setWriteBudgetPerKeyForTest(int budget)`. When the budget is set and the write
count for a key reaches it, subsequent writes return `ERROR_WRITE_BUDGET_EXCEEDED`.

This models the Flash wear-limit behavior documented in the architecture for the 4 KB
Configuration Sector. The default budget is effectively unlimited (`Integer.MAX_VALUE`).

---

## Native Dispatch Integration

Native indexes 32 and 33 correspond to `NVConfig.read` and `NVConfig.write` in
`VersionedNativeDispatchTable`. `createDefaultDispatchHandlers()` returns a full
handler array with:
- **Index 32** → `dispatchRead(int[] args)`: `args = [key, bufferHandle]`
- **Index 33** → `dispatchWrite(int[] args)`: `args = [key, dataHandle, len]`

All other handler slots return `ERROR_SYMBOL_NOT_FOUND` as placeholders.

### Dispatch Buffer Registry

Buffer arguments in dispatch calls are passed as 1-based handles obtained via
`registerDispatchByteBuffer(byte[] data)`. After a `read` dispatch, the caller
recovers the written bytes using `copyDispatchByteBuffer(int handle)`.

---

## Test-Seam Accessors

| Method | Purpose |
|---|---|
| `writeCountForKey(int key)` | Returns the number of successful writes to `key` |
| `storedLengthForKey(int key)` | Returns the current stored byte count, or `-1` if unwritten |
| `setWriteBudgetPerKeyForTest(int budget)` | Sets a write limit across all keys for deterministic wear tests |
| `registerDispatchByteBuffer(byte[] data)` | Register a buffer for dispatch argument marshalling |
| `copyDispatchByteBuffer(int handle)` | Read back the contents of a registered buffer |

---

## Architecture Constraints Preserved

- **Static allocation**: key store (`byte[6][64]`), write-count array, and dispatch registry are all
  pre-allocated at construction time with no runtime growth.
- **Deterministic failure codes**: every failure path returns a named negative constant.
- **Erase-before-write model**: zeroing the 64-byte slot before writing is the correct Flash sector
  behavior; partial old bytes are never observable after a successful write.
- **Wear budget**: the write count per key is bounded and observable, enabling deterministic
  enforcement of wear-limit contracts without non-deterministic hardware counters.

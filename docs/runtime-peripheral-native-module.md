# Runtime Peripheral Native Module (E2-S3)

This document defines the deterministic host-side behavior for
`wioe5.io.GPIO`, `wioe5.bus.I2C`, and `wioe5.bus.UART` natives implemented by
`DeterministicPeripheralNativeModule`.

## Goals

- Provide deterministic native behavior for GPIO, I2C, and UART APIs.
- Keep API failures explicit and standardized with return-code semantics.
- Preserve static/runtime-safety constraints: fixed capacities, no dynamic
  resizing in hot paths, and bounded behavior.

## Implemented Native Behaviors

### GPIO

- `pinMode(pin, mode)` validates known pin and mode IDs.
- `digitalWrite(pin, value)` requires `OUTPUT` mode and `LOW/HIGH` values.
- `digitalRead(pin)` rejects reads from `ANALOG` mode.
- `analogRead(pin)` requires `ANALOG` mode and returns deterministic `0..4095`.
- Optional deterministic GPIO loopback allows output pin transitions to drive
  an input pin in tests.

### I2C

- `begin(speedKhz)` supports only `100` or `400`.
- `write(address, data, len)` and `read(address, buffer, len)` validate init,
  address range (`0x01..0x7F`), and bounded lengths.
- `writeRead(...)` performs deterministic register-pointer write + read.
- `end()` transitions bus to explicit not-initialized state.
- Test-time device registration simulates sensor/register behavior.

### UART

- `begin(uart, baud)` supports UART1/UART2 and bounded baud range.
- `available(uart)`, `read(uart)`, `write(uart, data, len)` enforce init and
  fixed-buffer capacity.
- `read(uart)` returns `-1` when RX is empty per API contract.
- `print(uart, s)` and `println(uart, s)` encode ASCII payloads deterministically.
- Optional UART loopback routes TX of one UART into RX of the peer UART.

## Dispatch Integration

`createDefaultDispatchHandlers()` returns a full handler array compatible with
`VersionedNativeDispatchTable.defaultBindingCount()`.

- Implemented native indexes:
  - GPIO: `6..9`
  - I2C: `21..25`
  - UART: `26..31`
- Non-peripheral entries intentionally return `ERROR_SYMBOL_NOT_FOUND`.
- Dispatch helper registries provide deterministic handle-based storage for
  byte buffers and strings used by marshalled dispatch argument arrays.

## Standardized Result Codes

- `0`: success
- `-1`: invalid argument (and UART empty-read sentinel for `read(uart)`)
- `-10..-14`: GPIO validation/mode/value errors
- `-20..-28`: I2C/dispatch buffer and storage errors
- `-30..-34`: UART ID/init/baud/buffer overflow errors

## Determinism and Safety Notes

- All mutable runtime state uses fixed-size arrays.
- No unbounded data structures are used in call paths.
- Error handling is explicit and bounded for negative and boundary inputs.

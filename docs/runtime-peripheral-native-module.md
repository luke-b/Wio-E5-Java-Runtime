# Runtime Peripheral Native Module (E2-S3)

This document defines the deterministic host-side behavior for
`wioe5.io.GPIO`, `wioe5.bus.I2C`, and `wioe5.bus.UART` natives implemented by
`DeterministicPeripheralNativeModule`.

## Goals

- Provide deterministic native behavior for GPIO, I2C, and UART APIs.
- Keep API failures explicit and standardized with return-code semantics.
- Preserve static/runtime-safety constraints: fixed capacities, no dynamic
  resizing in hot paths, and bounded behavior.

---

## Architecture Pin Mapping (GPIO)

| Constant | Pin Index | Physical Pin | Notes                  |
|----------|-----------|--------------|------------------------|
| `LED`    | 0         | PB5          | User LED               |
| `D0`     | 1         | PA0          | Grove header / Analog  |
| `D1`     | 2         | PA1          | Grove header / Analog  |
| `D2`     | 3         | PA2          | Grove header / Analog  |
| `D3`     | 4         | PA3          | Grove header / Analog  |
| `D4`     | 5         | PA4          | Grove header / Analog  |
| `D5`     | 6         | PA5          | Grove header / Analog  |

Valid pin index range: `0..6`. Any value outside this range returns
`ERROR_PIN_OUT_OF_RANGE`.

---

## Implemented Native Behaviors

### GPIO

#### Mode Constants

| Constant         | Value | Permitted Operations                                    |
|------------------|-------|---------------------------------------------------------|
| `INPUT`          | 0     | `digitalRead`                                           |
| `OUTPUT`         | 1     | `digitalWrite`, `digitalRead` (read-back of driven level) |
| `INPUT_PULLUP`   | 2     | `digitalRead`                                           |
| `INPUT_PULLDOWN` | 3     | `digitalRead`                                           |
| `ANALOG`         | 4     | `analogRead`                                            |

Valid mode range: `0..4`. Any value outside this range returns
`ERROR_PIN_MODE_OUT_OF_RANGE`.

**Behavioral contract:**

- `digitalRead` on an `OUTPUT` pin returns the current driven level (read-back).
  This is the correct hardware behaviour and does **not** return
  `ERROR_PIN_MODE_MISMATCH`.
- `digitalRead` on an `ANALOG` pin returns `ERROR_PIN_MODE_MISMATCH`.
- `analogRead` on **any** non-`ANALOG` mode returns `ERROR_PIN_MODE_MISMATCH`
  (includes `INPUT`, `OUTPUT`, `INPUT_PULLUP`, `INPUT_PULLDOWN`).
- `digitalWrite` on any mode other than `OUTPUT` returns `ERROR_PIN_MODE_MISMATCH`.
- The digital value argument must be `0` (LOW) or `1` (HIGH). Any other value
  returns `ERROR_DIGITAL_VALUE_OUT_OF_RANGE`.

#### Analog Input

- `analogRead` returns 12-bit ADC values in range `0..4095`.
- The analog level is set deterministically in tests via `setAnalogInputForTest`.
  Default is `0`.
- `setAnalogInputForTest` values outside `0..4095` return
  `ERROR_ANALOG_VALUE_OUT_OF_RANGE`.

#### GPIO Loopback (Test Seam)

- `configureGpioLoopback(outputPin, inputPin)` routes every `digitalWrite`
  transition on `outputPin` to the digital level of `inputPin`.
- Used for host-side deterministic regression testing; does not model electrical
  characteristics.

---

### I2C

Uses I2C1 (PB6 = SCL, PB7 = SDA).

#### Init Lifecycle

| State            | Allowed                          | Rejected (error code)                     |
|------------------|----------------------------------|-------------------------------------------|
| Not initialized  | `begin`                          | `write`, `read`, `writeRead`, `end` → `ERROR_I2C_NOT_INITIALIZED` |
| Initialized      | `write`, `read`, `writeRead`, `end` | `begin` → `ERROR_I2C_ALREADY_INITIALIZED` |

#### Speed Whitelist

Only `100 kHz` and `400 kHz` are accepted. Any other value returns
`ERROR_I2C_SPEED_UNSUPPORTED`.

#### Address Bounds

7-bit addresses `0x01..0x7F`. Address `0x00` (general call) and addresses
`0x80`+ return `ERROR_I2C_ADDRESS_OUT_OF_RANGE`.

#### Register Pointer Semantics

I2C register access follows standard 7-bit register-address protocol:

| `write` `len` | Behavior                                                           |
|---------------|--------------------------------------------------------------------|
| 0             | No-op. Register pointer **not** updated. Returns `STATUS_OK` (0). |
| 1             | Sets register pointer to `data[0]`. Writes no data. Returns 1.    |
| ≥ 2           | Sets pointer to `data[0]`, writes `data[1..len-1]` starting at that register, advances pointer past the last written register. Returns `len`. |

- `read` reads `len` bytes sequentially from the current register pointer and
  advances the pointer by `len`. Returns `len` on success.
- `read` with `len == 0` is a no-op; returns 0.
- Register address arithmetic wraps modulo the device memory size (cyclic
  register space).
- `writeRead` performs write then read atomically (combined write-then-read
  without STOP, used for sensor register reads). If the write returns a negative
  error code, no read is attempted and the write error code is returned directly.

#### Length Validation

`len` must satisfy `0 ≤ len ≤ buffer.length`. Values outside this range return
`ERROR_LENGTH_OUT_OF_RANGE`.

---

### UART

Uses UART1 (PA9/PA10) and UART2 (PA2/PA3 debug header).

#### Baud Rate Bounds

Valid range: `1200..921600` inclusive. Values outside this range return
`ERROR_UART_BAUD_OUT_OF_RANGE`. Boundary values `1200` and `921600` are valid.

#### Init Lifecycle

| State            | Allowed                                        | Rejected (error code)                      |
|------------------|------------------------------------------------|--------------------------------------------|
| Not initialized  | `begin`                                        | `available`, `read`, `write`, `print`, `println` → `ERROR_UART_NOT_INITIALIZED` |
| Initialized      | `available`, `read`, `write`, `print`, `println` | `begin` → `ERROR_UART_ALREADY_INITIALIZED` |

#### Empty Read Sentinel

`read(uart)` returns `UART_READ_EMPTY` (`-1`) when the RX buffer is empty.
This is the only negative return from a successful `read` call.

#### ASCII Constraint

`print` and `println` accept only strings where every character has code point
`0x00..0x7F`. Any character with code point `> 0x7F` causes the method to
return `ERROR_INVALID_ARGUMENT`. A `null` string argument also returns
`ERROR_INVALID_ARGUMENT`.

#### Buffer Overflow

`write` returns `ERROR_UART_TX_OVERFLOW` if:
- `len > bufferCapacity` (write exceeds the fixed buffer size), or
- the loopback peer's RX buffer has insufficient free space.

`write` with `len == 0` is a no-op returning `0`.

#### UART Loopback (Test Seam)

- `configureUartLoopback(sourceUart, targetUart)` routes TX of `sourceUart`
  into the RX buffer of `targetUart`.
- `injectUartRxForTest(uart, data)` directly enqueues bytes into the RX buffer
  of the specified UART.
- Both are test-time utilities and do not model electrical signal characteristics.

---

## Dispatch Integration

`createDefaultDispatchHandlers()` returns a full handler array compatible with
`VersionedNativeDispatchTable.defaultBindingCount()`.

### Dispatch Index Table

| Native Index | Class | Method      | Argument Layout                                    |
|--------------|-------|-------------|---------------------------------------------------|
| 6            | GPIO  | `pinMode`   | `[pin, mode]`                                     |
| 7            | GPIO  | `digitalWrite` | `[pin, value]`                                 |
| 8            | GPIO  | `digitalRead`  | `[pin]`                                        |
| 9            | GPIO  | `analogRead`   | `[pin]`                                        |
| 21           | I2C   | `begin`     | `[speedKhz]`                                      |
| 22           | I2C   | `write`     | `[address, bufHandle, len]`                       |
| 23           | I2C   | `read`      | `[address, bufHandle, len]`                       |
| 24           | I2C   | `writeRead` | `[address, txHandle, txLen, rxHandle, rxLen]`     |
| 25           | I2C   | `end`       | `[]`                                              |
| 26           | UART  | `begin`     | `[uart, baud]`                                    |
| 27           | UART  | `available` | `[uart]`                                          |
| 28           | UART  | `read`      | `[uart]`                                          |
| 29           | UART  | `write`     | `[uart, bufHandle, len]`                          |
| 30           | UART  | `print`     | `[uart, strHandle]`                               |
| 31           | UART  | `println`   | `[uart, strHandle]`                               |

- Indexes `0..5` (Power), `10..20` (LoRaWAN), and `32..33` (NVConfig) are not
  handled by this module; those entries return `ERROR_SYMBOL_NOT_FOUND`.
- `bufHandle` and `strHandle` are 1-based integer identifiers registered via
  `registerDispatchByteBuffer` and `registerDispatchString` respectively.
  Handle `0` or any out-of-range handle returns `ERROR_BUFFER_HANDLE_INVALID`
  or `ERROR_STRING_HANDLE_INVALID`.
- Argument array length is validated before dispatch; wrong-length arrays return
  `ERROR_INVALID_ARGUMENT`.
- Buffer and string registries are **separate** and handle numbers within each
  do not collide across registry types.

---

## Standardized Result Code Matrix

| Constant                          | Value | Meaning |
|-----------------------------------|-------|---------|
| `STATUS_OK`                       | 0     | Success |
| `ERROR_INVALID_ARGUMENT`          | -1    | Null or structurally invalid argument; also alias for `UART_READ_EMPTY` |
| `ERROR_PIN_OUT_OF_RANGE`          | -10   | Pin index outside `0..6` |
| `ERROR_PIN_MODE_OUT_OF_RANGE`     | -11   | Mode value outside `0..4` |
| `ERROR_PIN_MODE_MISMATCH`         | -12   | Operation not permitted in the current pin mode |
| `ERROR_DIGITAL_VALUE_OUT_OF_RANGE`| -13   | Digital value not `0` or `1` |
| `ERROR_ANALOG_VALUE_OUT_OF_RANGE` | -14   | Analog value outside `0..4095` |
| `ERROR_I2C_NOT_INITIALIZED`       | -20   | I2C operation before `begin` |
| `ERROR_I2C_ALREADY_INITIALIZED`   | -21   | `begin` called when already initialized |
| `ERROR_I2C_SPEED_UNSUPPORTED`     | -22   | Speed not `100` or `400` kHz |
| `ERROR_I2C_ADDRESS_OUT_OF_RANGE`  | -23   | 7-bit address outside `0x01..0x7F` |
| `ERROR_I2C_DEVICE_NOT_FOUND`      | -24   | No device registered at the given address |
| `ERROR_LENGTH_OUT_OF_RANGE`       | -25   | `len < 0` or `len > buffer.length` |
| `ERROR_BUFFER_HANDLE_INVALID`     | -26   | Dispatch byte-buffer handle not registered |
| `ERROR_STRING_HANDLE_INVALID`     | -27   | Dispatch string handle not registered |
| `ERROR_DISPATCH_STORAGE_FULL`     | -28   | No free slots in dispatch buffer/string registry |
| `ERROR_UART_OUT_OF_RANGE`         | -30   | UART ID not `1` or `2` |
| `ERROR_UART_NOT_INITIALIZED`      | -31   | UART operation before `begin` |
| `ERROR_UART_ALREADY_INITIALIZED`  | -32   | `begin` called when already initialized |
| `ERROR_UART_BAUD_OUT_OF_RANGE`    | -33   | Baud rate outside `1200..921600` |
| `ERROR_UART_TX_OVERFLOW`          | -34   | TX write exceeds buffer or loopback peer RX capacity |
| `UART_READ_EMPTY`                 | -1    | Alias: returned by `read(uart)` when RX buffer is empty |

---

## Determinism and Safety Notes

- All mutable runtime state uses fixed-size pre-allocated arrays.
- No unbounded data structures are used in call paths.
- Error handling is explicit and bounded for all negative and boundary inputs.
- Dispatch buffer and string registries are bounded by constructor-specified
  capacities (`maxDispatchByteBuffers`, `maxDispatchStrings`).
- GPIO loopback and UART loopback are test-time seams and do not model electrical
  characteristics of the physical hardware.
- Register address arithmetic in I2C wraps modulo device memory size; this is
  the correct behavior for cyclic-register-address sensor devices.

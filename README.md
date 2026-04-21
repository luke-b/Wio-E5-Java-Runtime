# Wio-E5 Java Runtime

Reference API surface and project bootstrap for a romized Java runtime on the Seeed Studio Wio-E5 (STM32WLE5JC).

## Embedded Java API surface

This repository now contains Java package stubs matching the technical white paper design:

- `wioe5.system.Power`
- `wioe5.io.GPIO`
- `wioe5.lora.LoRaWAN`
- `wioe5.bus.I2C`
- `wioe5.bus.UART`
- `wioe5.storage.NVConfig`

These classes use `static native` methods and constants so that the C runtime can provide the final hardware implementations.

# Architecture & Design Decisions

## 1. Packet Header Size (22 bytes)
**Decision**: Fixed header size of 22 bytes.
**Fields**: Type (1) + Flags (1) + Sequence (4) + ACK (4) + Payload Length (2) + Checksum (2) + Timestamp (8) = 22 bytes.
**Reasoning**: A fixed header avoids the complexity and overhead of delimiter parsing. The timestamp field was added to support accurate RTO calculations.

## 2. Endianness (BIG_ENDIAN)
**Decision**: Force `ByteOrder.BIG_ENDIAN` in `PacketSerializer`.
**Reasoning**: Ensures cross-platform compatibility (network byte order). If a sender is on Windows (often little-endian) and receiver on Linux, the packet structure remains consistent.

## 3. Checksum Algorithm (RFC 1071)
**Decision**: Use the 16-bit Internet Checksum.
**Reasoning**: Standard, fast, and meets the project proposal requirements. Catches all single-bit errors.

## 4. UDP Receiver Threading
**Decision**: `UDPReceiver` runs on a dedicated daemon thread and feeds a `LinkedBlockingQueue`.
**Reasoning**: Prevents the application from dropping packets while busy processing a previous packet (Risk 1 from proposal).

## 5. Deterministic Channel Emulator
**Decision**: Use a seeded `java.util.Random` for all impairments (loss, corruption, delay, duplicate).
**Reasoning**: Crucial for the final experiments. We must test all three ARQ protocols under the *exact same* channel conditions to fairly compare their goodput.

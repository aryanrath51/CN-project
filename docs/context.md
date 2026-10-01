# Project Context: Reliable Data Transfer over UDP

**Project:** CN-project (P3) - CS-30003
**Team:**
- Aryan Rath (Selective Repeat & RTO)
- Priyanshi Goyal (App, Hash Verification, Benchmarks)
- Tanishqa Jaiswal (Go-Back-N & State Machine)
- Raunak Agarwal (Channel Emulator, Packet Framing, Checksums, UDP Transport)

## Goal
Build a robust file transfer application over reliable UDP sockets from scratch. The system implements three ARQ protocols (Stop-and-Wait, Go-Back-N, Selective Repeat) and compares their performance under various network conditions (loss, corruption, delay) using a custom deterministic channel emulator.

## Core Architecture
1.  **Application Layer (`app/`)**: CLI interface, reads/writes files, calculates SHA-256 hashes for integrity verification.
2.  **ARQ Protocol Layer (`arq/`)**: Implements `ARQProtocol`. Handles fragmentation, reassembly, ACKs/NAKs, and retransmissions.
3.  **RTO Engine (`rto/`)**: Jacobson/Karn's algorithm for dynamic timeout estimation.
4.  **Channel Emulator (`channel/`)**: Runs as a proxy between sender and receiver. Applies deterministic impairments based on a seed.
5.  **Transport Layer (`transport/`)**: Raw UDP socket wrappers (`UDPSender`, `UDPReceiver`) for sending/receiving byte arrays.
6.  **Packet Layer (`packet/`)**: Fixed 22-byte header + payload framing and serialization.
7.  **Checksum (`checksum/`)**: RFC 1071 16-bit Internet checksum for corruption detection.

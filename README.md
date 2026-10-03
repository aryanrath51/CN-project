# CN-project — Reliable Data Transfer over UDP (P3)

**CS-30003 | Coding Assignment 1 | Kalinga Institute of Industrial Technology (KIIT)**

## Team

| Member | Roll | Responsibility |
|---|---|---|
| Aryan Rath | 24155318 | Selective Repeat ARQ & Adaptive RTO Engine |
| Priyanshi Goyal | 24155341 | File Transfer App, Hash Verification, Benchmarks & Plotting |
| Tanishqa Jaiswal | 24052042 | Go-Back-N ARQ & State Machine Management |
| Raunak Agarwal | 24155644 | Channel Emulator, Packet Framing/Serialization, Checksums & UDP Transport Layer |

## What We're Building

A robust file transfer application over reliable UDP sockets from scratch, bypassing high-level transport libraries. The system features three pluggable ARQ transport protocols — **Stop-and-Wait**, **Go-Back-N**, and **Selective Repeat** — backed by a custom deterministic channel emulator and an adaptive Retransmission Timeout (RTO) estimation engine.

## Project Structure

```
CN-project/
├── src/
│   ├── packet/          # Packet framing & serialization
│   ├── checksum/        # RFC 1071 Internet checksum
│   ├── transport/       # UDP send/receive layer
│   ├── channel/         # Deterministic channel emulator
│   ├── arq/             # ARQ protocol interface + implementations
│   ├── rto/             # Jacobson/Karn's RTO estimator
│   └── app/             # File transfer CLI application
├── tests/               # Unit tests
├── experiments/         # Python experiment runner & plotting
└── docs/                # Proposal & documentation
```

## Building & Running

```bash
# Compile all sources
javac -d out -sourcepath src src/**/*.java

# Run tests
javac -d out -sourcepath src:tests tests/*.java
java -cp out tests.PacketTest
java -cp out tests.ChecksumTest
java -cp out tests.UDPTransportTest
java -cp out tests.ChannelEmulatorTest

# Run channel emulator (standalone)
java -cp out channel.ChannelEmulatorMain 9000 localhost 9001 0.1 0.01 0.0 10 42
```

## Stack

- **Java** — Core protocol implementation (DatagramSocket, concurrency)
- **Python** — Experiment runner, benchmarking, Matplotlib/pandas plotting

## Claim Under Test

> Selective Repeat will maintain significantly higher **goodput** than Go-Back-N and Stop-and-Wait as channel packet loss rates exceed 10%.

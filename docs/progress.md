# Project Progress

## ✅ Completed (Raunak's Tasks - Week 1)
- **Project Structure**: Setup, `.gitignore`, `README.md`.
- **Packet Layer**: `Packet`, `PacketType`, `PacketSerializer`, `CorruptPacketException`.
- **Checksum Engine**: `ChecksumUtil` (RFC 1071).
- **Transport Layer**: `UDPSender`, `UDPReceiver`, `TransportConfig`.
- **Channel Emulator**: `ChannelEmulator`, `ChannelConfig`, `ChannelEmulatorMain`.
- **Integration Interfaces**: `ARQProtocol`, `ProtocolStats`.
- **Testing**: 32 test cases passing across all 4 core components.
- **Environment**: Fixed Java compilation issues on Windows (encoding, javadoc globs).

## ⏳ Pending / Work Left

### Raunak (Integration & Handoff)
- [ ] Push codebase to GitHub (`aryanrath51/CN-project`). *Blocked: Git not installed locally.*
- [ ] Write a small `IntegrationDemo.java` (Optional, but good for proving the transport layer).
- [ ] Handoff `ARQProtocol` interface to teammates.

### Tanishqa & Priyanshi (Week 3-4)
- [ ] Implement `StopAndWait` ARQ.
- [ ] Implement `GoBackN` ARQ.
- [ ] Build the base File Transfer CLI app (`app/`).

### Aryan (Week 5-6)
- [ ] Implement `SelectiveRepeat` ARQ.
- [ ] Implement Adaptive RTO Engine (`rto/`).

### Team (Week 7-8)
- [ ] Run experiments using `ChannelEmulator` varying loss/corruption rates.
- [ ] Collect data using `ProtocolStats`.
- [ ] Plot graphs (Python) to prove the claim: *Selective Repeat > Go-Back-N > Stop-and-Wait at >10% loss.*
- [ ] Final Report / Presentation.

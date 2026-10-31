# AI Usage Disclosure (AI-USE.md)

Project: P3 · Reliable Data Transfer over UDP    

In accordance with project guidelines, AI tools were utilized strictly for permitted support activities. Below is the full disclosure of how AI assistance was applied across the project modules:

### 1. Core Architecture & ARQ Protocols (Stop-and-Wait, Go-Back-N, Selective Repeat)
Permitted Assistance: Conceptual clarification of state machine transitions, handling packet checksum verification boilerplate, and debugging `java.net.DatagramSocket` concurrency edge cases.
Scope: Core sliding-window boundaries and packet delivery logic were developed and verified by the development team.

### 2. Channel Emulator Module
Permitted Assistance: Structuring the pseudorandom number generator (PRNG) with seed-based configuration for reproducibility, and reviewing syntax for packet loss, duplication, reordering, and delay-with-jitter injection logic.

### 3. Adaptive RTO & Timer Management
Permitted Assistance: Mathematical formula review for Jacobson/Karels RTT estimation ($SRTT \leftarrow (1 - \alpha) \cdot SRTT + \alpha \cdot RTT$) and Karn's algorithm implementation to exclude retransmitted samples.
Scope: Integration of concurrent per-packet timestamps and timeout handling within the sender architecture.

### 4. Experimental Benchmarking Suite
Permitted Assistance: Generating boilerplate script structures for `matplotlib` to plot Goodput vs. Loss, Goodput vs. Window Size, Retransmissions vs. Reordering, and RTO Sensitivity curves.

### 5. Advanced Stretch Goals
Permitted Assistance: 
  * Connection Setup/Teardown:* Syntax review for TCP-style control state tracking (`SYN`, `SYN-ACK`, `ACK`, `FIN`).
  * Flow Control: Structuring receiver window (`rwnd`) buffer tracking.
  * SACK Blocks: Assistance with regex and string parsing algorithms for non-contiguous buffer range reports (`[start-end]`).
  * Nagle-Style Coalescing:* Buffering logic for Maximum Segment Size (MSS) threshold chunking.

### 6. Testing & Verification
Permitted Assistance: Scaffolding unit and integration test boilerplate, specifically for intentional packet/ACK loss recovery failure-injection test suites.
package channel;

import packet.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic Channel Emulator — the core testing infrastructure.
 *
 * Operates as a relay proxy between sender and receiver, selectively applying
 * network impairments: packet loss, duplication, corruption, reordering, and jitter.
 *
 * Architecture:
 * ┌──────────┐     ┌────────────────────┐     ┌──────────────┐
 * │  Sender  │────►│  ChannelEmulator   │────►│   Receiver   │
 * │          │     │  (listenPort)      │     │ (dest:port)  │
 * │          │◄────│  Bidirectional     │◄────│              │
 * └──────────┘     └────────────────────┘     └──────────────┘
 *
 * Determinism:
 * - Uses a seeded java.util.Random so the same seed produces the same
 *   impairment pattern across runs. This enables fair comparison of
 *   Stop-and-Wait vs Go-Back-N vs Selective Repeat under identical conditions.
 *
 * Bidirectional:
 * - The emulator remembers the first sender's address and relays ACKs
 *   from the receiver back through the channel (also with impairments).
 */
public class ChannelEmulator implements Runnable {

    private final DatagramSocket socket;          // Single socket for both directions
    private final ChannelConfig config;
    private final Random random;
    private volatile boolean running = true;

    // ── Destination (receiver) ───────────────────────────────────

    private final InetAddress destAddress;
    private final int destPort;

    // ── Source (sender, learned from first packet) ───────────────

    private volatile InetAddress srcAddress;
    private volatile int srcPort = -1;

    // ── Delay queue for jitter/reordering ────────────────────────

    private final PriorityBlockingQueue<DelayedPacket> delayQueue;
    private final ScheduledExecutorService scheduler;

    // ── Statistics (thread-safe) ─────────────────────────────────

    private final AtomicLong totalPackets = new AtomicLong(0);
    private final AtomicLong droppedPackets = new AtomicLong(0);
    private final AtomicLong duplicatedPackets = new AtomicLong(0);
    private final AtomicLong corruptedPackets = new AtomicLong(0);
    private final AtomicLong reorderedPackets = new AtomicLong(0);
    private final AtomicLong forwardedPackets = new AtomicLong(0);

    // ── Constructor ──────────────────────────────────────────────

    /**
     * Create a ChannelEmulator.
     *
     * @param config     the impairment configuration
     * @param listenPort the port to listen on (both sender and receiver connect here)
     * @param destHost   the receiver's hostname/IP
     * @param destPort   the receiver's port
     * @throws Exception if socket creation or DNS resolution fails
     */
    public ChannelEmulator(ChannelConfig config, int listenPort,
                           String destHost, int destPort) throws Exception {
        this.config = config;
        this.random = new Random(config.getRandomSeed());
        this.socket = new DatagramSocket(listenPort);
        this.destAddress = InetAddress.getByName(destHost);
        this.destPort = destPort;
        this.delayQueue = new PriorityBlockingQueue<>();
        this.scheduler = Executors.newScheduledThreadPool(1);

        System.out.printf("[Channel] Emulator created on port %d → %s:%d%n",
                listenPort, destHost, destPort);
        System.out.printf("[Channel] Config: %s%n", config);
    }

    // ── Main receive loop ────────────────────────────────────────

    @Override
    public void run() {
        // Start the delayed-packet forwarder in a separate thread
        scheduler.submit(this::forwardDelayedPackets);

        byte[] buffer = new byte[Packet.HEADER_SIZE + Packet.MAX_PAYLOAD_SIZE + 64];

        while (running) {
            try {
                DatagramPacket dgPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(dgPacket);

                long count = totalPackets.incrementAndGet();

                // Copy received data
                byte[] data = new byte[dgPacket.getLength()];
                System.arraycopy(dgPacket.getData(), dgPacket.getOffset(),
                        data, 0, dgPacket.getLength());

                InetAddress pktSrcAddr = dgPacket.getAddress();
                int pktSrcPort = dgPacket.getPort();

                // Determine forwarding direction:
                // - If packet came from the receiver (destAddress:destPort), forward to sender
                // - Otherwise, it came from the sender — forward to receiver
                InetAddress fwdAddr;
                int fwdPort;

                if (pktSrcAddr.equals(destAddress) && pktSrcPort == destPort) {
                    // ACK path: receiver → sender
                    if (srcAddress == null) {
                        // Haven't seen the sender yet — drop
                        System.out.println("[Channel] Dropping ACK: sender unknown yet");
                        continue;
                    }
                    fwdAddr = srcAddress;
                    fwdPort = srcPort;
                } else {
                    // Data path: sender → receiver
                    // Learn sender address from first packet
                    if (srcAddress == null) {
                        srcAddress = pktSrcAddr;
                        srcPort = pktSrcPort;
                        System.out.printf("[Channel] Learned sender: %s:%d%n",
                                srcAddress.getHostAddress(), srcPort);
                    }
                    fwdAddr = destAddress;
                    fwdPort = destPort;
                }

                processPacket(data, fwdAddr, fwdPort, count);

            } catch (SocketException e) {
                if (running) {
                    System.err.println("[Channel] Socket error: " + e.getMessage());
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[Channel] Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // ── Impairment processing ────────────────────────────────────

    private void processPacket(byte[] data, InetAddress fwdAddr, int fwdPort, long packetNum) {
        // 1. LOSS — drop the packet entirely
        if (random.nextDouble() < config.getLossRate()) {
            droppedPackets.incrementAndGet();
            log("DROPPED packet #" + packetNum);
            return;
        }

        // 2. CORRUPTION — flip random bits in the payload
        if (random.nextDouble() < config.getCorruptionRate()) {
            data = corruptData(data);
            corruptedPackets.incrementAndGet();
            log("CORRUPTED packet #" + packetNum);
        }

        // 3. Calculate delay (base propagation + jitter)
        int delay = config.getMinDelayMs();
        if (config.getMaxDelayMs() > config.getMinDelayMs()) {
            delay += random.nextInt(config.getMaxDelayMs() - config.getMinDelayMs());
        }

        // 4. REORDERING — add extra random delay to push this packet behind later ones
        if (random.nextDouble() < config.getReorderRate()) {
            delay += random.nextInt(100) + 50; // Extra 50–150ms
            reorderedPackets.incrementAndGet();
            log("REORDERED packet #" + packetNum + " (extra delay +" + delay + "ms)");
        }

        // 5. Schedule forwarding
        long deliveryTime = System.currentTimeMillis() + delay;
        delayQueue.put(new DelayedPacket(data, fwdAddr, fwdPort, deliveryTime));

        // 6. DUPLICATION — schedule a second copy with slightly different delay
        if (random.nextDouble() < config.getDuplicateRate()) {
            int dupDelay = delay + random.nextInt(50) + 5; // Slightly after original
            long dupDeliveryTime = System.currentTimeMillis() + dupDelay;
            delayQueue.put(new DelayedPacket(data.clone(), fwdAddr, fwdPort, dupDeliveryTime));
            duplicatedPackets.incrementAndGet();
            log("DUPLICATED packet #" + packetNum);
        }
    }

    // ── Delayed forwarder ────────────────────────────────────────

    /**
     * Continuously polls the delay queue and forwards packets whose
     * delivery time has arrived.
     */
    private void forwardDelayedPackets() {
        while (running) {
            try {
                DelayedPacket dp = delayQueue.peek();
                if (dp != null && System.currentTimeMillis() >= dp.deliveryTime) {
                    dp = delayQueue.poll();
                    if (dp != null) {
                        DatagramPacket dgp = new DatagramPacket(
                                dp.data, dp.data.length, dp.address, dp.port
                        );
                        socket.send(dgp);
                        forwardedPackets.incrementAndGet();
                    }
                } else {
                    // Sleep briefly to avoid busy-wait (1ms granularity is fine for our use)
                    Thread.sleep(1);
                }
            } catch (SocketException e) {
                if (running) {
                    System.err.println("[Channel] Forward socket error: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Channel] Forward IO error: " + e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Corruption logic ─────────────────────────────────────────

    /**
     * Flip 1–3 random bits in the packet payload area.
     * We corrupt the payload (not the header) so the packet remains structurally
     * parseable but fails the checksum verification.
     */
    private byte[] corruptData(byte[] data) {
        byte[] corrupted = data.clone();
        int payloadStart = Packet.HEADER_SIZE;

        if (corrupted.length <= payloadStart) {
            // Control packet with no payload — corrupt a header byte instead
            // (but not the type byte, to keep it parseable)
            int idx = 1 + random.nextInt(corrupted.length - 1);
            corrupted[idx] ^= (1 << random.nextInt(8));
            return corrupted;
        }

        int payloadSize = corrupted.length - payloadStart;
        int numFlips = random.nextInt(3) + 1;

        for (int i = 0; i < numFlips; i++) {
            int byteIdx = payloadStart + random.nextInt(payloadSize);
            int bitIdx = random.nextInt(8);
            corrupted[byteIdx] ^= (1 << bitIdx);
        }

        return corrupted;
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    /**
     * Stop the emulator and release resources.
     */
    public void stop() {
        running = false;
        socket.close();
        scheduler.shutdownNow();
        System.out.println("[Channel] Emulator stopped.");
        System.out.println(getStatistics());
    }

    // ── Statistics ────────────────────────────────────────────────

    /**
     * @return a formatted summary of all impairment statistics
     */
    public String getStatistics() {
        long total = totalPackets.get();
        long dropped = droppedPackets.get();
        long corrupted = corruptedPackets.get();
        long duplicated = duplicatedPackets.get();
        long reordered = reorderedPackets.get();
        long forwarded = forwardedPackets.get();

        return String.format(
                "[Channel Stats] total=%d, forwarded=%d, dropped=%d (%.1f%%), " +
                "corrupted=%d (%.1f%%), duplicated=%d (%.1f%%), reordered=%d (%.1f%%)",
                total, forwarded,
                dropped, pct(dropped, total),
                corrupted, pct(corrupted, total),
                duplicated, pct(duplicated, total),
                reordered, pct(reordered, total));
    }

    /** @return a CSV-friendly stats line: total,forwarded,dropped,corrupted,duplicated,reordered */
    public String getStatsCsv() {
        return String.format("%d,%d,%d,%d,%d,%d",
                totalPackets.get(), forwardedPackets.get(),
                droppedPackets.get(), corruptedPackets.get(),
                duplicatedPackets.get(), reorderedPackets.get());
    }

    private static double pct(long part, long total) {
        return total > 0 ? (part * 100.0 / total) : 0.0;
    }

    // ── Logging ──────────────────────────────────────────────────

    private void log(String msg) {
        System.out.println("[Channel] " + msg);
    }

    // ── Inner class: delayed packet ──────────────────────────────

    private static class DelayedPacket implements Comparable<DelayedPacket> {
        final byte[] data;
        final InetAddress address;
        final int port;
        final long deliveryTime; // Absolute time in ms

        DelayedPacket(byte[] data, InetAddress address, int port, long deliveryTime) {
            this.data = data;
            this.address = address;
            this.port = port;
            this.deliveryTime = deliveryTime;
        }

        @Override
        public int compareTo(DelayedPacket other) {
            return Long.compare(this.deliveryTime, other.deliveryTime);
        }
    }
}

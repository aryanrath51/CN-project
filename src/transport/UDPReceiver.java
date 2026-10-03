package transport;

import packet.Packet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated UDP receiver running in its own thread.
 *
 * Architecture (from proposal Risk 1 mitigation):
 * - A single dedicated reader thread calls socket.receive() in a tight loop.
 * - Received raw byte arrays are placed into a thread-safe BlockingQueue.
 * - ARQ layers consume from the queue via receive() / tryReceive().
 *
 * This isolates socket I/O into one thread, eliminating race conditions
 * when multiple threads need to read ACKs or data concurrently.
 *
 * ┌──────────┐       ┌───────────────┐       ┌──────────────┐
 * │ Network  │──────►│ RecvThread    │──────►│ BlockingQueue │
 * │ (socket) │       │ (this class)  │       │              │
 * └──────────┘       └───────────────┘       └──────┬───────┘
 *                                                    │
 *                                              ┌─────▼──────┐
 *                                              │  ARQ Layer  │
 *                                              │  (consumer) │
 *                                              └─────────────┘
 */
public class UDPReceiver implements Runnable {

    private final DatagramSocket socket;
    private final BlockingQueue<byte[]> recvQueue;
    private volatile boolean running = true;

    /** Max size of a single received datagram. */
    private static final int MAX_PACKET_SIZE = Packet.HEADER_SIZE + Packet.MAX_PAYLOAD_SIZE;

    // ── Statistics ────────────────────────────────────────────────

    private long packetsReceived = 0;
    private long bytesReceived = 0;
    private long queueDrops = 0; // Packets dropped because queue was full

    // ── Constructor ──────────────────────────────────────────────

    /**
     * Create a UDPReceiver.
     *
     * @param socket        the DatagramSocket to listen on
     * @param queueCapacity max number of packets buffered in the queue
     */
    public UDPReceiver(DatagramSocket socket, int queueCapacity) {
        this.socket = socket;
        this.recvQueue = new LinkedBlockingQueue<>(queueCapacity);
    }

    /**
     * Convenience constructor with default queue capacity (1024).
     */
    public UDPReceiver(DatagramSocket socket) {
        this(socket, 1024);
    }

    // ── Receiver loop (runs in dedicated thread) ─────────────────

    @Override
    public void run() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];

        while (running) {
            try {
                DatagramPacket dgPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(dgPacket); // Blocks until data arrives

                // CRITICAL: Copy data out of the reusable buffer before enqueuing.
                // The buffer is reused across iterations — without this copy,
                // consumers would see garbled data from subsequent receives.
                byte[] data = new byte[dgPacket.getLength()];
                System.arraycopy(dgPacket.getData(), dgPacket.getOffset(),
                        data, 0, dgPacket.getLength());

                // Offer with no wait — if queue is full, drop the packet
                // (ARQ retransmission will recover it)
                boolean enqueued = recvQueue.offer(data);
                if (enqueued) {
                    packetsReceived++;
                    bytesReceived += data.length;
                } else {
                    queueDrops++;
                }

            } catch (SocketException e) {
                // Expected when stop() closes the socket to unblock receive()
                if (running) {
                    System.err.println("[UDPReceiver] Socket error: " + e.getMessage());
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[UDPReceiver] Receive error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // ── Consumer methods (called by ARQ layers) ──────────────────

    /**
     * Blocking receive with timeout.
     * Used by ARQ layers to wait for ACKs/data with RTO deadline.
     *
     * @param timeoutMs max time to wait in milliseconds; 0 or negative = wait forever
     * @return the raw packet bytes, or null if the timeout expires
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public byte[] receive(long timeoutMs) throws InterruptedException {
        if (timeoutMs <= 0) {
            return recvQueue.take(); // Block indefinitely
        }
        return recvQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Non-blocking receive.
     * Returns immediately with a packet or null.
     *
     * @return the raw packet bytes, or null if the queue is empty
     */
    public byte[] tryReceive() {
        return recvQueue.poll();
    }

    /**
     * Drain all currently queued packets (useful for resetting state).
     */
    public void clear() {
        recvQueue.clear();
    }

    /**
     * @return number of packets currently buffered in the queue
     */
    public int queueSize() {
        return recvQueue.size();
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    /**
     * Stop the receiver loop and close the socket.
     * The socket.close() unblocks the blocking receive() call.
     */
    public void stop() {
        running = false;
        socket.close(); // Unblocks socket.receive()
    }

    /**
     * @return true if the receiver loop is still running
     */
    public boolean isRunning() {
        return running;
    }

    // ── Statistics ────────────────────────────────────────────────

    public long getPacketsReceived() { return packetsReceived; }
    public long getBytesReceived() { return bytesReceived; }
    public long getQueueDrops() { return queueDrops; }
}

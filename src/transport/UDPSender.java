package transport;

import packet.Packet;
import packet.PacketSerializer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Thread-safe UDP sender.
 *
 * All send operations are synchronized on the socket to prevent interleaving
 * when multiple threads (e.g., data sender + ACK sender) write concurrently.
 *
 * This addresses Risk 1 from the proposal: "Race conditions under concurrent
 * socket reads/background ACKs."
 */
public class UDPSender {

    private final DatagramSocket socket;
    private final InetAddress remoteAddress;
    private final int remotePort;

    // ── Statistics ────────────────────────────────────────────────

    private long packetsSent = 0;
    private long bytesSent = 0;

    // ── Constructor ──────────────────────────────────────────────

    /**
     * Create a UDPSender bound to the given socket, targeting the specified remote.
     *
     * @param socket     the DatagramSocket to send through
     * @param remoteHost the destination hostname or IP
     * @param remotePort the destination port
     * @throws UnknownHostException if remoteHost cannot be resolved
     */
    public UDPSender(DatagramSocket socket, String remoteHost, int remotePort)
            throws UnknownHostException {
        this.socket = socket;
        this.remoteAddress = InetAddress.getByName(remoteHost);
        this.remotePort = remotePort;
    }

    // ── Send methods ─────────────────────────────────────────────

    /**
     * Send raw bytes over UDP. Thread-safe.
     *
     * @param data the serialized packet bytes
     * @throws IOException if the send fails
     */
    public synchronized void send(byte[] data) throws IOException {
        DatagramPacket dgPacket = new DatagramPacket(
                data, data.length, remoteAddress, remotePort
        );
        socket.send(dgPacket);
        packetsSent++;
        bytesSent += data.length;
    }

    /**
     * Convenience: serialize a {@link Packet} and send it.
     *
     * @param packet the Packet to serialize and send
     * @throws IOException if the send fails
     */
    public void sendPacket(Packet packet) throws IOException {
        byte[] serialized = PacketSerializer.serialize(packet);
        send(serialized);
    }

    // ── Statistics ────────────────────────────────────────────────

    public long getPacketsSent() { return packetsSent; }
    public long getBytesSent() { return bytesSent; }

    /**
     * Reset send statistics counters.
     */
    public synchronized void resetStats() {
        packetsSent = 0;
        bytesSent = 0;
    }
}

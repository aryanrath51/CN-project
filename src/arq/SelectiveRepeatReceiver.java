package arq;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class SelectiveRepeatReceiver {
    private int expectedSeqNum;
    private Map<Integer, byte[]> buffer; // Buffers out-of-order packets
    private int windowSize;

    public SelectiveRepeatReceiver(int windowSize) {
        this.expectedSeqNum = 0;
        this.windowSize = windowSize;
        this.buffer = new HashMap<>();
    }

    // --- STRETCH GOAL: Server-Side 3-Way Handshake Listener ---
    public boolean listenForHandshake(DatagramSocket socket) throws IOException {
        System.out.println("[Server] Waiting for client connection (SYN)...");
        socket.setSoTimeout(0); // Wait indefinitely for connection request

        while (true) {
            byte[] bufferBytes = new byte[1024];
            DatagramPacket packet = new DatagramPacket(bufferBytes, bufferBytes.length);
            socket.receive(packet);

            String message = new String(packet.getData(), 0, packet.getLength());
            if (message.equals("SYN")) {
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                System.out.println("[Handshake] Received SYN. Sending SYN-ACK...");
                byte[] synAckData = "SYN-ACK".getBytes();
                DatagramPacket synAckPacket = new DatagramPacket(synAckData, synAckData.length, clientAddress, clientPort);
                socket.send(synAckPacket);

                // Wait for final ACK from sender
                socket.setSoTimeout(2000);
                try {
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);
                    String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    
                    if (ackMessage.equals("ACK")) {
                        System.out.println("[Handshake] Received final ACK. Connection Established!");
                        return true;
                    }
                } catch (Exception e) {
                    System.err.println("[Handshake] Timeout waiting for final ACK. Retrying handshake...");
                }
            }
        }
    }

    // --- STRETCH GOAL: Graceful Teardown Listener ---
    public void listenForTeardown(DatagramSocket socket) throws IOException {
        System.out.println("[Teardown] Waiting for FIN from sender...");
        socket.setSoTimeout(5000);
        try {
            byte[] bufferBytes = new byte[1024];
            DatagramPacket packet = new DatagramPacket(bufferBytes, bufferBytes.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            
            if (message.equals("FIN")) {
                System.out.println("[Teardown] Received FIN. Sending FIN-ACK...");
                byte[] finAckData = "FIN-ACK".getBytes();
                DatagramPacket finAckPacket = new DatagramPacket(finAckData, finAckData.length, packet.getAddress(), packet.getPort());
                socket.send(finAckPacket);
                System.out.println("[Teardown] Connection closed gracefully.");
            }
        } catch (Exception e) {
            System.out.println("[Teardown] Teardown window closed or timed out.");
        }
    }

    // Process an incoming packet
    public boolean addPacket(int seqNum, byte[] payload) {
        // If packet is within the acceptable receive window
        if (seqNum >= expectedSeqNum && seqNum < expectedSeqNum + windowSize) {
            // Buffer the packet if it's out of order
            if (!buffer.containsKey(seqNum)) {
                buffer.put(seqNum, payload);
            }

            // Deliver contiguous packets in order
            while (buffer.containsKey(expectedSeqNum)) {
                byte[] data = buffer.get(expectedSeqNum);
                deliverData(expectedSeqNum, data);
                buffer.remove(expectedSeqNum);
                expectedSeqNum++;
            }
            return true;
        } else if (seqNum < expectedSeqNum) {
            // Already delivered packet, just re-acknowledge it
            return true;
        }
        
        // Outside window, ignore
        return false;
    }

    private void deliverData(int seqNum, byte[] data) {
        // Here is where you pass the ordered byte data to Priyanshi's file writer module
        System.out.println("Delivering packet in order: " + seqNum);
    }
}
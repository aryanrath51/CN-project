package arq;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class SelectiveRepeatSender {
    private int windowSize;
    private int base;
    private int nextSeqNum;
    private Map<Integer, Long> sentTimestamps;
    private AdaptiveRTO rtoEngine;

    public SelectiveRepeatSender(int windowSize) {
        this.windowSize = windowSize;
        this.base = 0;
        this.nextSeqNum = 0;
        this.sentTimestamps = new HashMap<>();
        this.rtoEngine = new AdaptiveRTO(1000.0); // Start with 1 second initial RTO
    }

    // --- STRETCH GOAL: 3-Way Handshake (Client Side) ---
    public boolean performHandshake(DatagramSocket socket, InetAddress address, int port) throws IOException {
        System.out.println("[Handshake] Initiating connection (SYN)...");
        
        // Step 1: Send SYN packet (using seqNum -1 or a control flag representation)
        byte[] synData = "SYN".getBytes();
        DatagramPacket synPacket = new DatagramPacket(synData, synData.length, address, port);
        socket.send(synPacket);

        socket.setSoTimeout(2000); // 2 second timeout for handshake response

        try {
            // Step 2: Receive SYN-ACK from receiver
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            
            String responseStr = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseStr.equals("SYN-ACK")) {
                System.out.println("[Handshake] Received SYN-ACK. Sending final ACK...");

                // Step 3: Send final ACK
                byte[] ackData = "ACK".getBytes();
                DatagramPacket finalAckPacket = new DatagramPacket(ackData, ackData.length, address, port);
                socket.send(finalAckPacket);
                
                System.out.println("[Handshake] Connection Established successfully!");
                return true;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[Handshake] Timeout waiting for SYN-ACK. Handshake failed.");
        }
        return false;
    }

    // --- STRETCH GOAL: Graceful Teardown ---
    public void performTeardown(DatagramSocket socket, InetAddress address, int port) throws IOException {
        System.out.println("[Teardown] Sending FIN to close connection...");
        byte[] finData = "FIN".getBytes();
        DatagramPacket finPacket = new DatagramPacket(finData, finData.length, address, port);
        socket.send(finPacket);

        socket.setSoTimeout(2000);
        try {
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            String responseStr = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseStr.equals("FIN-ACK")) {
                System.out.println("[Teardown] Received FIN-ACK. Connection closed gracefully.");
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[Teardown] Teardown acknowledged or timed out, closing socket.");
        }
    }

    // Check if we can send a new packet within our window
    public boolean canSend() {
        return nextSeqNum < base + windowSize;
    }

    public void packetSent(int seqNum) {
        sentTimestamps.put(seqNum, System.currentTimeMillis());
        nextSeqNum++;
    }

    // Called when an ACK is received from the receiver
    public void receiveAck(int ackNum) {
        if (sentTimestamps.containsKey(ackNum)) {
            long sendTime = sentTimestamps.get(ackNum);
            double sampleRtt = System.currentTimeMillis() - sendTime;
            
            // Update RTO engine (assuming first transmission for simplicity here)
            rtoEngine.updateRtt(sampleRtt, false);
            
            sentTimestamps.remove(ackNum);

            // Slide window base forward if base packet is acknowledged
            while (!sentTimestamps.containsKey(base) && base < nextSeqNum) {
                // Advance base past acknowledged chunks
                if (sentTimestamps.isEmpty() && base < nextSeqNum) {
                    base = nextSeqNum; // Catch up if window is clear
                    break;
                }
                base++;
            }
        }
    }
}
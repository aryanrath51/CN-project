package arq;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectiveRepeatSender {
    private int windowSize;
    private int base;
    private int nextSeqNum;
    private Map<Integer, Long> sentTimestamps;
    private AdaptiveRTO rtoEngine;
    private int currentReceiverWindow; // Flow control tracking

    // --- STRETCH GOAL: Nagle-Style Coalescing Buffers ---
    private List<Byte> nagleBuffer = new ArrayList<>();
    private static final int MSS = 512; // Maximum Segment Size threshold

    public SelectiveRepeatSender(int windowSize) {
        this.windowSize = windowSize;
        this.base = 0;
        this.nextSeqNum = 0;
        this.sentTimestamps = new HashMap<>();
        this.rtoEngine = new AdaptiveRTO(1000.0); // Start with 1 second initial RTO
        this.currentReceiverWindow = windowSize;  // Initially match default window
    }

    // --- STRETCH GOAL: 3-Way Handshake (Client Side) ---
    public boolean performHandshake(DatagramSocket socket, InetAddress address, int port) throws IOException {
        System.out.println("[Handshake] Initiating connection (SYN)...");
        
        byte[] synData = "SYN".getBytes();
        DatagramPacket synPacket = new DatagramPacket(synData, synData.length, address, port);
        socket.send(synPacket);

        socket.setSoTimeout(2000);

        try {
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            
            String responseStr = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseStr.equals("SYN-ACK")) {
                System.out.println("[Handshake] Received SYN-ACK. Sending final ACK...");

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

    // --- STRETCH GOAL: Flow Control (Dynamic Window Adjustment) ---
    public void updateReceiverWindow(int receiverWindow) {
        this.currentReceiverWindow = receiverWindow;
        if (receiverWindow < this.windowSize) {
            System.out.println("[Flow Control] Receiver buffer space low. Throttling sender window to: " + receiverWindow);
        }
    }

    // --- STRETCH GOAL: SACK Blocks (Process non-contiguous acknowledgements) ---
    public void processSackReport(String sackReport) {
        if (sackReport == null || sackReport.isEmpty()) {
            return;
        }
        
        System.out.println("[SACK] Processing SACK blocks from receiver: " + sackReport);
        
        String[] blocks = sackReport.replace("[", "").split("]");
        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;
            String[] bounds = block.trim().split("-");
            if (bounds.length == 2) {
                int start = Integer.parseInt(bounds[0].trim());
                int end = Integer.parseInt(bounds[1].trim());
                
                for (int i = start; i <= end; i++) {
                    if (sentTimestamps.containsKey(i)) {
                        sentTimestamps.remove(i);
                        System.out.println("[SACK] SACK block confirmed packet " + i + ", removing from retransmission queue.");
                    }
                }
            }
        }

        while (!sentTimestamps.containsKey(base) && base < nextSeqNum) {
            if (sentTimestamps.isEmpty() && base < nextSeqNum) {
                base = nextSeqNum;
                break;
            }
            base++;
        }
    }

    // --- STRETCH GOAL: Nagle-Style Coalescing Buffer Methods ---
    public boolean queuePayload(byte[] payload, List<byte[]> readyPackets) {
        for (byte b : payload) {
            nagleBuffer.add(b);
        }
        
        // Coalesce until MSS threshold is hit
        if (nagleBuffer.size() >= MSS) {
            flushNagleBuffer(readyPackets);
            return true;
        }
        return false;
    }

    public void flushNagleBuffer(List<byte[]> readyPackets) {
        if (nagleBuffer.isEmpty()) return;
        
        byte[] chunk = new byte[nagleBuffer.size()];
        for (int i = 0; i < nagleBuffer.size(); i++) {
            chunk[i] = nagleBuffer.get(i);
        }
        readyPackets.add(chunk);
        nagleBuffer.clear();
        System.out.println("[Nagle] Coalesced and flushed packet of size: " + chunk.length + " bytes.");
    }

    // Check if we can send a new packet within our window and receiver's available window
    public boolean canSend() {
        int effectiveWindow = Math.min(windowSize, currentReceiverWindow);
        return nextSeqNum < base + effectiveWindow;
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
            
            rtoEngine.updateRtt(sampleRtt, false);
            sentTimestamps.remove(ackNum);

            while (!sentTimestamps.containsKey(base) && base < nextSeqNum) {
                if (sentTimestamps.isEmpty() && base < nextSeqNum) {
                    base = nextSeqNum; 
                    break;
                }
                base++;
            }
        }
    }
}
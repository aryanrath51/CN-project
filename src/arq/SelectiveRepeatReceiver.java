package arq;

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
package arq;

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
            while (!sentTimestamps.containsKey(base) && base < nextSeqNum && sentTimestamps.isEmpty()) {
                // If map is empty or base is cleared, advance base
                base++;
            }
        }
    }
}
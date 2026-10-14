package arq;

public class AdaptiveRTO {
    private double srtt; // Smoothed RTT
    private double rttvar; // RTT Variation
    private double rto; // Retransmission Timeout
    
    private static final double ALPHA = 0.125; // 1/8
    private static final double BETA = 0.25;   // 1/4

    public AdaptiveRTO(double initialRtoMillis) {
        this.srtt = initialRtoMillis;
        this.rttvar = initialRtoMillis / 2.0;
        this.rto = initialRtoMillis;
    }

    // Called when a packet is successfully acknowledged (Karn's algorithm: ignore retransmitted samples)
    public void updateRtt(double sampleRtt, boolean wasRetransmitted) {
        if (wasRetransmitted) {
            // Karn's algorithm: Do not update RTT estimation for retransmitted packets to avoid ambiguity
            return;
        }

        // Jacobson/Karels Algorithm
        rttvar = (1 - BETA) * rttvar + BETA * Math.abs(srtt - sampleRtt);
        srtt = (1 - ALPHA) * srtt + ALPHA * sampleRtt;
        rto = srtt + 4.0 * rttvar;

        // Ensure a safe minimum RTO (e.g., 20ms)
        if (rto < 20.0) {
            rto = 20.0;
        }
    }

    public long getRtoMillis() {
        return (long) rto;
    }
}
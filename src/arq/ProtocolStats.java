package arq;

/**
 * Captures protocol performance metrics for experiment comparison.
 *
 * Used by the experiment runner (Week 7-8) to measure:
 * - Goodput (effective throughput excluding retransmissions)
 * - Total retransmissions
 * - Transfer time
 *
 * Each ARQ protocol implementation updates these counters during transfer.
 */
public class ProtocolStats {

    // ── Counters ─────────────────────────────────────────────────

    /** Total application-layer bytes successfully delivered. */
    public long totalBytesDelivered;

    /** Total bytes sent on the wire (including retransmissions). */
    public long totalBytesSent;

    /** Total unique data packets sent (first transmission). */
    public long totalPacketsSent;

    /** Total retransmissions (packets sent more than once). */
    public long totalRetransmissions;

    /** Total ACKs received. */
    public long totalAcksReceived;

    /** Total packets received by the receiver side. */
    public long totalPacketsReceived;

    /** Total duplicate packets received (already acknowledged). */
    public long totalDuplicatesReceived;

    /** Total packets dropped due to checksum failure. */
    public long totalCorruptDrops;

    // ── Timing ───────────────────────────────────────────────────

    /** Transfer start time (System.nanoTime()). */
    public long startTimeNanos;

    /** Transfer end time (System.nanoTime()). */
    public long endTimeNanos;

    // ── Derived metrics ──────────────────────────────────────────

    /**
     * Goodput: effective application-layer throughput in bytes per second.
     * This is the metric the proposal claims to test:
     * "Selective Repeat maintains significantly higher goodput than
     *  Go-Back-N and Stop-and-Wait as loss rates exceed 10%."
     */
    public double getGoodputBytesPerSec() {
        double elapsedSec = getElapsedSeconds();
        return elapsedSec > 0 ? totalBytesDelivered / elapsedSec : 0;
    }

    /**
     * Raw throughput including retransmissions, in bytes per second.
     */
    public double getRawThroughputBytesPerSec() {
        double elapsedSec = getElapsedSeconds();
        return elapsedSec > 0 ? totalBytesSent / elapsedSec : 0;
    }

    /**
     * Elapsed transfer time in seconds.
     */
    public double getElapsedSeconds() {
        return (endTimeNanos - startTimeNanos) / 1_000_000_000.0;
    }

    /**
     * Retransmission ratio: fraction of total sends that were retransmissions.
     */
    public double getRetransmissionRatio() {
        long totalSends = totalPacketsSent + totalRetransmissions;
        return totalSends > 0 ? (double) totalRetransmissions / totalSends : 0;
    }

    /**
     * Efficiency: ratio of useful bytes to total bytes sent.
     */
    public double getEfficiency() {
        return totalBytesSent > 0 ? (double) totalBytesDelivered / totalBytesSent : 0;
    }

    // ── Lifecycle helpers ────────────────────────────────────────

    /** Mark the start of the transfer. */
    public void markStart() {
        startTimeNanos = System.nanoTime();
    }

    /** Mark the end of the transfer. */
    public void markEnd() {
        endTimeNanos = System.nanoTime();
    }

    /** Reset all counters. */
    public void reset() {
        totalBytesDelivered = 0;
        totalBytesSent = 0;
        totalPacketsSent = 0;
        totalRetransmissions = 0;
        totalAcksReceived = 0;
        totalPacketsReceived = 0;
        totalDuplicatesReceived = 0;
        totalCorruptDrops = 0;
        startTimeNanos = 0;
        endTimeNanos = 0;
    }

    // ── Output ───────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
                "ProtocolStats{delivered=%d bytes, sent=%d bytes, " +
                "packets=%d, retx=%d, efficiency=%.1f%%, " +
                "goodput=%.2f KB/s, time=%.3f s}",
                totalBytesDelivered, totalBytesSent,
                totalPacketsSent, totalRetransmissions,
                getEfficiency() * 100,
                getGoodputBytesPerSec() / 1024.0,
                getElapsedSeconds());
    }

    /**
     * CSV header for experiment data collection.
     */
    public static String csvHeader() {
        return "bytes_delivered,bytes_sent,packets_sent,retransmissions," +
                "acks_received,packets_received,duplicates,corrupt_drops," +
                "elapsed_sec,goodput_bps,efficiency";
    }

    /**
     * CSV row for experiment data collection.
     */
    public String toCsv() {
        return String.format("%d,%d,%d,%d,%d,%d,%d,%d,%.6f,%.2f,%.4f",
                totalBytesDelivered, totalBytesSent,
                totalPacketsSent, totalRetransmissions,
                totalAcksReceived, totalPacketsReceived,
                totalDuplicatesReceived, totalCorruptDrops,
                getElapsedSeconds(), getGoodputBytesPerSec(),
                getEfficiency());
    }
}

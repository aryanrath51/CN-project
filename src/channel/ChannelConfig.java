package channel;

/**
 * Configuration for the deterministic channel emulator.
 *
 * All rates are probabilities in [0.0, 1.0].
 * The randomSeed ensures reproducibility: the same seed produces the exact
 * same sequence of drop/corrupt/reorder decisions, enabling fair comparison
 * across Stop-and-Wait, Go-Back-N, and Selective Repeat.
 */
public class ChannelConfig {

    private double lossRate = 0.0;           // Probability of dropping a packet
    private double duplicateRate = 0.0;      // Probability of sending a duplicate
    private double corruptionRate = 0.0;     // Probability of flipping bits
    private double reorderRate = 0.0;        // Probability of adding extra delay (reordering)
    private int minDelayMs = 0;              // Minimum propagation delay (ms)
    private int maxDelayMs = 0;              // Maximum propagation delay (ms)
    private long randomSeed = 42L;           // Seed for java.util.Random

    // ── Constructors ──────────────────────────────────────────────

    public ChannelConfig() {}

    // ── Static presets for common scenarios ───────────────────────

    /** Perfect channel: no loss, no corruption, no delay. */
    public static ChannelConfig perfectChannel() {
        return new ChannelConfig();
    }

    /** Channel with only packet loss. */
    public static ChannelConfig lossyChannel(double lossRate, long seed) {
        ChannelConfig c = new ChannelConfig();
        c.lossRate = lossRate;
        c.randomSeed = seed;
        return c;
    }

    /**
     * Full experiment profile with all impairment types.
     *
     * @param loss       packet loss rate [0.0, 1.0]
     * @param corrupt    corruption rate [0.0, 1.0]
     * @param reorder    reorder rate [0.0, 1.0]
     * @param delayMs    fixed propagation delay in ms
     * @param seed       random seed for reproducibility
     */
    public static ChannelConfig experimentProfile(double loss, double corrupt,
                                                   double reorder, int delayMs, long seed) {
        ChannelConfig c = new ChannelConfig();
        c.lossRate = loss;
        c.corruptionRate = corrupt;
        c.reorderRate = reorder;
        c.minDelayMs = delayMs;
        c.maxDelayMs = delayMs;
        c.randomSeed = seed;
        return c;
    }

    /**
     * Full experiment profile with jitter (variable delay).
     *
     * @param loss       packet loss rate
     * @param corrupt    corruption rate
     * @param duplicate  duplication rate
     * @param reorder    reorder rate
     * @param minDelay   minimum delay in ms
     * @param maxDelay   maximum delay in ms
     * @param seed       random seed
     */
    public static ChannelConfig fullProfile(double loss, double corrupt, double duplicate,
                                            double reorder, int minDelay, int maxDelay, long seed) {
        ChannelConfig c = new ChannelConfig();
        c.lossRate = loss;
        c.corruptionRate = corrupt;
        c.duplicateRate = duplicate;
        c.reorderRate = reorder;
        c.minDelayMs = minDelay;
        c.maxDelayMs = maxDelay;
        c.randomSeed = seed;
        return c;
    }

    // ── Getters & Setters ─────────────────────────────────────────

    public double getLossRate() { return lossRate; }
    public void setLossRate(double lossRate) { this.lossRate = clampRate(lossRate); }

    public double getDuplicateRate() { return duplicateRate; }
    public void setDuplicateRate(double duplicateRate) { this.duplicateRate = clampRate(duplicateRate); }

    public double getCorruptionRate() { return corruptionRate; }
    public void setCorruptionRate(double corruptionRate) { this.corruptionRate = clampRate(corruptionRate); }

    public double getReorderRate() { return reorderRate; }
    public void setReorderRate(double reorderRate) { this.reorderRate = clampRate(reorderRate); }

    public int getMinDelayMs() { return minDelayMs; }
    public void setMinDelayMs(int minDelayMs) { this.minDelayMs = Math.max(0, minDelayMs); }

    public int getMaxDelayMs() { return maxDelayMs; }
    public void setMaxDelayMs(int maxDelayMs) { this.maxDelayMs = Math.max(0, maxDelayMs); }

    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }

    // ── Helpers ───────────────────────────────────────────────────

    private static double clampRate(double rate) {
        return Math.max(0.0, Math.min(1.0, rate));
    }

    @Override
    public String toString() {
        return String.format(
                "ChannelConfig{loss=%.2f, corrupt=%.2f, dup=%.2f, reorder=%.2f, " +
                "delay=%d-%dms, seed=%d}",
                lossRate, corruptionRate, duplicateRate, reorderRate,
                minDelayMs, maxDelayMs, randomSeed);
    }
}

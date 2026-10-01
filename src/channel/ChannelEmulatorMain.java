package channel;

/**
 * Standalone runner for the Channel Emulator.
 *
 * Usage:
 *   java channel.ChannelEmulatorMain listenPort destHost destPort lossRate [corruptRate] [reorderRate] [delayMs] [seed]
 *
 * Example:
 *   java channel.ChannelEmulatorMain 9000 localhost 9001 0.1 0.01 0.0 10 42
 *
 * This runs as a separate process between sender and receiver:
 *   Sender (port 8000) -> Emulator (port 9000) -> Receiver (port 9001)
 *
 * Press Ctrl+C to stop. Statistics are printed on shutdown.
 */
public class ChannelEmulatorMain {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: ChannelEmulatorMain <listenPort> <destHost> <destPort> <lossRate> " +
                    "[corruptRate] [reorderRate] [delayMs] [seed]");
            System.err.println();
            System.err.println("Example: ChannelEmulatorMain 9000 localhost 9001 0.1 0.01 0.0 10 42");
            System.exit(1);
        }

        try {
            int listenPort = Integer.parseInt(args[0]);
            String destHost = args[1];
            int destPort = Integer.parseInt(args[2]);
            double lossRate = Double.parseDouble(args[3]);
            double corruptRate = args.length > 4 ? Double.parseDouble(args[4]) : 0.01;
            double reorderRate = args.length > 5 ? Double.parseDouble(args[5]) : 0.0;
            int delayMs = args.length > 6 ? Integer.parseInt(args[6]) : 10;
            long seed = args.length > 7 ? Long.parseLong(args[7]) : 42L;

            ChannelConfig config = ChannelConfig.experimentProfile(
                    lossRate, corruptRate, reorderRate, delayMs, seed
            );

            ChannelEmulator emulator = new ChannelEmulator(config, listenPort, destHost, destPort);

            System.out.println("+==================================================+");
            System.out.println("|          Channel Emulator -- Running             |");
            System.out.println("+==================================================+");
            System.out.printf("|  Listening on port : %-27d|%n", listenPort);
            System.out.printf("|  Forwarding to     : %-27s|%n", destHost + ":" + destPort);
            System.out.printf("|  Loss rate         : %-27.1f|%n", lossRate * 100);
            System.out.printf("|  Corruption rate   : %-27.1f|%n", corruptRate * 100);
            System.out.printf("|  Reorder rate      : %-27.1f|%n", reorderRate * 100);
            System.out.printf("|  Delay             : %-27s|%n", delayMs + " ms");
            System.out.printf("|  Seed              : %-27d|%n", seed);
            System.out.println("+==================================================+");
            System.out.println("|  Press Ctrl+C to stop                           |");
            System.out.println("+==================================================+");

            // Register shutdown hook for clean exit + stats printing
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Channel] Shutting down...");
                emulator.stop();
            }));

            // Run (blocks until stopped)
            emulator.run();

        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format -- " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

package transport;

/**
 * Configuration for the UDP transport layer.
 * Encapsulates local/remote socket binding and buffer sizing.
 * Uses the Builder pattern for clean construction.
 */
public class TransportConfig {

    private int localPort;
    private String remoteHost;
    private int remotePort;
    private int socketTimeoutMs = 0;       // 0 = infinite blocking
    private int recvBufferSize = 65536;    // OS socket receive buffer
    private int sendBufferSize = 65536;    // OS socket send buffer
    private int recvQueueCapacity = 1024;  // BlockingQueue capacity for UDPReceiver

    // ── Private constructor — use Builder ─────────────────────────

    private TransportConfig() {}

    // ── Builder ───────────────────────────────────────────────────

    public static class Builder {
        private final TransportConfig config = new TransportConfig();

        public Builder localPort(int port) {
            config.localPort = port;
            return this;
        }

        public Builder remoteHost(String host) {
            config.remoteHost = host;
            return this;
        }

        public Builder remotePort(int port) {
            config.remotePort = port;
            return this;
        }

        public Builder socketTimeout(int ms) {
            config.socketTimeoutMs = ms;
            return this;
        }

        public Builder recvBufferSize(int bytes) {
            config.recvBufferSize = bytes;
            return this;
        }

        public Builder sendBufferSize(int bytes) {
            config.sendBufferSize = bytes;
            return this;
        }

        public Builder recvQueueCapacity(int capacity) {
            config.recvQueueCapacity = capacity;
            return this;
        }

        public TransportConfig build() {
            if (config.remoteHost == null || config.remoteHost.isEmpty()) {
                throw new IllegalStateException("remoteHost must be set");
            }
            if (config.localPort <= 0 || config.localPort > 65535) {
                throw new IllegalStateException("localPort must be 1-65535, got: " + config.localPort);
            }
            if (config.remotePort <= 0 || config.remotePort > 65535) {
                throw new IllegalStateException("remotePort must be 1-65535, got: " + config.remotePort);
            }
            return config;
        }
    }

    // ── Getters ───────────────────────────────────────────────────

    public int getLocalPort() { return localPort; }
    public String getRemoteHost() { return remoteHost; }
    public int getRemotePort() { return remotePort; }
    public int getSocketTimeoutMs() { return socketTimeoutMs; }
    public int getRecvBufferSize() { return recvBufferSize; }
    public int getSendBufferSize() { return sendBufferSize; }
    public int getRecvQueueCapacity() { return recvQueueCapacity; }

    @Override
    public String toString() {
        return String.format("TransportConfig{local=:%d, remote=%s:%d, timeout=%dms, queueCap=%d}",
                localPort, remoteHost, remotePort, socketTimeoutMs, recvQueueCapacity);
    }
}

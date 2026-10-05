package arq;

public class GoBackN implements ARQProtocol {

    private final ProtocolStats stats = new ProtocolStats();

    @Override
    public void sendData(byte[] fileData) throws Exception {
        // Go-Back-N sender implementation will be added incrementally.
    }

    @Override
    public byte[] receiveData() throws Exception {
        // Go-Back-N receiver implementation will be added incrementally.
        return new byte[0];
    }

    @Override
    public ProtocolStats getStats() {
        return stats;
    }

    @Override
    public String getProtocolName() {
        return "Go-Back-N";
    }
}
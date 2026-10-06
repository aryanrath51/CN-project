import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class StopAndWait {
    private static final int PORT = 9876;
    private static final int TIMEOUT_MS = 2000; // 2 seconds timeout

    public static void sendFilePacket(String data, String serverIp) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(serverIp);
            
            byte[] buffer = data.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, PORT);

            boolean acknowledged = false;
            while (!acknowledged) {
                try {
                    // Packet bhejna
                    socket.send(packet);
                    System.out.println("Packet sent, waiting for ACK...");

                    // ACK receive karne ke liye buffer
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    
                    socket.receive(ackPacket);
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    
                    if (ack.equals("ACK")) {
                        System.out.println("ACK received successfully!");
                        acknowledged = true;
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout! Resending packet...");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("Stop-and-Wait Sender Initialized...");
        // Example call: sendFilePacket("Hello UDP", "127.0.0.1");
    }
}
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class FileTransferApp {
    
    // SHA-256 Hash generate karne ka function (Aapka assigned task)
    public static String getFileChecksum(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteArray = new byte[1024];
            int bytesCount = 0;
            
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java transport.FileTransferApp <filepath>");
            return;
        }
        File file = new File(args[0]);
        if (file.exists()) {
            System.out.println("SHA-256 Hash for " + file.getName() + ": " + getFileChecksum(file));
        } else {
            System.out.println("File not found: " + args[0]);
        }
    }
}
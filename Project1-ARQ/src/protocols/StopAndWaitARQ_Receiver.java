package protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StopAndWaitARQ_Receiver {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private static final char MAX_SEQ_NUM = 255;
    private static final char TOTAL_SEQ_NUM = (MAX_SEQ_NUM+1);
    private final int port;
    private final String outputFile;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final List<byte[]> receivedData;
    private int totalPacketsReceived;
    private int currentPacketIndex; // points to current packet index

    public StopAndWaitARQ_Receiver(int port, String outputFile) {
        this.port = port;
        this.outputFile = outputFile;
        this.running = false;
        this.receivedData = new ArrayList<>();
        this.totalPacketsReceived = 0;
        this.currentPacketIndex = 0;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Receiver listening on port " + port);
        Socket clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        while (running) {
            try{
                // Read packet metadata
                int packetLength = in.readInt();
                char packetIndex = in.readChar();
                boolean isLastPacket = in.readBoolean();

                // Read packet data
                byte[] packetData = new byte[packetLength];
                in.readFully(packetData);
                BISYNCPacket packet = new BISYNCPacket(packetData, true);

                // Verify packet integrity using checksum
                if (packet.isValid()) {
                    // If packet is valid:
                    // 1. Store the packet data
                    ensureCapacity(packetIndex);
                    receivedData.set(packetIndex, packet.getData());
                    totalPacketsReceived++;

                    // 2. Send ACK with next expected sequence number
                    char nextSeqNum = (char)((packetIndex + 1) % TOTAL_SEQ_NUM);
                    out.writeByte(ACK);
                    out.writeChar(nextSeqNum);
                    out.flush();

                    // Update current packet index
                    currentPacketIndex = packetIndex;

                    // If this is the last packet, stop the receiver
                    if (isLastPacket) {
                        running = false;
                    }
                } else {
                    // If packet is invalid:
                    // 1. Send NAK with the current sequence number
                    out.writeByte(NAK);
                    out.writeChar(packetIndex);
                    out.flush();
                }





            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        }

        // after receive all the packets, save them into the output file
        saveFile();

    }

    private void ensureCapacity(int index) {
        while (receivedData.size() <= index) {
            receivedData.add(null);
        }
    }

    private void saveFile() {
        try {
            // Calculate total size
            int totalSize = 0;
            for (byte[] data : receivedData) {
                if (data != null) {
                    totalSize += data.length;
                }
            }

            // Combine all packets
            byte[] completeFile = new byte[totalSize];
            int offset = 0;
            for (byte[] data : receivedData) {
                if (data != null) {
                    System.arraycopy(data, 0, completeFile, offset, data.length);
                    offset += data.length;
                }
            }

            // Write to file
            Files.write(Paths.get(outputFile), completeFile);
            System.out.println("File saved successfully: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error saving video file: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }
}
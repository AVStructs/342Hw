/**
 * Author: Aiden Vangura, Joshua Blanks
 * Course: COMP 342 Data Communications and Networking
 * Date: 03/20/2025
 *
 */
package protocols;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * this is the receiver for Selective-and-Repeat ARQ protocol
 * This receiver uses a sliding window to receive packets and send ACKs/NAKs
 * It uses a list to store received packets and a list of flags to track received packets
 * The use of flags is to avoid duplicate packets
 * flags also helps keep track of the window base
 */

public class SelectiveAndRepeatARQ_Receiver {

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
    private int winBase; // points to the first packet in the window(no usages)

    /**
     * Default Constructor
     * @param port
     * @param winSize
     * @param outputFile
     */


    public SelectiveAndRepeatARQ_Receiver(int port, int winSize, String outputFile){
        this.port = port;
        this.outputFile = outputFile;
        this.running = false;
        this.receivedData = new ArrayList<>();
        this.totalPacketsReceived = 0;
    }

    /**
     * Ensure the capacity of the receivedData list
     * @param N
     */

    private void ensureCapacity(int N) {
        //this ensures that the receivedData list has a size of N+1
        while (receivedData.size() <= N) {
            receivedData.add(null);
        }
    }

    /**
     * Start the receiver
     * This method will listen on the port and receive packets
     * @throws IOException
     */

    public void start() throws IOException {
        // initializes and creates a server socket to listen on the port
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Receiver listening on port " + port);
        Socket clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        // only resend nak once for each lost packet
        Set<Integer> nak_packets = new HashSet<>();

        // Handshake
        int N = in.readInt();
        // sends ACK for the handshake
        int winSize = in.readInt();
        out.writeChar(ACK); // 1 is ACK
        out.writeChar(0); // any number should be good at this moment
        int winBase = 0;
        System.out.println("Receiver handshake, N: " + N + " winSize: " + winSize);
        Boolean[] flags = new Boolean[N]; // flags[i] indicate whether the packet i has been received
        Arrays.fill(flags, false);
        ensureCapacity(N);

        while(running){
            try{
                // read packet metadata
                int packetLength = in.readInt();
                char packetIndex = in.readChar();
                boolean isLastPacket = in.readBoolean();

                System.out.println("packetIndex : " + (int)(packetIndex) );

                // this reads the packet data
                byte[] packetData = new byte[packetLength];
                in.readFully(packetData);
                BISYNCPacket packet = new BISYNCPacket(packetData, true);

                if (packetIndex < 0 || packetIndex >= N) {
                    System.err.println("Invalid packet index: " + (int) packetIndex);
                    continue;
                }
                // checks the packet integrity using checksum
                if (packet.isValid()) {
                    // then convert circular sequence number to actual packet index

                    // this stores the packet if it's within the window
                    if (!flags[packetIndex]) {
                        receivedData.set(packetIndex, packet.getData());
                        flags[packetIndex] = true;
                        totalPacketsReceived++;

                        // Send ACK for this packet
                        out.writeByte(ACK);
                        out.writeChar(packetIndex);
                        out.flush();

                        // Update window base
                        while (winBase < N && flags[winBase]) {
                            winBase++;
                        }

                        // Check if all packets have been received
                        if (totalPacketsReceived == N || isLastPacket) {
                            boolean allReceived = true;
                            for (boolean flag : flags) {
                                if (!flag) {
                                    allReceived = false;
                                    break;
                                }
                            }
                            if (allReceived) {
                                running = false;
                            }
                        }
                    } else {
                        // Duplicate packet, send ACK anyway
                        out.writeByte(ACK);
                        out.writeChar(packetIndex);
                        out.flush();
                    }
                } else {
                    // Packet is corrupted, send NAK if not already sent for this packet
                    if (!nak_packets.contains((int)packetIndex)) {
                        out.writeByte(NAK);
                        out.writeChar(packetIndex);
                        out.flush();
                        nak_packets.add((int)packetIndex);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        }
        // finish receiving all the data
        System.out.println("receiver: finish receiving all packets, now save into file!");
        saveFile();
        stop();
    }

    /**
     * Save the received packets into a file
     * This method will combine all the received packets and write them to a file
     * The file path is specified in the outputFile field
     * If the file already exists, it will be overwritten
     * @throws IOException
     */
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
            // saveBytesAsIntegers(completeFile, "mytest3.log");
            // Write to file
            Files.write(Paths.get(outputFile), completeFile);
            System.out.println("Video file saved successfully: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error saving video file: " + e.getMessage());
        }
    }

    /**
     * Stop the receiver
     * This method will stop the receiver and close the server socket
     * @throws IOException
     */
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
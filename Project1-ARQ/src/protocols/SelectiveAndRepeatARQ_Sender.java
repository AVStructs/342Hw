/**
 * Author: Aiden Vangura, Joshua Blanks
 * Course: COMP 342 Data Communications and Networking
 * Date: 03/20/2025
 *
 */
package protocols;


import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * this is the sender for Selective-and-Repeat ARQ protocol
 * This sender uses a sliding window to send packets and receive ACKs/NAKs
 * It uses a set to store unacknowledged packets and a window base to track the window
 * The use of window base is to slide the window when the base packet is acknowledged
 * The sender sends packets within the window and waits for ACKs/NAKs
 * If an ACK is received, the sender slides the window
 * If a NAK is received, the sender resends the specific packet
 * The sender stops when all packets are acknowledged
 * The sender uses sendPacketWithLost for packet loss simulation
 */

public class SelectiveAndRepeatARQ_Sender {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private static final char MAX_SEQ_NUM = 255;
    private static final char TOTAL_SEQ_NUM = (MAX_SEQ_NUM+1);
    private final NetworkSender sender;
    private int winBase = 0;
    private int winSize = 0;

    /**
     * Default Constructor
     * @param sender
     * @param winSize
     */

    public SelectiveAndRepeatARQ_Sender(NetworkSender sender, int winSize){
        this.sender = sender;
        // Sliding window
        this.winBase = 0;
        this.winSize = winSize;
    }

    /**
     * Transmit packets using Selective-and-Repeat ARQ
     * The sender sends packets within the window and waits for ACKs/NAKs
     * If an ACK is received, the sender slides the window
     * If a NAK is received, the sender resends the specific packet
     * The sender stops when all packets are acknowledged
     * @param packets
     * @throws IOException
     */
    public void transmit(List<BISYNCPacket> packets) throws IOException {
        // Handshake
        int N = packets.size();
        sender.sendHandshakeRequest(N, winSize);
        char[] response = sender.waitForResponse();
        if(response[0] != ACK) {
            System.out.println("Handshake failed, exit");
            return;
        } else {
            System.out.println("Handshake succeed, proceed!");
        }

        Boolean finished = false;
        Set<Integer> unacknowledgedPackets = new HashSet<>();
        int nextSeqNum = 0;

        while(!finished) {
            try {
                // Send packets within window if available
                while (nextSeqNum < packets.size() &&
                        nextSeqNum < winBase + winSize) {
                    BISYNCPacket packet = packets.get(nextSeqNum);
                    packet.setSequenceNumber((char)(nextSeqNum % TOTAL_SEQ_NUM));

                    // Use sendPacket for last packet, sendPacketWithLost for others
                    if (nextSeqNum == packets.size() - 1) {
                        sender.sendPacket(packet);
                    } else {
                        if (!sender.sendPacketWithLost(packet, (char) nextSeqNum, false)) {
                            System.out.println("Sender: packetIndex " + nextSeqNum + " get lost");
                        }

                    }

                    unacknowledgedPackets.add(nextSeqNum);
                    nextSeqNum++;
                }

                // Wait for response
                char [] ackResponse = sender.waitForResponse();
                int receivedSeqNum = ackResponse[1];

                if (ackResponse[0] == ACK) {
                    // Handle ACK
                    unacknowledgedPackets.remove(receivedSeqNum);

                    // Slide window if base packet is acknowledged
                    while (!unacknowledgedPackets.contains(winBase) &&
                            winBase < packets.size()) {
                        winBase++;
                    }
                } else if (ackResponse[0] == NAK) {
                    // Handle NAK - resend the specific packet
                    if (receivedSeqNum < packets.size()) {
                        BISYNCPacket packet = packets.get(receivedSeqNum);
                        packet.setSequenceNumber((char)(receivedSeqNum % TOTAL_SEQ_NUM));
                        sender.sendPacket(packet); // Use sendPacket for retransmission
                    }
                }

                // Check if transmission is complete
                if (winBase >= packets.size() && unacknowledgedPackets.isEmpty()) {
                    finished = true;
                }

            } catch (IOException e) {
                System.err.println("Error transmitting packet: " + e.getMessage());
                throw e;
            }
        }
    }


}
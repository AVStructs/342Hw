package protocols;


import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectiveAndRepeatARQ_Sender {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private static final char MAX_SEQ_NUM = 255;
    private static final char TOTAL_SEQ_NUM = (MAX_SEQ_NUM+1);
    private final NetworkSender sender;
    private int winBase = 0;
    private int winSize = 0;

    public SelectiveAndRepeatARQ_Sender(NetworkSender sender, int winSize){
        this.sender = sender;
        // Sliding window
        this.winBase = 0;
        this.winSize = winSize;
    }

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
                        sender.sendPacketWithLost(packet, 'A', true);

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

package protocols;

import java.io.IOException;
import java.util.List;

public class StopAndWaitARQ_Sender {
    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private final NetworkSender sender;
    private char currSeqNumber = 0; // 0 - 255

    public StopAndWaitARQ_Sender(NetworkSender sender){
        this.sender = sender;
        this.currSeqNumber = 0;
    }

    public void transmit(List<BISYNCPacket> packets) throws IOException {
        for (int i = 0; i < packets.size(); i++) {
            BISYNCPacket packet = packets.get(i);
            boolean packetReceived = false;
            boolean isLastPacket = (i == packets.size() - 1);

            while (!packetReceived) {
                // Set the sequence number for the packet
                packet.setSequenceNumber(currSeqNumber);

                // Send the packet and wait for response
                sender.sendPacketWithError(packet, currSeqNumber, isLastPacket);
                byte[] response = sender.byteResponse();

                // Check if packet was received correctly
                if (response[0] == ACK) {
                    packetReceived = true;
                    // Update sequence number for next packet
                    currSeqNumber = (char)((currSeqNumber + 1) % 256);
                }
                // If NAK received or response corrupted, retry sending the same packet
            }
        }
    }



}

package ro.unitbv.pguzun.ntp.impl;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/***
 * The NTPUDPClient class is a UDP implementation of a client for the Network
 * Time Protocol (NTP) described in RFC 1305 as well as the Simple Network Time
 * Protocol (SNTP) in RFC-2030. To use the class, merely open a local datagram
 * socket with <a href="#open"> open </a> and call <a href="#getTime"> getTime
 * </a> to retrieve the time. Then call
 * <a href="org.apache.commons.net.DatagramSocketClient.html#close"> close </a>
 * to close the connection properly. Successive calls to <a href="#getTime">
 * getTime </a> are permitted without re-establishing a connection. That is
 * because UDP is a connectionless protocol and the Network Time Protocol is
 * stateless.
 * 
 * @author Jason Mathews, MITRE Corp
 * @version $Revision: 1299238 $
 ***/

public final class NTPUDPClient {
    /*** The default NTP port. It is set to 123 according to RFC 1305. ***/
    public static final int DEFAULT_PORT = 123;

    private int _version = NtpV3Packet.VERSION_3;

    /***
     * Retrieves the time information from the specified server and port and returns
     * it. The time is the number of miliiseconds since 00:00 (midnight) 1 January
     * 1900 UTC, as specified by RFC 1305. This method reads the raw NTP packet and
     * constructs a <i>TimeInfo</i> object that allows access to all the fields of
     * the NTP message header.
     * <p>
     * 
     * @param host The address of the server.
     * @param port The port of the service.
     * @return The time value retrieved from the server.
     ***/
    public TimeInfo getTime(InetAddress host, int port) throws Exception {
        DatagramSocket socket = socket();

        NtpV3Packet message = new NtpV3Impl();
        message.setMode(NtpV3Packet.MODE_CLIENT);
        message.setVersion(_version);
        DatagramPacket sendPacket = message.getDatagramPacket();
        sendPacket.setAddress(host);
        sendPacket.setPort(port);

        NtpV3Packet recMessage = new NtpV3Impl();
        DatagramPacket receivePacket = recMessage.getDatagramPacket();

        /*
         * Must minimize the time between getting the current time, timestamping the
         * packet, and sending it out which introduces an error in the delay time. No
         * extraneous logging and initializations here !!!
         */
        TimeStamp now = TimeStamp.getCurrentTime();

        // Note that if you do not set the transmit time field then originating time
        // in server response is all 0's which is "Thu Feb 07 01:28:16 EST 2036".
        message.setTransmitTime(now);

        socket.send(sendPacket);
        socket.receive(receivePacket);

        long returnTime = System.currentTimeMillis();
        // create TimeInfo message container but don't pre-compute the details yet
        TimeInfo info = new TimeInfo(recMessage, returnTime, false);

        return info;
    }

    /***
     * Retrieves the time information from the specified server on the default NTP
     * port and returns it. The time is the number of miliiseconds since 00:00
     * (midnight) 1 January 1900 UTC, as specified by RFC 1305. This method reads
     * the raw NTP packet and constructs a <i>TimeInfo</i> object that allows access
     * to all the fields of the NTP message header.
     * <p>
     * 
     * @param host The address of the server.
     * @return The time value retrieved from the server.
     ***/
    public TimeInfo getTime(InetAddress host) throws Exception {
        return getTime(host, NtpV3Packet.NTP_PORT);
    }

    /***
     * Returns the NTP protocol version number that client sets on request packet
     * that is sent to remote host (e.g. 3=NTP v3, 4=NTP v4, etc.)
     * 
     * @return the NTP protocol version number that client sets on request packet.
     * @see #setVersion(int)
     ***/
    public int getVersion() {
        return _version;
    }

    /***
     * Sets the NTP protocol version number that client sets on request packet
     * communicate with remote host.
     * 
     * @param version the NTP protocol version number
     ***/
    public void setVersion(int version) {
        _version = version;
    }

    private DatagramSocket socket() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000);
        return socket;
    }

    public static void main(String[] args) throws Exception {
        NTPUDPClient client = new NTPUDPClient();
        for (int i = 0; i < 2000; i++) {
            TimeInfo time = client.getTime(InetAddress.getByName("ntp.org"), 123);
            time.computeDetails();
            System.out.println(time.getMessage());
        }
    }
}
package ro.unitbv.pguzun.ntp.impl;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;


public final class NTPUDPClient {
    public static final int DEFAULT_PORT = 123;

    private int _version = NtpV3Packet.VERSION_3;

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

    public TimeInfo getTime(InetAddress host) throws Exception {
        return getTime(host, NtpV3Packet.NTP_PORT);
    }

    public int getVersion() {
        return _version;
    }

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
        for (int i = 0; i < 20; i++) {
            TimeInfo time = client.getTime(InetAddress.getByName("ntp.org"), 123);
            final LocalDateTime localTime = LocalDateTime.now(ZoneId.of("UTC"));
            time.computeDetails();
            System.out.print(time.getMessage());
            System.out.println(DateTimeFormatter.ISO_DATE_TIME.format(localTime));
        }
    }
}
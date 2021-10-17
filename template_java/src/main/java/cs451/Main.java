package cs451;

import cs451.packets.AckPacket;
import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.*;

import static cs451.Log.*;


public class Main {

    /*
     DATA
     */
    final static ScheduledExecutorService exec =
            Executors.newScheduledThreadPool(
                    Runtime.getRuntime().availableProcessors() - 1 /* `-1` because of main thread */);

    final static PeerMsgTbl<ScheduledFuture<?>> ackSyncTbl = new PeerMsgTbl<>();

    private static final int BUF_SZ = 128;
    private static DatagramSocket globalSocket = null;

    /*
     FUNCTIONALITY
     */
    private static void handleSignal() {
        // TODO: terminate on apt signals
        // TODO: flush output

        //immediately stop network packet processing
        warn("Immediately stopping network packet processing.");

        //write/flush output file if necessary
        warn("Writing output.");

        if (globalSocket != null) globalSocket.close(); // TODO do a proper clean-up
        exec.shutdownNow();
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::handleSignal));
    }

    /*
     * Invariant: Must not throw since runs in the executor
     *
     * We handle two types of packets, ie all kinds of packets that exist
     * 1. AckPacket
     *   A peer is sending an ack for the packet that we sent,
     *   so we should stop resending it
     * 2. MessagePacket
     *   A peer sent us a message packet, so we try sending an ack for it to that peer
     *
     * TODO: handle "delivery"
     * */
    private static void processIncomingPacket(Object packet, DatagramSocket socket, InetAddress fromIP, int fromPort) {
        try {
            trace("processIncomingPacket", "received " + packet);
            if (packet instanceof AckPacket) {
                // stop infinite resending of the packet, since received an ack for it
                var ackPacket = (AckPacket) packet;
                var infResend = ackSyncTbl.get(fromPort, ackPacket.id);
                if (infResend != null) {
                    infResend.cancel(false);
                }
            } else if (packet instanceof MessagePacket) {
                var msgPacket = (MessagePacket) packet;
                // try sending an ack once,
                // if not successful - give up (because the sender will keep sending the message packet until it gets an ack
                // TODO: can I optimize byte[] use ?
                var ackPacket = new AckPacket((msgPacket.id));
                var b = PacketCodec.convertToBytes(new AckPacket(msgPacket.id));
                try {
                    socket.send(new DatagramPacket(b, b.length, fromIP, fromPort));
                    trace("processIncomingRequests", "sending an ack: " + ackPacket);
                } catch (IOException e) {
                    warn("couldn't send ack to " +
                            fromIP.toString() + ":" + fromPort + " " + ", but not resending", e);
                }
            } else {
                assert false; /* a packet MUST be either AckPacket or MessagePacket} */
            }
        } catch (Exception e) {
            error("processing incoming packet", e);
        }
    }

    /*
     * Listen to a socket and submit tasks to schedule incoming packets
     * */
    private static void listenToAndHandleIncomingPackets(DatagramSocket socket) {
        byte[] recvBuf = new byte[BUF_SZ];
        DatagramPacket inputPacket = new DatagramPacket(recvBuf, recvBuf.length);
        info("Starting to wait for a client to connect...");

        while (true) {
            try {
                socket.receive(inputPacket);

                var packet = PacketCodec.convertFromBytes(
                        Arrays.copyOfRange(recvBuf, 0, inputPacket.getLength()));

                exec.submit(() -> processIncomingPacket(packet, socket,
                        inputPacket.getAddress(),
                        inputPacket.getPort()));
            } catch (IOException | ClassNotFoundException e) {
                error("receive", e);
            }
        }
    }

    public static void main(String[] args) throws UnknownHostException, SocketException {
        Log.TRACE();

        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        long pid = ProcessHandle.current().pid();
        trace("PID: " + pid + "\n");
        info("From a new terminal type `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "` to " + "stop processing packets\n");

        trace("List of resolved hosts is:");
        for (Host host : parser.hosts()) {
            trace("\t" + host.getId() + " | " + host.getIp() + ":" + host.getPort());
        }

        trace("Path to output: " + parser.output());
        trace("Path to config " + parser.config());

        var hosts = parser.hosts();

        // resolve myAddr, myPort, myPeers
        var myPeers = new HashMap<Integer, Node>(hosts.size() - 1);
        InetAddress myAddr = null;
        int myPort = -1;
        for (var host : hosts) {
            var hostId = host.getId();
            var hostAddr = InetAddress.getByName(host.getIp());
            var hostPort = host.getPort();
            if (parser.myId() == host.getId()) {
                myAddr = hostAddr;
                myPort = hostPort;
            } else {
                myPeers.put(hostId, new Node(hostId, hostAddr, hostPort));
            }
        }
        if (myPort == -1) {throw new RuntimeException("'hosts' file doesn't include given ID " + parser.myId());}

        final var myNode = new MyNode(new Node(parser.myId(), myAddr, myPort), myPeers);
        info(myNode.toString());

        final var socket = new DatagramSocket(myNode.me.port, myNode.me.addr);
        globalSocket = socket;

        exec.submit(() -> sendByPerfectLinksConfig(socket, parser, myNode));

        listenToAndHandleIncomingPackets(socket);
    }

    private static ConfigParser.PerfectLinksConfig[] parsePerfectLinksConfig(Parser parser) {
        var configParser = new ConfigParser();
        configParser.populate(parser.config());

        try {
            return configParser.parsePerfectLinks();
        } catch (Exception e) {
            error("error parsing perfect links config file", e);
            return null;
        }
    }

    private static void sendByPerfectLinksConfig(DatagramSocket socket, Parser parser, MyNode myNode) {
        try {
            var configs = parsePerfectLinksConfig(parser);
            assert configs != null;
            trace(Arrays.toString(configs));

            for (var config : configs) {
                final var firstMsgId = myNode.msgUid.get();
                for (int msgId = firstMsgId;
                     msgId < firstMsgId + config.nMessages;
                     msgId = myNode.msgUid.incrementAndGet()) {
                    var msgPacket = new MessagePacket(msgId, String.valueOf(msgId));
                    var outBuf = PacketCodec.convertToBytes(msgPacket);
                    var peerReceiver = myNode.peers.get(config.hostIdx);
                    var outPacket = new DatagramPacket(outBuf, 0, outBuf.length, peerReceiver.addr, peerReceiver.port);
                    exec.submit(() -> sendPacketUntilAck(msgPacket, socket, outPacket, 1000)); // FIXME: timeout needs to be fixed
                }
            }
        } catch (Exception e) {
            error("", e);
        }
    }

    /*
     * Invariant: do not raise
     * */
    private static void sendPacketUntilAck(MessagePacket msgPacket,
                                           DatagramSocket socket,
                                           DatagramPacket outPacket,
                                           int timeoutMs) {
        try {
            trace("sendPacketUntilAck", "sending " + msgPacket + " to :" + outPacket.getPort());
            sendPacketOrFailSilently(socket, outPacket);

            var infResend = exec.schedule(
                    () -> sendPacketUntilAck(msgPacket, socket, outPacket, timeoutMs),
                    timeoutMs, TimeUnit.MILLISECONDS);

            ackSyncTbl.set(outPacket.getPort(), msgPacket.id, infResend);
        } catch (Throwable e) {
            error("couldn't send a message packet from perfect links config", e);
        }
    }

    private static void sendPacketOrFailSilently(DatagramSocket socket, DatagramPacket outPacket) {
        try {
            socket.send(outPacket);
        } catch (Throwable e) {
            // any failure in sending a packet should be logged,
            // but since we have infinite resend, we don't do anything about the failure
            error("couldn't send a message packet from perfect links config", e);
        }
    }
}

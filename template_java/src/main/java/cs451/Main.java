package cs451;

import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static cs451.Log.*;


public class Main {

    /*
     DATA
     */
    final static ScheduledExecutorService exec =
            Executors.newScheduledThreadPool(
                    Runtime.getRuntime().availableProcessors() - 1 /* `-1` because of main thread */);

    final static ConcurrentLinkedQueue<String> eventLog = new ConcurrentLinkedQueue<>();

    static String outputFilePath; // must be set by main()
    private static DatagramSocket globalSocket = null; // must be set by main()

    /*
     FUNCTIONALITY
     */
    private static void handleSignal() {
        warn("Immediately stopping network packet processing.");
        warn("Writing output.");

        exec.shutdownNow();

        if (globalSocket != null) globalSocket.close();

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));
            for (String s : eventLog) {
                writer.write(s);
                writer.write('\n');
            }
            writer.flush();
            writer.close();
        } catch (Exception e) {
            error("flushing to file", e);
        }
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::handleSignal));
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

        outputFilePath = parser.output();

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

        var link = new PerfectLinkUdp(socket, exec);
        link.registerOnDeliverCallback(
                messagePacket -> eventLog.add("d " + messagePacket.senderId + " " + messagePacket.id));

        exec.submit(() -> sendByPerfectLinksConfig(link, parser, myNode));

        link.listenToAndHandleIncomingPackets(); // beware: blocks the thread
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

    private static void sendByPerfectLinksConfig(PerfectLinkUdp link, Parser parser, MyNode myNode) {
        try {
            var configs = parsePerfectLinksConfig(parser);
            assert configs != null;
            trace(Arrays.toString(configs));

            for (var config : configs) {
                if (config.hostId == myNode.me.id)
                    continue; // don't send to myself

                final var firstMsgId = myNode.msgUid.get();
                for (int msgId = firstMsgId;
                     msgId < firstMsgId + config.nMessages;
                     msgId = myNode.msgUid.incrementAndGet()) {
                    var msgPacket = new MessagePacket(myNode.me.id, msgId, String.valueOf(msgId));
                    var outBuf = PacketCodec.convertToBytes(msgPacket);
                    var peerReceiver = myNode.peers.get(config.hostId);
                    var outPacket = new DatagramPacket(outBuf, 0, outBuf.length, peerReceiver.addr, peerReceiver.port);
                    var event = "b " + msgId;
                    exec.submit(() -> {
                        eventLog.add(event);
                        link.sendPacketAndScheduleResend(msgPacket, outPacket, 100);
                    }); // FIXME: timeout needs to be fixed
                }
            }
        } catch (Exception e) {
            error("", e);
        }
    }


}

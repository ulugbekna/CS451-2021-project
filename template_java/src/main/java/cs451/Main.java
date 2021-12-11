package cs451;

import cs451.packets.MessagePacket;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static cs451.Log.*;

public class Main {
    /*
     * CONSTANTS
     * */
    final static int THREAD_POOL_SZ = 1024; // TODO: too many -- what can we do about this?

    /*
     DATA
     */
    final static long executionStartTime = System.nanoTime();
    final static ScheduledThreadPoolExecutor exec =
            new ScheduledThreadPoolExecutor(THREAD_POOL_SZ);

    final static ConcurrentLinkedQueue<String> eventLog = new ConcurrentLinkedQueue<>();

    static String outputFilePath; // must be set by main()
    private static DatagramSocket globalSocket = null; // must be set by main()

    /*
     FUNCTIONALITY
     */

    // FIXME: make sure this works as expected
    private static void handleSignal() {
        exec.shutdownNow();

        if (globalSocket != null) globalSocket.close();

        try {
            long startTime = System.nanoTime();
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));
            for (String s : eventLog) {
                writer.write(s);
                writer.write('\n');
            }
            writer.flush();
            long endTime = System.nanoTime();
            info("Writing to output file time in milliseconds: " + (endTime - startTime) / 1000000);
            info("Execution time in milliseconds: " + (System.nanoTime() - executionStartTime) / 1000000);

            writer.close();
        } catch (Exception e) {
            error("flushing to file", e);
        }
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::handleSignal));
    }

    public static void main(String[] args) throws IOException {
        Log.NONE();

        Parser parser = new Parser(args);
        parser.parse();
        var configParser = new ConfigParser();
        configParser.populate(parser.config());
        var nMsgsToBroadcast = configParser.parseFifoBroadcastConfig();

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

        var fifob = new FifoBroadcast(
                myNode.me.id,
                myNode.peers,
                socket,
                exec,
                (MessagePacket m) ->
                        eventLog.add("d " + m.authorId + " " + m.messageId));

        exec.submit(() -> {
            for (int i = 1; i <= nMsgsToBroadcast; ++i) {
                eventLog.add("b " + i);
                var msg = new MessagePacket(myNode.me.id, i, myNode.me.id, String.valueOf(i));
                fifob.broadcast(msg);
            }
        });

        fifob.blockingListen();
    }
}

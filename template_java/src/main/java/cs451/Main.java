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

import static cs451.Constants.THREAD_POOL_SZ;
import static cs451.Log.*;

public class Main {
    /*
     * CONSTANTS
     * */

    /*
     DATA
     */

    // to measure how much whole program execution took
    final static Bench wholeProgramExecution = new Bench();

    // create thread pool
    final static ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(THREAD_POOL_SZ);

    // log "broadcast" and "deliver" events
    final static ConcurrentLinkedQueue<String> eventLog = new ConcurrentLinkedQueue<>();

    // path to the file to which events are written
    static String outputFilePath; // must be set by main()

    private static DatagramSocket globalSocket; // must be set by main()

    /*
     FUNCTIONALITY
     */

    private static void handleSignal() {
        exec.shutdownNow();

        if (globalSocket != null) globalSocket.close();

        try {
            var writingEventLogToFile = new Bench();
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));
            for (String s : eventLog) {
                writer.write(s);
                writer.write('\n');
            }
            writer.flush();
            writer.close(); // TODO: should be done in `finally` ?

            info("Writing to output file time in milliseconds: " + writingEventLogToFile.timeElapsedMS());

            info("Program execution time in milliseconds: " + wholeProgramExecution.timeElapsedMS());
        } catch (Exception e) {
            error("flushing to file", e);
        }
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::handleSignal));
    }

    public static void main(String[] args) throws IOException {
        /* START: Program state initialization */
        Log.TRACE();

        initSignalHandlers();

        exec.setRemoveOnCancelPolicy(true);

        Parser parser = new Parser(args);
        parser.parse();
        var configParser = new ConfigParser();
        configParser.populate(parser.config());

        long pid = ProcessHandle.current().pid();
        trace("PID: " + pid + "\n");
        info("From a new terminal type `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "` to " + "stop processing packets\n");

        outputFilePath = parser.output();

        trace("Path to output: " + outputFilePath);
        trace("Path to config: " + parser.config());

        var hosts = parser.hosts();

        trace("List of resolved hosts is:");
        for (Host host : hosts) {
            trace("\t" + host.getId() + " | " + host.getIp() + ":" + host.getPort());
        }

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

        /* END: Program state initialization */

        /* START: Program actions */
        var nMsgsToBroadcast = configParser.parseFifoBroadcastConfig();

        var urb = new UniformReliableBroadcastUdp(myNode.me.id, myPeers, socket, exec,
                (m) -> eventLog.add("d " + m.authorId + " " + m.messageId));

        exec.submit(() -> {
            for (int i = 1; i <= nMsgsToBroadcast; ++i) {
                eventLog.add("b " + i);
                var msg = new MessagePacket(myNode.me.id, i, myNode.me.id, String.valueOf(i));
                urb.broadcast(msg);
            }
        });

        urb.blockingListen();

        /* END: Program actions */
    }
}

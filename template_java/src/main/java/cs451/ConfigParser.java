package cs451;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class ConfigParser {

    private String path;

    public boolean populate(String value) {
        File file = new File(value);
        path = file.getPath();
        return true;
    }

    public String getPath() {
        return path;
    }

    /*
     * Config file format expected - A file containing a single integer -
     * the number of messages to broadcast
     * */
    public int parseFifoBroadcastConfig() throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(path));
        var fstLine = r.readLine();
        return Integer.parseInt(fstLine);
    }

    public PerfectLinksConfig[] parsePerfectLinks() throws IOException {
        var configFilePath = Paths.get(path);
        var lines = Files.lines(configFilePath);
        var configStream = lines.map((s) -> {
            var ns = s.split(" ");
            assert ns.length == 2;
            var nMessages = Integer.parseInt(ns[0]);
            var hostIdx = Integer.parseInt(ns[1]);
            return new PerfectLinksConfig(nMessages, hostIdx);
        });
        var arr = configStream.toArray(PerfectLinksConfig[]::new);
        lines.close();
        return arr;
    }

    public LCBConfig parseLCBConfig() throws IOException {
        var configFilePath = Paths.get(path);
        try (var lines_strm = Files.lines(configFilePath)) {
            var lines = lines_strm.collect(Collectors.toList());

            var nMsgsToBroadcast = Integer.parseInt(lines.get(0));

            var nProcs = lines.size() - 1;

            var procCausality = new ProcArray<int[]>(nProcs);
            for (int i = 1; i <= nProcs; ++i) {
                var line = lines.get(i);
                var nums = line.split(" ");
                var procId = Integer.parseInt(nums[0]);
                if (nums.length == 1) {
                    procCausality.setById(procId, new int[0]);
                } else {
                    var causalProcIds = new int[nums.length - 1];
                    for (int j = 1; j < nums.length; ++j) {
                        causalProcIds[j - 1] = Integer.parseInt(nums[j]);
                    }
                    procCausality.setById(procId, causalProcIds);
                }
            }

            return new LCBConfig(nMsgsToBroadcast, procCausality);
        }
    }

    protected static class PerfectLinksConfig {
        public final int nMessages;
        public final int hostId;

        public PerfectLinksConfig(int nMessages, int hostId) {
            this.nMessages = nMessages;
            this.hostId = hostId;
        }

        @Override
        public String toString() {
            return "PerfectLinksConfig{" + "nMessages=" + nMessages + ", hostId=" + hostId + '}';
        }
    }

    protected static class LCBConfig {
        public final int nMsgsToBroadcast;
        public final ProcArray<int[]> procCausality;

        public LCBConfig(int nMsgsToBroadcast, ProcArray<int[]> procCausality) {
            this.nMsgsToBroadcast = nMsgsToBroadcast;
            this.procCausality = procCausality;
        }

        @Override
        public String toString() {
            return "LCBConfig{" +
                    "nMsgsToBroadcast=" + nMsgsToBroadcast +
                    ", procCausality=" + procCausality +
                    '}';
        }
    }
}

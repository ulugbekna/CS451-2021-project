package cs451;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
}

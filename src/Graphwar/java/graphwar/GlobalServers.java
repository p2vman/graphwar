package graphwar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GlobalServers {
    public static class ServerEntry {
        public final String name;
        public final String host;
        public final int port;

        public ServerEntry(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return name + " (" + host + ":" + port + ")";
        }
    }

    public static List<ServerEntry> load() {
        List<ServerEntry> list = new ArrayList<>();

        String override = System.getProperty("global.servers.file");
        if (override != null) {
            File fo = new File(override);
            if (fo.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(fo))) {
                    loadFromReader(br, list);
                } catch (Exception e) { e.printStackTrace(); }
                if (!list.isEmpty()) return list;
            }
        }

        File f = new File("global_servers.txt");
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                loadFromReader(br, list);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!list.isEmpty()) return list;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(GlobalServers.class.getResourceAsStream("/global_servers.txt")))) {
            if (br != null) loadFromReader(br, list);
        } catch (Exception e) {

        }

        if (list.isEmpty()) {
            list.add(new ServerEntry("Default", graphwar.graphserver.Constants.GLOBAL_IP, graphwar.graphserver.Constants.GLOBAL_PORT));
        }

        return list;
    }

    private static void loadFromReader(BufferedReader br, List<ServerEntry> list) throws Exception {
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;

            String[] parts = line.split(",");
            if (parts.length >= 2) {
                String name = parts[0].trim();
                String hostPart = parts[1].trim();
                int port = graphwar.graphserver.Constants.GLOBAL_PORT;
                String host = hostPart;
                if (hostPart.startsWith("ws://") || hostPart.startsWith("wss://") || hostPart.startsWith("http://") || hostPart.startsWith("https://")) {
                    try {
                        java.net.URI uri = new java.net.URI(hostPart);
                        String scheme = uri.getScheme();
                        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                            String wsScheme = "https".equalsIgnoreCase(scheme) ? "wss" : "ws";
                            java.net.URI wsUri = new java.net.URI(wsScheme, uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                            host = wsUri.toString();
                            if (wsUri.getPort() != -1) port = wsUri.getPort();
                        } else {
                            host = hostPart;
                            if (uri.getPort() != -1) port = uri.getPort();
                        }
                        if (uri.getPort() == -1 && parts.length >= 3) {
                            try { port = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException e) {}
                        }
                    } catch (Exception e) { }
                } else {
                    if (parts.length >= 3) {
                        try { port = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException e) {}
                    }
                }
                list.add(new ServerEntry(name, host, port));
            }
        }
    }
}

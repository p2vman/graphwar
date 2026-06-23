package graphwar.graphserver.commands;

import graphwar.graphserver.ClientConnection;
import graphwar.graphserver.GraphServer;
import graphwar.graphserver.NetworkProtocol;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class CommandContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandContext.class);
    @Getter
    private final ClientConnection client;
    @Getter
    private final GraphServer server;
    @Setter
    @Getter
    private boolean hidemessage = false;
    public CommandContext(@NonNull ClientConnection client, @NonNull  GraphServer server) {
        this.client = client;
        this.server = server;
    }

    public void sendMessage(String s) {
        try {
            String encoded = URLEncoder.encode(s, StandardCharsets.UTF_8.name());
            client.sendMessage(NetworkProtocol.CHAT_MSG + "&" + -1 + "&" + encoded);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Failed to encode message", e);
        }
    }
}

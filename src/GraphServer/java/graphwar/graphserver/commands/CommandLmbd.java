package graphwar.graphserver.commands;

import graphwar.graphserver.ClientConnection;
import graphwar.graphserver.GraphServer;

@FunctionalInterface
public interface CommandLmbd {
    int handle(IArguments arguments, String command, ClientConnection client, GraphServer server);
}

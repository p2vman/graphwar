package graphwar.graphserver.commands;

@FunctionalInterface
public interface CommandLmbd {
    int handle(CommandContext ctx, IArguments arguments, String command);
}

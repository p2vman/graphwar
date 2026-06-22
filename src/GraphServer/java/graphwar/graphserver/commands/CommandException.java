package graphwar.graphserver.commands;

public class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message);
    }

    public static class CommandNotFoundException extends CommandException {
        public CommandNotFoundException(String command) {
            super(command);
        }
    }

    public String build() {
        return getMessage();
    }
}

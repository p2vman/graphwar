package graphwar.graphserver.commands;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.NonNull;

import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Dispatcher {
    private final Object2ObjectMap<String, CommandLmbd> commands;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    public Dispatcher() {
        commands = new Object2ObjectOpenHashMap<>();
    }

    public void register(String name, @NonNull CommandLmbd lmbd) {
        lock.writeLock().lock();
        try {
            if (commands.containsKey(name)) return;
            commands.put(name, lmbd);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int handle(String input, @NonNull CommandContext ctx) throws CommandException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be empty");
        }
        String[] tokens = input.trim().split("\\s+");
        String command = tokens[0];
        String[] rawArgs = Arrays.copyOfRange(tokens, 1, tokens.length);
        lock.readLock().lock();
        try {
            if (!commands.containsKey(command)) throw new CommandException.CommandNotFoundException(command);
            return commands.get(command).handle(ctx, new ArgumentsImpl(ObjectList.of(rawArgs)), command);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int handleCommand(@NonNull String input, @NonNull CommandContext ctx) {
        try {
            if (input.startsWith("-")) {
                return handle(input.substring(1), ctx);
            }
        } catch (CommandException e) {
            ctx.sendMessage(e.build());
        }
        return 0;
    }
}

package graphwar.graphserver.commands;

import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Optional;

public class ArgumentsImpl implements IArguments {
    private final ObjectList<String> args;

    public ArgumentsImpl(ObjectList<String> args) {
        this.args = args;
    }

    private boolean hasIndex(int index) {
        return index >= 0 && index < args.size();
    }

    private void checkRequired(int index) throws CommandException {
        if (!hasIndex(index)) {
            throw new CommandException("Missing required argument at index " + index);
        }
    }

    @Override
    public String getString(int index) {
        checkRequired(index);
        return args.get(index);
    }

    @Override
    public Optional<String> optString(int index) {
        return hasIndex(index) ? Optional.of(args.get(index)) : Optional.empty();
    }

    @Override
    public String optString(int index, String defaultValue) {
        return hasIndex(index) ? args.get(index) : defaultValue;
    }

    @Override
    public int getInt(int index) {
        checkRequired(index);
        return Integer.parseInt(args.get(index));
    }

    @Override
    public Optional<Integer> optInt(int index) {
        if (!hasIndex(index)) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(args.get(index)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public int optInt(int index, int defaultValue) {
        return optInt(index).orElse(defaultValue);
    }

    @Override
    public double getDouble(int index) {
        checkRequired(index);
        return Double.parseDouble(args.get(index));
    }

    @Override
    public Optional<Double> optDouble(int index) {
        if (!hasIndex(index)) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(args.get(index)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public double optDouble(int index, double defaultValue) {
        return optDouble(index).orElse(defaultValue);
    }

    @Override
    public boolean getBoolean(int index) {
        checkRequired(index);
        String val = args.get(index).toLowerCase();
        return val.equals("true") || val.equals("1") || val.equals("yes");
    }

    @Override
    public Optional<Boolean> optBoolean(int index) {
        if (!hasIndex(index)) return Optional.empty();
        String val = args.get(index).toLowerCase();
        if (val.equals("true") || val.equals("1") || val.equals("yes")) return Optional.of(true);
        if (val.equals("false") || val.equals("0") || val.equals("no")) return Optional.of(false);
        return Optional.empty();
    }

    @Override
    public boolean optBoolean(int index, boolean defaultValue) {
        return optBoolean(index).orElse(defaultValue);
    }

    @Override
    public int size() {
        return args.size();
    }
}
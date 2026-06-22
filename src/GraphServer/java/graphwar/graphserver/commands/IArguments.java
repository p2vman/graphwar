package graphwar.graphserver.commands;


import java.util.Optional;

public interface IArguments {
    String getString(int index);
    Optional<String> optString(int index);
    String optString(int index, String defaultValue);

    int getInt(int index);
    Optional<Integer> optInt(int index);
    int optInt(int index, int defaultValue);

    double getDouble(int index);
    Optional<Double> optDouble(int index);
    double optDouble(int index, double defaultValue);

    boolean getBoolean(int index);
    Optional<Boolean> optBoolean(int index);
    boolean optBoolean(int index, boolean defaultValue);

    int size();
}
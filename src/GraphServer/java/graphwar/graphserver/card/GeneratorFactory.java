package graphwar.graphserver.card;

import graphwar.graphserver.Player;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Random;

public interface GeneratorFactory {
    CardGenerator create(Random random, ObjectList<Player> players);
}

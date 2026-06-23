package graphwar.graphserver;

import graphwar.graphserver.card.*;
import graphwar.graphserver.registry.Identifier;
import graphwar.graphserver.registry.Registry;
import graphwar.graphserver.registry.SimpleRegistry;

public class Registries {
    public static final Registry<GeneratorFactory> CARD_GENERATORS =
            new SimpleRegistry<>(Identifier.of(Identifier.DEFAULT_NAMESPACE, "card_generators"));

    static {
        Registry.register(CARD_GENERATORS, Identifier.tryParse("standart"), StandardCardGenerator::new);
        Registry.register(CARD_GENERATORS, Identifier.tryParse("spiral"), SpiralCardGenerator::new);
    }
}

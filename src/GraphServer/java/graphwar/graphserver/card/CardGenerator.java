package graphwar.graphserver.card;

import graphwar.graphserver.Player;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Interface for card/soldier generation strategies
 */
public interface CardGenerator
{
	/**
	 * Generate circles (obstacles) for the game map
	 * @return array of circles [x1, y1, radius1, x2, y2, radius2, ...]
	 */
	int[] generateCircles();

	/**
	 * Generate soldiers positions for all players
	 * @param circles array of circles from generateCircles()
	 * @return array of soldier positions [x1, y1, x2, y2, ...]
	 */
	int[] generateSoldiers(int[] circles, ObjectList<Player> players);
}

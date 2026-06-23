package graphwar.graphserver.card;

import graphwar.graphserver.Constants;
import graphwar.graphserver.Player;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

/**
 * Standard implementation of CardGenerator using Gaussian distribution
 */
public class StandardCardGenerator implements CardGenerator
{
	private final Random random;

	public StandardCardGenerator(Random random, ObjectList<Player> players)
	{
		this.random = random;

	}

	@Override
	public int[] generateCircles()
	{
		int numCircles = (int) (random.nextGaussian() * Constants.NUM_CIRCLES_STANDARD_DEVIATION + Constants.NUM_CIRCLES_MEAN_VALUE);

		if (numCircles < 1)
		{
			numCircles = 1;
		}

		int[] circles = new int[3 * numCircles];

		for (int i = 0; i < numCircles; i++)
		{
			circles[3 * i]     = random.nextInt(Constants.PLANE_LENGTH);
			circles[3 * i + 1] = random.nextInt(Constants.PLANE_HEIGHT);

            do {
                circles[3 * i + 2] = (int) (random.nextGaussian() * Constants.CIRCLE_STANDARD_DEVIATION + Constants.CIRCLE_MEAN_RADIUS);
            } while (circles[3 * i + 2] < 0);
		}

		return circles;
	}

	@Override
	public int[] generateSoldiers(int[] circles, ObjectList<Player> players)
	{
		List<Soldier> soldiers = new ArrayList<>();

        for (Player player : players) {
            for (int i = 0; i < player.getNumSoldiers(); i++) {
                Soldier soldier = generateSoldier(soldiers, circles, player.getTeam());
                soldiers.add(soldier);
            }
        }

		int[] soldiersPos = new int[soldiers.size() * 2];

		ListIterator<Soldier> sitr = soldiers.listIterator();
		int i = 0;
		while (sitr.hasNext())
		{
			Soldier tempSoldier = sitr.next();

			soldiersPos[2 * i]     = tempSoldier.x;
			soldiersPos[2 * i + 1] = tempSoldier.y;

			i++;
		}

		return soldiersPos;
	}

	private Soldier generateSoldier(List<Soldier> soldiers, int[] circles, int team)
	{
		Soldier soldier;

		do
		{
			int x = random.nextInt(Constants.PLANE_LENGTH / 2 - 2 * Constants.SOLDIER_RADIUS) + Constants.SOLDIER_RADIUS;
			int y = random.nextInt(Constants.PLANE_HEIGHT - 2 * Constants.SOLDIER_RADIUS) + Constants.SOLDIER_RADIUS;

			if (team == Constants.TEAM2)
			{
				x += Constants.PLANE_LENGTH / 2;
			}

			soldier = new Soldier(x, y);

		}
		while (!testSoldier(soldier, soldiers, circles));

		return soldier;
	}

	private boolean testSoldier(Soldier soldier, List<Soldier> soldiers, int[] circles)
	{

        for (Soldier tempSoldier : soldiers) {
            if (Math.abs(soldier.x - tempSoldier.x) < 20 && Math.abs(soldier.y - tempSoldier.y) < 20) {
                return false;
            }
        }

		int numCircles = circles.length / 3;
		for (int i = 0; i < numCircles; i++)
		{
			if (distance(soldier.x, soldier.y, circles[3 * i], circles[3 * i + 1]) < circles[3 * i + 2] + Constants.SOLDIER_SELECTION_RADIUS)
			{
				return false;
			}
		}

		return true;
	}

	private double distance(int x1, int y1, int x2, int y2)
	{
		return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
	}

	private static class Soldier
	{
		public int x;
		public int y;

		public Soldier(int x, int y)
		{
			this.x = x;
			this.y = y;
		}
	}
}

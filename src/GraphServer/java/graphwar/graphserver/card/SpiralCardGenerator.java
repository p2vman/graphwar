package graphwar.graphserver.card;

import graphwar.graphserver.Constants;
import graphwar.graphserver.Player;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

/**
 * Улучшенный спиральный генератор — создает динамичные,
 * играбельные препятствия с элементами случайности.
 */
public class SpiralCardGenerator implements CardGenerator
{
	private final Random random;
	private final ObjectList<Player> players;

	public SpiralCardGenerator(Random random, ObjectList<Player> players)
	{
		this.random = random;
		this.players = players;
	}

	@Override
	public int[] generateCircles()
	{
		int centerX = Constants.PLANE_LENGTH / 2;
		int centerY = Constants.PLANE_HEIGHT / 2;
		int numCircles = Constants.NUM_CIRCLES_MEAN_VALUE;

		// Немного рандомим общее количество кругов (±20%), чтобы карты отличались
		numCircles = (int) (numCircles * (0.9 + random.nextDouble() * 0.3));

		int[] circles = new int[3 * numCircles];

		// Параметры спирали
		double currentAngle = random.nextDouble() * Math.PI; // Случайный начальный поворот спирали
		double maxRadius = Math.min(Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT) * 0.45;

		for (int i = 0; i < numCircles; i++)
		{
			// Прогресс от центра к краю (от 0.0 до 1.0)
			double progress = (double) i / numCircles;

			// Шанс 15%, что круг "пропустится" — это создает естественные дыры и проходы в спирали
			if (i > 0 && i < numCircles - 1 && random.nextDouble() < 0.15) {
				// Просто смещаем угол и радиус дальше, но этот круг сделаем невидимым/крошечным на краю
				currentAngle += 0.2 + (0.3 * (1.0 - progress));
				circles[3 * i] = -1000;
				circles[3 * i + 1] = -1000;
				circles[3 * i + 2] = 1;
				continue;
			}

			// Угол закручивания: шаг угла уменьшается к краю, чтобы спираль расширялась красиво
			currentAngle += 0.25 + (0.35 * (1.0 - progress)) + (random.nextDouble() * 0.1 - 0.05);

			// Базовый радиус спирали + небольшое случайное смещение, чтобы круги не шли по идеальной линии
			double spiralRadius = (progress * maxRadius) + (random.nextDouble() * 30 - 15);

			int x = (int) (centerX + spiralRadius * Math.cos(currentAngle));
			int y = (int) (centerY + spiralRadius * Math.sin(currentAngle));

			// Вычисляем размер круга: в центре поменьше (чтобы не застревать), к краю крупнее
			int baseRadius = Constants.CIRCLE_MEAN_RADIUS;
			int circleRadius = (int) (baseRadius * (0.6 + progress * 0.8) + (random.nextInt(15) - 7));

			// Защита от слишком огромных или микроскопических кругов
			circleRadius = Math.max(15, Math.min(circleRadius, baseRadius + 15));

			// Ограничиваем координаты границами карты
			x = Math.max(circleRadius, Math.min(x, Constants.PLANE_LENGTH - circleRadius));
			y = Math.max(circleRadius, Math.min(y, Constants.PLANE_HEIGHT - circleRadius));

			circles[3 * i]     = x;
			circles[3 * i + 1] = y;
			circles[3 * i + 2] = circleRadius;
		}

		return circles;
	}

	@Override
	public int[] generateSoldiers(int[] circles, ObjectList<Player> players)
	{
		List<Soldier> soldiers = new ArrayList<Soldier>();

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
		int attempts = 0;

		do
		{
			int x = random.nextInt(Constants.PLANE_LENGTH / 2 - 2 * Constants.SOLDIER_RADIUS) + Constants.SOLDIER_RADIUS;
			int y = random.nextInt(Constants.PLANE_HEIGHT - 2 * Constants.SOLDIER_RADIUS) + Constants.SOLDIER_RADIUS;

			if (team == Constants.TEAM2)
			{
				x += Constants.PLANE_LENGTH / 2;
			}

			soldier = new Soldier(x, y);
			attempts++;

			// Защита от бесконечного цикла, если карта слишком плотная
			if (attempts > 500) {
				break;
			}
		}
		while (!testSoldier(soldier, soldiers, circles));

		return soldier;
	}

	private boolean testSoldier(Soldier soldier, List<Soldier> soldiers, int[] circles)
	{
		for (Soldier tempSoldier : soldiers) {
			if (Math.abs(soldier.x - tempSoldier.x) < 30 && Math.abs(soldier.y - tempSoldier.y) < 30) {
				return false;
			}
		}

		int numCircles = circles.length / 3;
		for (int i = 0; i < numCircles; i++)
		{
			// Пропускаем "удаленные" (пропущенные) круги
			if (circles[3 * i] < 0) continue;

			if (distance(soldier.x, soldier.y, circles[3 * i], circles[3 * i + 1]) < circles[3 * i + 2] + Constants.SOLDIER_SELECTION_RADIUS + 5)
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
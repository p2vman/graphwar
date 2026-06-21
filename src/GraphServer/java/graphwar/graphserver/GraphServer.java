//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//    
//  This file is part of Graphwar.
//
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  Graphwar is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with Graphwar.  If not, see <http://www.gnu.org/licenses/>.

package graphwar.graphserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class GraphServer implements Runnable
{
	private final int port;
	protected List<ClientConnection> clients;
	protected List<Player> players;
	protected boolean acceptingConnections;
	protected int gameMode;
	protected int gameState;
	private boolean countingDown;
	StartDelayer startDelayer;

	private long timeTurnStarted;

	private final static Random random = new SecureRandom();

	private NioEventLoopGroup bossGroup;
	private NioEventLoopGroup workerGroup;
	private Channel serverChannel;

	public GraphServer(int port) throws IOException
	{
		clients  = new ArrayList<ClientConnection>();
		players  = new ArrayList<Player>();

		this.port = port;

		gameMode  = Constants.NORMAL_FUNC;
		gameState = Constants.PRE_GAME;

		countingDown = false;
		startDelayer = null;

		acceptingConnections = true;
	}

	public GraphServer() throws IOException
	{
		this(0);
	}

	public void run()
	{
		acceptingConnections = true;

		bossGroup   = new NioEventLoopGroup(1);
		workerGroup = new NioEventLoopGroup();

		try
		{
			final GraphServer self = this;

			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>()
					{
						@Override
						protected void initChannel(SocketChannel ch)
						{
							ChannelPipeline p = ch.pipeline();

							p.addLast(new IdleStateHandler(
									Constants.TIMEOUT_KEEPALIVE, 0, 0,
									TimeUnit.MILLISECONDS));

							p.addLast(new LineBasedFrameDecoder(8192));
							p.addLast(new StringDecoder(StandardCharsets.UTF_8));
							p.addLast(new StringEncoder(StandardCharsets.UTF_8));

							p.addLast(new NettyServerHandler(self));
						}
					})
					.option(ChannelOption.SO_BACKLOG, 128)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.TCP_NODELAY, true);

			ChannelFuture f = b.bind(port).sync();
			serverChannel = f.channel();

			serverChannel.closeFuture().sync();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		finally
		{
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	public int getPort()
	{
		if (serverChannel != null && serverChannel.localAddress() instanceof java.net.InetSocketAddress)
		{
			return ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
		}
		return this.port;
	}

	private void stopListening()
	{
		acceptingConnections = false;
		if (serverChannel != null)
		{
			serverChannel.close();
		}
	}

	private void restartListening()
	{
		acceptingConnections = true;
		new Thread(this).start();
	}

	public void finalize()
	{
		String message = NetworkProtocol.DISCONNECT + "";
		sendMessageAll(message);

		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();
			client.disconnect();
		}

		stopListening();
	}

	public synchronized void addClient(ClientConnection client)
	{
		if (!acceptingConnections)
		{
			String message = NetworkProtocol.GAME_FULL + "";
			client.sendMessage(message);
			client.disconnect();
			return;
		}

		if (clients.size() < Constants.MAX_CLIENTS)
		{
			if (clients.size() == 0)
			{
				client.setLeader(true);
			}

			clients.add(client);

			sendAllInfoMessage(client);

			if (client.isLeader())
			{
				sendLeaderMessage(client);
			}
		}
		else
		{
			String message = NetworkProtocol.GAME_FULL + "";
			client.sendMessage(message);
			client.disconnect();
		}
	}

	private void sendMessageAll(String message)
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();
			client.sendMessage(message);
		}
	}

	private void sendLeaderMessage(ClientConnection client)
	{
		String message = NetworkProtocol.NEW_LEADER + "";
		client.sendMessage(message);
	}

	private void sendAllInfoMessage(ClientConnection client)
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection tmpClient = itr.next();

			List<Player> players = tmpClient.getPlayers();

			ListIterator<Player> pitr = players.listIterator();

			while (pitr.hasNext())
			{
				Player player = pitr.next();

				String message;

				int local = 0;
				if (client == tmpClient)
				{
					local = 1;
				}

				int ready = 0;
				if (player.getReady() == true)
				{
					ready = 1;
				}

				message = NetworkProtocol.ADD_PLAYER + "&" + player.getID() + "&" + player.getName() + "&" + player.getTeam() + "&" + local + "&" + player.getNumSoldiers() + "&" + ready;

				client.sendMessage(message);
			}
		}

		String message = NetworkProtocol.SET_MODE + "&" + gameMode;
		client.sendMessage(message);
	}

	protected void sendAddPlayerMessage(Player player, ClientConnection playerFrom)
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();

			String message;

			int local = 0;
			if (client == playerFrom)
			{
				local = 1;
			}

			int ready = 0;
			if (player.getReady() == true)
			{
				ready = 1;
			}

			message = NetworkProtocol.ADD_PLAYER + "&" + player.getID() + "&" + player.getName() + "&" + player.getTeam() + "&" + local + "&" + player.getNumSoldiers() + "&" + ready;

			client.sendMessage(message);
		}
	}

	private boolean setTeam(int team, int playerID, ClientConnection client)
	{
		List<Player> players = client.getPlayers();

		ListIterator<Player> itr = players.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			if (player.getID() == playerID)
			{
				player.setTeam(team);
				return true;
			}
		}

		if (client.isLeader())
		{
			ListIterator<ClientConnection> citr = clients.listIterator();

			while (citr.hasNext())
			{
				ClientConnection tmpClient = citr.next();

				List<Player> tmpPlayers = tmpClient.getPlayers();

				ListIterator<Player> pitr = tmpPlayers.listIterator();

				while (pitr.hasNext())
				{
					Player player = pitr.next();

					if (player.getID() == playerID)
					{
						player.setTeam(team);
						return true;
					}
				}
			}
		}

		return false;
	}

	protected boolean removePlayer(int playerID, ClientConnection client)
	{
		List<Player> clientPlayers = client.getPlayers();

		ListIterator<Player> itr = clientPlayers.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			if (player.getID() == playerID)
			{
				client.removePlayer(player);
				players.remove(player);
				return true;
			}
		}

		if (client.isLeader())
		{
			ListIterator<ClientConnection> citr = clients.listIterator();

			while (citr.hasNext())
			{
				ClientConnection tmpClient = citr.next();

				List<Player> tmpPlayers = tmpClient.getPlayers();

				ListIterator<Player> pitr = tmpPlayers.listIterator();

				while (pitr.hasNext())
				{
					Player player = pitr.next();

					if (player.getID() == playerID)
					{
						tmpClient.removePlayer(player);
						players.remove(player);
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean setReady(int playerID, ClientConnection client, boolean ready)
	{
		List<Player> players = client.getPlayers();

		ListIterator<Player> itr = players.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			if (player.getID() == playerID)
			{
				player.setReady(ready);

				if (ready == false)
				{
					if (startDelayer != null)
					{
						startDelayer.stop();
					}
				}

				return true;
			}
		}

		return false;
	}

	private boolean addSoldier(int playerID, ClientConnection client)
	{
		List<Player> players = client.getPlayers();

		ListIterator<Player> itr = players.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			if (player.getID() == playerID)
			{
				int newNum = player.getNumSoldiers() + 1;

				if (newNum <= Constants.MAX_SOLDIERS_PER_PLAYER)
				{
					player.setNumSoldiers(newNum);
					return true;
				}

				break;
			}
		}

		if (client.isLeader())
		{
			ListIterator<ClientConnection> citr = clients.listIterator();

			while (citr.hasNext())
			{
				ClientConnection tmpClient = citr.next();

				players = tmpClient.getPlayers();

				ListIterator<Player> pitr = players.listIterator();

				while (pitr.hasNext())
				{
					Player player = pitr.next();

					if (player.getID() == playerID)
					{
						int newNum = player.getNumSoldiers() + 1;

						if (newNum <= Constants.MAX_SOLDIERS_PER_PLAYER)
						{
							player.setNumSoldiers(newNum);
							return true;
						}

						break;
					}
				}
			}
		}

		return false;
	}

	private boolean removeSoldier(int playerID, ClientConnection client)
	{
		List<Player> players = client.getPlayers();

		ListIterator<Player> itr = players.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			if (player.getID() == playerID)
			{
				int newNum = player.getNumSoldiers() - 1;

				if (newNum >= 0)
				{
					player.setNumSoldiers(newNum);
					return true;
				}

				break;
			}
		}

		if (client.isLeader())
		{
			ListIterator<ClientConnection> citr = clients.listIterator();

			while (citr.hasNext())
			{
				ClientConnection tmpClient = citr.next();

				players = tmpClient.getPlayers();

				ListIterator<Player> pitr = players.listIterator();

				while (pitr.hasNext())
				{
					Player player = pitr.next();

					if (player.getID() == playerID)
					{
						int newNum = player.getNumSoldiers() - 1;

						if (newNum >= 0)
						{
							player.setNumSoldiers(newNum);
							return true;
						}

						break;
					}
				}
			}
		}

		return false;
	}

	private void setEveryoneNotReady()
	{
		if (startDelayer != null)
		{
			startDelayer.stop();
		}

		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection tmpClient = itr.next();

			List<Player> players = tmpClient.getPlayers();

			ListIterator<Player> pitr = players.listIterator();

			while (pitr.hasNext())
			{
				Player player = pitr.next();

				if (player.getReady() == true)
				{
					player.setReady(false);

					String message;
					message = NetworkProtocol.SET_READY + "&" + player.getID() + "&" + 0;

					sendMessageAll(message);
				}
			}
		}
	}

	private boolean checkPlayer(int playerID, ClientConnection client)
	{
		List<Player> players = client.getPlayers();

		ListIterator<Player> itr = players.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			if (player.getID() == playerID)
			{
				return true;
			}
		}

		return false;
	}

	protected void sendModeMessage()
	{
		String message = NetworkProtocol.SET_MODE + "&" + gameMode;
		sendMessageAll(message);
	}

	private boolean checkAllReady()
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection tmpClient = itr.next();

			List<Player> players = tmpClient.getPlayers();

			ListIterator<Player> pitr = players.listIterator();

			while (pitr.hasNext())
			{
				Player player = pitr.next();

				if (player.getReady() == false)
				{
					return false;
				}
			}
		}

		return true;
	}

	public void removeClient(ClientConnection client)
	{
		this.clients.remove(client);

		List<Player> clientPlayers = client.getPlayers();

		ListIterator<Player> itr = clientPlayers.listIterator();

		while (itr.hasNext())
		{
			Player player = itr.next();

			String message = NetworkProtocol.REMOVE_PLAYER + "&" + player.getID();
			sendMessageAll(message);

			players.remove(player);
		}

		if (clients.isEmpty() == false)
		{
			if (client.isLeader())
			{
				clients.get(0).setLeader(true);
				sendLeaderMessage(clients.get(0));
			}
		}

		checkNextTurn();
	}

	private int[] generateCircles()
	{
		int numCircles = (int) (random.nextGaussian() * Constants.NUM_CIRCLES_STANDARD_DEVIATION + Constants.NUM_CIRCLES_MEAN_VALUE);

		if (numCircles < 1)
		{
			numCircles = 1;
		}

		int circles[] = new int[3 * numCircles];

		for (int i = 0; i < numCircles; i++)
		{
			circles[3 * i]     = random.nextInt(Constants.PLANE_LENGTH);
			circles[3 * i + 1] = random.nextInt(Constants.PLANE_HEIGHT);
			circles[3 * i + 2] = (int) (random.nextGaussian() * Constants.CIRCLE_STANDARD_DEVIATION + Constants.CIRCLE_MEAN_RADIUS);

			while (circles[3 * i + 2] < 0)
			{
				circles[3 * i + 2] = (int) (random.nextGaussian() * Constants.CIRCLE_STANDARD_DEVIATION + Constants.CIRCLE_MEAN_RADIUS);
			}
		}

		return circles;
	}

	private class Soldier
	{
		public int x;
		public int y;

		public Soldier(int x, int y)
		{
			this.x = x;
			this.y = y;
		}
	}

	private double distance(int x1, int y1, int x2, int y2)
	{
		return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
	}

	private boolean testSoldier(Soldier soldier, List<Soldier> soldiers, int[] circles)
	{
		ListIterator<Soldier> itr = soldiers.listIterator();

		while (itr.hasNext())
		{
			Soldier tempSoldier = itr.next();

			if (Math.abs(soldier.x - tempSoldier.x) < 20 && Math.abs(soldier.y - tempSoldier.y) < 20)
			{
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
		while (testSoldier(soldier, soldiers, circles) == false);

		return soldier;
	}

	private int[] generateSoldiers(int[] circles)
	{
		List<Soldier> soldiers = new ArrayList<Soldier>();

		ListIterator<Player> pitr = players.listIterator();

		while (pitr.hasNext())
		{
			Player player = pitr.next();

			for (int i = 0; i < player.getNumSoldiers(); i++)
			{
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

	private class StartDelayer implements Runnable
	{
		GraphServer graphServer;
		Thread thisThread;

		public StartDelayer(GraphServer graphServer)
		{
			this.graphServer = graphServer;

			thisThread = new Thread(this);
			thisThread.start();
		}

		public void stop()
		{
			thisThread.interrupt();
			countingDown = false;
		}

		public void run()
		{
			try
			{
				Thread.sleep(Constants.START_GAME_DELAY);
				graphServer.sendStartGameMessage();
			}
			catch (InterruptedException e)
			{
				// зупинений нормально
			}
		}
	}

	protected synchronized void sendStartGameMessage()
	{
		if (checkAllReady())
		{
			startGame();
		}

		countingDown = false;
	}

	private void sendStartCountDown()
	{
		if (countingDown == false)
		{
			countingDown = true;

			startDelayer = new StartDelayer(this);

			String message = NetworkProtocol.START_COUNTDOWN + "";
			sendMessageAll(message);
		}
	}

	private void reorderPlayers()
	{
		List<Player> newPlayers = new ArrayList<Player>();

		int currentTeam = Constants.TEAM1;

		if (random.nextBoolean())
		{
			currentTeam = Constants.TEAM2;
		}

		while (players.size() > 0)
		{
			ListIterator<Player> pitr = players.listIterator();

			boolean found = false;

			while (pitr.hasNext())
			{
				Player player = pitr.next();

				if (player.getTeam() == currentTeam)
				{
					pitr.remove();

					newPlayers.add(player);

					if (currentTeam == Constants.TEAM1)
					{
						currentTeam = Constants.TEAM2;
					}
					else
					{
						currentTeam = Constants.TEAM1;
					}

					found = true;
				}
			}

			if (found == false)
			{
				if (currentTeam == Constants.TEAM1)
				{
					currentTeam = Constants.TEAM2;
				}
				else
				{
					currentTeam = Constants.TEAM1;
				}
			}
		}

		players = newPlayers;

		String message = NetworkProtocol.REORDER + "";

		ListIterator<Player> pitr = players.listIterator();

		while (pitr.hasNext())
		{
			Player player = pitr.next();

			message = message + "&" + player.getID();
		}

		sendMessageAll(message);
	}

	protected void startGame()
	{
		stopListening();

		reorderPlayers();

		int[] circles    = generateCircles();
		int numCircles   = circles.length / 3;

		int[] soldiers = generateSoldiers(circles);

		if (soldiers.length == 0)
		{
			return;
		}

		String message = NetworkProtocol.START_GAME + "&" + numCircles;

		for (int i = 0; i < circles.length; i++)
		{
			message = message + "&" + circles[i];
		}

		for (int i = 0; i < soldiers.length; i++)
		{
			message = message + "&" + soldiers[i];
		}

		int startPlayer = Math.abs(random.nextInt() % players.size());

		while (players.get(startPlayer).getNumSoldiers() == 0)
		{
			startPlayer = Math.abs(random.nextInt() % players.size());
		}

		message = message + "&" + startPlayer;

		sendMessageAll(message);

		timeTurnStarted = System.currentTimeMillis();
		gameState = Constants.GAME;

		setEveryoneNotReady();
	}

	private void checkNextTurn()
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();

			if (client.getReadyNextTurn() == false)
			{
				return;
			}
		}

		nextTurn();
	}

	private void nextTurn()
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();

			client.setReadyNextTurn(false);
		}

		String message = NetworkProtocol.NEXT_TURN + "";
		sendMessageAll(message);

		timeTurnStarted = System.currentTimeMillis();
	}

	protected void finishGame(ClientConnection client)
	{
		client.setFinished(true);

		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection tempClient = itr.next();

			if (tempClient.isFinished() == false)
			{
				return;
			}
		}

		itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection tempClient = itr.next();

			tempClient.setFinished(false);
			tempClient.setSkipLevel(false);
		}

		setEveryoneNotReady();
		String message = NetworkProtocol.GAME_FINISHED + "";
		sendMessageAll(message);

		goPreGame();
	}

	protected void goPreGame()
	{
		gameState = Constants.PRE_GAME;
		restartListening();
	}

	private void checkTimeUp()
	{
		if (System.currentTimeMillis() - timeTurnStarted > Constants.TURN_TIME)
		{
			nextTurn();
		}
	}

	private void checkSkipLevel()
	{
		ListIterator<ClientConnection> itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();

			if (client.getSkipLevel() == false)
			{
				return;
			}
		}

		itr = clients.listIterator();

		while (itr.hasNext())
		{
			ClientConnection client = itr.next();

			client.setSkipLevel(false);
		}

		startGame();
	}

	private void handleCommands(String msg, ClientConnection client) throws UnsupportedEncodingException
	{
		if (msg.startsWith("-"))
		{
			if (msg.compareToIgnoreCase("-skip") == 0)
			{
				if (this.gameState == Constants.GAME)
				{
					client.setSkipLevel(true);
					checkSkipLevel();
				}
			}
		}
	}

	public synchronized void handleMessage(String message, ClientConnection client)
	{
		String[] info = message.split("&");

		try
		{
			int type = Integer.parseInt(info[0]);

			switch (type)
			{
				case NetworkProtocol.NO_INFO:
				{
				}
				break;

				case NetworkProtocol.ADD_PLAYER:
				{
					if (players.size() < Constants.MAX_PLAYERS)
					{
						Player player = new Player(info[1]);
						client.addPlayer(player);
						players.add(player);

						setEveryoneNotReady();
						sendAddPlayerMessage(player, client);
					}
				}
				break;

				case NetworkProtocol.SET_TEAM:
				{
					int team     = Integer.parseInt(info[1]);
					int playerID = Integer.parseInt(info[2]);

					if (setTeam(team, playerID, client))
					{
						setEveryoneNotReady();
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.REMOVE_PLAYER:
				{
					int playerID = Integer.parseInt(info[1]);

					if (removePlayer(playerID, client))
					{
						setEveryoneNotReady();
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.ADD_SOLDIER:
				{
					int playerID = Integer.parseInt(info[1]);

					if (addSoldier(playerID, client))
					{
						setEveryoneNotReady();
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.REMOVE_SOLDIER:
				{
					int playerID = Integer.parseInt(info[1]);

					if (removeSoldier(playerID, client))
					{
						setEveryoneNotReady();
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.CHAT_MSG:
				{
					int playerID = Integer.parseInt(info[1]);

					if (checkPlayer(playerID, client))
					{
						handleCommands(info[2], client);
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.NEXT_MODE:
				{
					if (client.isLeader())
					{
						gameMode = (gameMode + 1) % 3;

						setEveryoneNotReady();
						sendModeMessage();
					}
				}
				break;

				case NetworkProtocol.SET_READY:
				{
					int playerID = Integer.parseInt(info[1]);
					boolean ready = Integer.parseInt(info[2]) != 0;

					if (setReady(playerID, client, ready))
					{
						sendMessageAll(message);
					}

					if (checkAllReady())
					{
						sendStartCountDown();
					}
				}
				break;

				case NetworkProtocol.READY_NEXT_TURN:
				{
					if (gameState == Constants.GAME)
					{
						client.setReadyNextTurn(true);
						checkNextTurn();
					}
				}
				break;

				case NetworkProtocol.FIRE_FUNC:
				{
					int playerID = Integer.parseInt(info[1]);

					if (checkPlayer(playerID, client) && gameState == Constants.GAME)
					{
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.FUNCTION_PREVIEW:
				{
					int playerID = Integer.parseInt(info[1]);

					if (checkPlayer(playerID, client) && gameState == Constants.GAME)
					{
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.TIME_UP:
				{
					if (gameState == Constants.GAME)
					{
						checkTimeUp();
					}
				}
				break;

				case NetworkProtocol.GAME_FINISHED:
				{
					finishGame(client);
				}
				break;

				case NetworkProtocol.SET_ANGLE:
				{
					int playerID = Integer.parseInt(info[1]);

					if (checkPlayer(playerID, client) && gameState == Constants.GAME)
					{
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.DISCONNECT:
				{
					removeClient(client);
					client.disconnect();
				}
			}
		}
		catch (Exception e)
		{
			invalidMessage(message);
			e.printStackTrace();
		}
	}

	private void invalidMessage(String message)
	{
		System.out.println("Invalid message received: " + message);
	}
}
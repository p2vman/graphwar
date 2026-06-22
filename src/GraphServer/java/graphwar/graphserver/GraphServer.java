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

import graphwar.graphserver.commands.CommandException;
import graphwar.graphserver.commands.Dispatcher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GraphServer implements Runnable
{
	private final int port;
	protected ObjectList<ClientConnection> clients;
	protected ObjectList<Player> players;
	protected boolean acceptingConnections;
	protected int gameMode;
	@Getter
	protected int gameState;
	private boolean countingDown;
	private StartDelayer startDelayer;

	private long timeTurnStarted;

	private final static Random random = new SecureRandom();
	private static final Logger LOGGER = LoggerFactory.getLogger(GraphServer.class);

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private Channel serverChannel;
	@Getter
	private final Dispatcher command_dispatcher;

	public GraphServer(int port) throws IOException
	{
		clients  = new ObjectArrayList<>();
		players  = new ObjectArrayList<>();

		this.port = port;

		gameMode  = Constants.NORMAL_FUNC;
		gameState = Constants.PRE_GAME;

		countingDown = false;
		startDelayer = null;

		acceptingConnections = true;
		this.command_dispatcher = new Dispatcher();

		this.command_dispatcher.register("skip", ((arguments, command, client, server) -> {
			if (server.getGameState() == Constants.GAME)
			{
				client.setSkipLevel(true);
				server.checkSkipLevel();
			} else {
				throw new CommandException("Invalid game state");
			}
			return 0;
		}));
	}

	public GraphServer() throws IOException
	{
		this(0);
	}

	public void run()
	{
		acceptingConnections = true;

		EventLoopGroupType groupType = EventLoopGroupType.getAvailable();
		LOGGER.info("Using: {}", groupType);
		bossGroup   = groupType.newEventLoop(1);
		workerGroup = groupType.newEventLoop();

		try
		{
			final GraphServer self = this;

			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
					.channel(groupType.serverSocketCls)
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

	public void shutdown()
	{
		String message = NetworkProtocol.DISCONNECT + "";
		sendMessageAll(message);

        for (ClientConnection client : clients) {
		    try {
		        client.disconnect();
		    } catch (Exception e) {
				// ignore
		    }
		}

		stopListening();

		try {
		    if (serverChannel != null) serverChannel.close().syncUninterruptibly();
		} catch (Exception e) {
		    // ignore
		}

		try {
		    if (bossGroup != null) bossGroup.shutdownGracefully().await(5000);
		} catch (InterruptedException e) {
		    Thread.currentThread().interrupt();
		} catch (Exception e) {
			// ignore
		}

		try {
		    if (workerGroup != null) workerGroup.shutdownGracefully().await(5000);
		} catch (InterruptedException e) {
		    Thread.currentThread().interrupt();
		} catch (Exception e) {
			// ignore
		}
	}

	public void addClient(ClientConnection client)
	{
		if (!acceptingConnections)
		{
			String message = NetworkProtocol.GAME_FULL + "";
			client.sendMessage(message);
			client.disconnect();
			return;
		}

		lock.lock();
		try {
			if (clients.size() < Constants.MAX_CLIENTS)
			{
				if (clients.isEmpty())
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
		} finally {
			lock.unlock();
		}

	}

	private void sendMessageAll(String message)
	{

        for (ClientConnection client : clients) {
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

        for (ClientConnection tmpClient : clients) {
            List<Player> players = tmpClient.getPlayers();

            for (Player player : players) {
                String message;

                int local = 0;
                if (client == tmpClient) {
                    local = 1;
                }

                int ready = 0;
                if (player.getReady()) {
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

        for (ClientConnection client : clients) {
            String message;

            int local = 0;
            if (client == playerFrom) {
                local = 1;
            }

            int ready = 0;
            if (player.getReady()) {
                ready = 1;
            }

            message = NetworkProtocol.ADD_PLAYER + "&" + player.getID() + "&" + player.getName() + "&" + player.getTeam() + "&" + local + "&" + player.getNumSoldiers() + "&" + ready;

            client.sendMessage(message);
        }
	}

	private boolean setTeam(int team, int playerID, ClientConnection client)
	{
		List<Player> players = client.getPlayers();

        for (Player player : players) {
            if (player.getID() == playerID) {
                player.setTeam(team);
                return true;
            }
        }

		if (client.isLeader())
		{

            for (ClientConnection tmpClient : clients) {
                List<Player> tmpPlayers = tmpClient.getPlayers();

                for (Player player : tmpPlayers) {
                    if (player.getID() == playerID) {
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

        for (Player player : clientPlayers) {
            if (player.getID() == playerID) {
                client.removePlayer(player);
                players.remove(player);
                return true;
            }
        }

		if (client.isLeader())
		{

            for (ClientConnection tmpClient : clients) {
                List<Player> tmpPlayers = tmpClient.getPlayers();

                for (Player player : tmpPlayers) {
                    if (player.getID() == playerID) {
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

        for (Player player : players) {
            if (player.getID() == playerID) {
                player.setReady(ready);

                if (!ready) {
                    if (startDelayer != null) {
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

        for (Player player : players) {
            if (player.getID() == playerID) {
                int newNum = player.getNumSoldiers() + 1;

                if (newNum <= Constants.MAX_SOLDIERS_PER_PLAYER) {
                    player.setNumSoldiers(newNum);
                    return true;
                }

                break;
            }
        }

		if (client.isLeader())
		{

            for (ClientConnection tmpClient : clients) {
                players = tmpClient.getPlayers();

                for (Player player : players) {
                    if (player.getID() == playerID) {
                        int newNum = player.getNumSoldiers() + 1;

                        if (newNum <= Constants.MAX_SOLDIERS_PER_PLAYER) {
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

        for (Player player : players) {
            if (player.getID() == playerID) {
                int newNum = player.getNumSoldiers() - 1;

                if (newNum >= 0) {
                    player.setNumSoldiers(newNum);
                    return true;
                }

                break;
            }
        }

		if (client.isLeader())
		{

            for (ClientConnection tmpClient : clients) {
                players = tmpClient.getPlayers();

                for (Player player : players) {
                    if (player.getID() == playerID) {
                        int newNum = player.getNumSoldiers() - 1;

                        if (newNum >= 0) {
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

        for (ClientConnection tmpClient : clients) {
            List<Player> players = tmpClient.getPlayers();

            for (Player player : players) {
                if (player.getReady()) {
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

        for (Player player : players) {
            if (player.getID() == playerID) {
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

        for (ClientConnection tmpClient : clients) {
            List<Player> players = tmpClient.getPlayers();

            for (Player player : players) {
                if (!player.getReady()) {
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

        for (Player player : clientPlayers) {
            String message = NetworkProtocol.REMOVE_PLAYER + "&" + player.getID();
            sendMessageAll(message);

            players.remove(player);
        }

		if (!clients.isEmpty())
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

	private double distance(int x1, int y1, int x2, int y2)
	{
		return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
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

	private int[] generateSoldiers(int[] circles)
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

	protected void sendStartGameMessage()
	{
		lock.lock();
		try {
			if (checkAllReady())
			{
				startGame();
			}

			countingDown = false;
		} finally {
			lock.unlock();
		}
	}

	private void sendStartCountDown()
	{
		if (!countingDown)
		{
			countingDown = true;

			startDelayer = new StartDelayer(this);

			String message = NetworkProtocol.START_COUNTDOWN + "";
			sendMessageAll(message);
		}
	}

	private void reorderPlayers()
	{
		ObjectList<Player> newPlayers = new ObjectArrayList<>();

		int currentTeam = Constants.TEAM1;

		if (random.nextBoolean())
		{
			currentTeam = Constants.TEAM2;
		}

		while (!players.isEmpty())
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

			if (!found)
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

        for (Player player : players) {
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

        for (int circle : circles) {
            message = message + "&" + circle;
        }

        for (int soldier : soldiers) {
            message = message + "&" + soldier;
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

        for (ClientConnection client : clients) {
            if (!client.getReadyNextTurn()) {
                return;
            }
        }

		nextTurn();
	}

	private void nextTurn()
	{

        for (ClientConnection client : clients) {
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

			if (!tempClient.isFinished())
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

			if (!client.getSkipLevel())
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

	private final Lock lock = new ReentrantLock();
	public void handleMessage(String message, ClientConnection client)
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
					lock.lock();
					try {
						if (players.size() < Constants.MAX_PLAYERS)
						{
							Player player = new Player(info[1]);
							client.addPlayer(player);
							players.add(player);

							setEveryoneNotReady();
							sendAddPlayerMessage(player, client);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.SET_TEAM:
				{
					int team     = Integer.parseInt(info[1]);
					int playerID = Integer.parseInt(info[2]);
					lock.lock();
					try {

						if (setTeam(team, playerID, client))
						{
							setEveryoneNotReady();
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.REMOVE_PLAYER:
				{
					int playerID = Integer.parseInt(info[1]);

					lock.lock();
					try {
						if (removePlayer(playerID, client))
						{
							setEveryoneNotReady();
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.ADD_SOLDIER:
				{
					int playerID = Integer.parseInt(info[1]);

					lock.lock();
					try {
						if (addSoldier(playerID, client))
						{
							setEveryoneNotReady();
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.REMOVE_SOLDIER:
				{
					int playerID = Integer.parseInt(info[1]);

					lock.lock();
					try {
						if (removeSoldier(playerID, client))
						{
							setEveryoneNotReady();
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.CHAT_MSG:
				{
					int playerID = Integer.parseInt(info[1]);

					if (checkPlayer(playerID, client))
					{
						//handleCommands(info[2], client);
						this.command_dispatcher.handleCommand(info[2], client, this);
						sendMessageAll(message);
					}
				}
				break;

				case NetworkProtocol.NEXT_MODE:
				{
					lock.lock();
					try {
						if (client.isLeader())
						{
							gameMode = (gameMode + 1) % 3;

							setEveryoneNotReady();
							sendModeMessage();
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.SET_READY:
				{
					int playerID = Integer.parseInt(info[1]);
					boolean ready = Integer.parseInt(info[2]) != 0;

					lock.lock();
					try {
						if (setReady(playerID, client, ready))
						{
							sendMessageAll(message);
						}

						if (checkAllReady())
						{
							sendStartCountDown();
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.READY_NEXT_TURN:
				{
					if (gameState == Constants.GAME)
					{
						lock.lock();
						try {
							client.setReadyNextTurn(true);
							checkNextTurn();
						} finally {
							lock.unlock();
						}
					}
				}
				break;

				case NetworkProtocol.FIRE_FUNC:
				{
					int playerID = Integer.parseInt(info[1]);

					lock.lock();
					try {
						if (checkPlayer(playerID, client) && gameState == Constants.GAME)
						{
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.FUNCTION_PREVIEW:
				{
					int playerID = Integer.parseInt(info[1]);

					lock.lock();
					try {
						if (checkPlayer(playerID, client) && gameState == Constants.GAME)
						{
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
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
					lock.lock();
					try {
						finishGame(client);
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.SET_ANGLE:
				{
					int playerID = Integer.parseInt(info[1]);

					try {
						if (checkPlayer(playerID, client) && gameState == Constants.GAME)
						{
							sendMessageAll(message);
						}
					} finally {
						lock.unlock();
					}
				}
				break;

				case NetworkProtocol.DISCONNECT:
				{
					lock.lock();
					try {
						removeClient(client);
						client.disconnect();
					} finally {
						lock.unlock();
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Invalid message received: {}", message);
			LOGGER.error("Throw: ", e);
		}
	}
}
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

import graphwar.graphserver.card.CardGenerator;
import graphwar.graphserver.card.GeneratorFactory;
import graphwar.graphserver.card.StandardCardGenerator;
import graphwar.graphserver.commands.CommandContext;
import graphwar.graphserver.commands.CommandException;
import graphwar.graphserver.commands.Dispatcher;
import graphwar.graphserver.registry.Identifier;
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
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
public class GraphServer implements Runnable
{
	private final int port;
	@Getter
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


	private final ConcurrentMap<String, AtomicInteger> ipClientCounts = new ConcurrentHashMap<>();
	
	private CardGenerator cardGenerator;


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
		this.cardGenerator = new StandardCardGenerator(random, players);

		this.command_dispatcher.register("skip", ((ctx,arguments, command) -> {
			if (ctx.getServer().getGameState() == Constants.GAME)
			{
				ctx.getClient().setSkipLevel(true);
				ctx.getServer().checkSkipLevel();
			} else {
				throw new CommandException("Invalid game state");
			}
			return 0;
		}));

		this.command_dispatcher.register("generator", ((ctx,arguments, command) -> {
			if (arguments.size() < 1) {
				ctx.sendMessage("Usage: -generator <subcommand>");
				ctx.sendMessage("-generator list");
				ctx.sendMessage("-generator set <id>");
				return 0;
			}
			switch (arguments.getString(0)) {
				case "set": {
					if (arguments.size() < 2) {
						ctx.sendMessage("Usage: -generator set <id>");
						return 0;
					}
					Optional<GeneratorFactory> opt = Registries.CARD_GENERATORS.getO(Identifier.tryParse(arguments.getString(1)));
					if (opt.isPresent()) {
						this.cardGenerator = opt.get().create(random, players);
						ctx.sendMessage("Ok.");
					} else {
						ctx.sendMessage("Generator not found");
					}
					break;
				}
				case "list": {
					for (Identifier key : Registries.CARD_GENERATORS.getKeys()) {
						ctx.sendMessage(key.toString());
					}
					break;
				}
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

							p.addLast(new LineBasedFrameDecoder(Constants.MAX_MESSAGE_LENGTH));
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
		if (serverChannel != null && serverChannel.localAddress() instanceof InetSocketAddress)
		{
			return ((InetSocketAddress) serverChannel.localAddress()).getPort();
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
		if (serverChannel != null && serverChannel.isOpen())
		{
			acceptingConnections = true;
			LOGGER.info("Restarted accepting connections (server channel already open)");
			return;
		}

		acceptingConnections = true;
		LOGGER.info("Starting server thread to accept connections");
		new Thread(this).start();
	}

	public void shutdown()
	{
		String message = NetworkProtocol.DISCONNECT + "";
		sendMessageAll(message);

        for (ClientConnection client : clients) {
		    try {
		        LOGGER.info("Shutting down connection to client {}", client.getConnection().getIpAddress());
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
		String ip = client.getConnection().getIpAddress();
		if (ip != null && !ip.isEmpty()) {
			AtomicInteger counter = ipClientCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
			int nowCount = counter.incrementAndGet();
			if (nowCount > Constants.MAX_CLIENTS_PER_IP) {
				LOGGER.info("Rejecting connection from {}: too many connections from this IP ({} > {})", ip, nowCount, Constants.MAX_CLIENTS_PER_IP);
				client.sendMessage(NetworkProtocol.DISCONNECT + "");
				counter.decrementAndGet();
				return;
			}
		}

		if (!acceptingConnections)
		{
			String message = NetworkProtocol.GAME_FULL + "";
			LOGGER.info("Rejecting connection from {}: not accepting connections", client.getConnection().getIpAddress());
			client.sendMessage(message);
			client.disconnect();
			if (ip != null && !ip.isEmpty()) {
				AtomicInteger c = ipClientCounts.get(ip);
				if (c != null) c.decrementAndGet();
			}
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
				LOGGER.info("Rejecting connection from {}: server full", client.getConnection().getIpAddress());
				client.sendMessage(message);
				client.disconnect();
				if (ip != null && !ip.isEmpty()) {
					AtomicInteger c = ipClientCounts.get(ip);
					if (c != null) c.decrementAndGet();
				}
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

	protected void sendWelcomeMessage(ClientConnection client, Player player)
	{
		try {
			String welcome = "Welcome to the community room!";
			String encoded = URLEncoder.encode(welcome, StandardCharsets.UTF_8.name());
			String welcomeMsg = NetworkProtocol.CHAT_MSG + "&" + player.getID() + "&" + encoded;
			client.sendMessage(welcomeMsg);
		} catch (UnsupportedEncodingException e) {
			LOGGER.error("Failed to encode welcome message", e);
		}
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

        String ip = client.getConnection().getIpAddress();
        if (ip != null && !ip.isEmpty()) {
            AtomicInteger c = ipClientCounts.get(ip);
            if (c != null) {
            	int v = c.decrementAndGet();
            	if (v <= 0) ipClientCounts.remove(ip);
            }
        }

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
			catch (Throwable e)
			{
				LOGGER.error("Throw: ", e);
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
		acceptingConnections = false;
		LOGGER.info("Starting game: no longer accepting new connections");
		
		reorderPlayers();

		int[] circles    = cardGenerator.generateCircles();
		int numCircles   = circles.length / 3;

		int[] soldiers = cardGenerator.generateSoldiers(circles, players);

		if (soldiers.length == 0)
		{
			LOGGER.info("SO {}", soldiers.length);
			acceptingConnections = true;
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
		Identifier[] keys = Registries.CARD_GENERATORS.getKeys().toArray(new Identifier[0]);
		cardGenerator = Registries.CARD_GENERATORS.get(keys[random.nextInt(keys.length)]).create(random, players);

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

		lock.lock();
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
					String name = info.length > 1 ? info[1].trim() : "";
					boolean valid = true;
					if (name.isEmpty() || name.equals(Constants.DUMMY_NAME) || name.length() > 32)
					{
						valid = false;
					}
					if (valid) {
						for (Player p : players) {
							if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
								valid = false;
								break;
							}
						}
					}
					if (!valid) {
						LOGGER.info("Rejecting ADD_PLAYER from {}: invalid name '{}'", client.getConnection().getIpAddress(), name);
						client.sendMessage(NetworkProtocol.DISCONNECT + "");
						client.disconnect();
						return;
					}
					if (players.size() < Constants.MAX_PLAYERS)
					{
						Player player = new Player(name);
						client.addPlayer(player);
						players.add(player);
						
						setEveryoneNotReady();
						sendAddPlayerMessage(player, client);

						sendWelcomeMessage(client, player);
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
						String decoded = URLDecoder.decode(info[2], StandardCharsets.UTF_8.name());

						LOGGER.info(
								"Message from player {} (room {}): {}",
								playerID,
								port,
								decoded
						);

						//handleCommands(info[2], client);
						CommandContext ctx = new CommandContext(client, this);
						this.command_dispatcher.handleCommand(decoded, ctx);
						if (!ctx.isHidemessage()) {
							sendMessageAll(message);
						}
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
					LOGGER.info("Client requested disconnect: {}", client.getConnection().getIpAddress());
					removeClient(client);
					client.disconnect();
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Invalid message received: {}", message);
			LOGGER.error("Throw: ", e);
		} finally {
			lock.unlock();
		}
	}

	public void tryLock(Runnable runnable) {
		lock.lock();
		try {
			runnable.run();
		} finally {
			lock.unlock();
		}
	}
}
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
package graphwar.globalserver;

import graphwar.graphserver.Connection;
import graphwar.graphserver.Constants;
import graphwar.graphserver.NetworkProtocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.AttributeKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;


public class GlobalServer implements Runnable
{
	private final ObjectList<LobbyPlayer> players;
	private final ObjectList<Room> rooms;
		
	private long lastRoomCheck;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;

	private static final AttributeKey<LobbyPlayer> ATTR_PLAYER = AttributeKey.valueOf("lobbyPlayer");
	private static final AttributeKey<Connection> ATTR_CONN = AttributeKey.valueOf("connection");

	public GlobalServer()
	{
		this.players = new ObjectArrayList<>();
		this.rooms = new ObjectArrayList<>();
		
		lastRoomCheck = System.currentTimeMillis();
	}
	
	public void registerNewPlayer(LobbyPlayer newPlayer)
	{
		if(!newPlayer.isDummy())
		{		
			String message = NetworkProtocol.JOIN+"&"+newPlayer.getName()+"&"+newPlayer.getID();
			
			sendMessageAll(message);
		}
	}
	
    private void sendChat(int playerID, String chatMessage)
    {
    	String message = NetworkProtocol.SAY_CHAT+"&"+playerID+"&"+chatMessage;
    	
    	sendMessageAll(message);
    }
	    
    private void sendNewRoom(Room room)
    {
    	String message = NetworkProtocol.CREATE_ROOM+"&"+room.getName()+"&"+room.getRoomID()+"&"+room.getIp()+"&"+room.getPort();
    	
    	sendMessageAll(message);
    }
    
    public void sendListPlayers(LobbyPlayer player)
    {
    	String message = "";
    	
    	int i=0;
    	
    	synchronized(players)
    	{

            for (LobbyPlayer tempPlayer : players) {
                if (!tempPlayer.isDummy()) {
                    message = message + "&" + tempPlayer.getName() + "&" + tempPlayer.getID();
                    i++;
                }
            }
    	}
	    	
    	message = NetworkProtocol.LIST_PLAYERS+"&"+i+message;
    	
    	player.sendMessage(message);
    }
    
    public void sendListRooms(LobbyPlayer player)
    {
    	String message = "";    	
    	
    	int i=0;
    	
    	synchronized(rooms)
		{
            for (Room room : rooms) {
                message = message + "&" + room.getName() + "&" + room.getRoomID() + "&" + room.getIp() + "&" + room.getPort() + "&" + room.getGameMode() + "&" + room.getNumPlayers();

                i++;
            }
		}
	    	
    	message = NetworkProtocol.LIST_ROOMS+"&"+i+message;
    	
    	player.sendMessage(message);
    }
    
    public void updateRoom(Room room)
    {
    	String message = NetworkProtocol.ROOM_STATUS+"&"+room.getRoomID()+"&"+room.getGameMode()+"&"+room.getNumPlayers();
    	
    	sendMessageAll(message);
    }
    
    private void sendMessageAll(String message)
    {
    	//System.out.println("Sent to everyone: "+message);

    	List<LobbyPlayer> snapshot;
    	synchronized(players)
    	{
    		snapshot = new Vector<LobbyPlayer>(players);
    	}

        for (LobbyPlayer player : snapshot) {
            player.sendMessage(message);
        }
    }
    
   
    public void removePlayer(LobbyPlayer player)
    {
    	String message = NetworkProtocol.QUIT+"&"+player.getID();
    	sendMessageAll(message);
    	
    	player.disconnect();
    	
    	synchronized(players)
    	{
    		players.remove(player);
    	}
    	
    	if(player.getRoom() != null)
    	{
    		removeRoom(player.getRoom());
    	}
    }
    
    public void removeRoom(Room room)
    {
		String message = NetworkProtocol.CLOSE_ROOM+"&"+room.getRoomID();
		sendMessageAll(message);
		
		synchronized(rooms)
		{
			rooms.remove(room);
		}
		
    }
    
    private boolean tryConnection(Room room)
    {
    	try
		{
			Connection connection = new Connection(room.getIp(), room.getPort());
			String message = NetworkProtocol.DISCONNECT+"";    	
	    	connection.sendMessage(message);
	    	connection.close();
	    	return true;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}    	
    }
    
    private void sendRoomInvalid(LobbyPlayer player)
    {
    	String message = NetworkProtocol.ROOM_INVALID+"";
    	
    	player.sendMessage(message);
    }
    
	public synchronized void handleMessage(String message, LobbyPlayer player)
	{		
		String[] info = new String[0];

		if(message != null)
		{
			info = message.split("&");
		}
		
		if(info.length > 0)
		{
			int code = Integer.parseInt(info[0]);
			
			if(code != NetworkProtocol.NO_INFO)
			{
				System.out.println("Received from "+player.getID()+": "+message);				
			}
			
			switch(code)
			{				
				case NetworkProtocol.NO_INFO:
				{
					player.sendMessage(NetworkProtocol.NO_INFO+"");
				}break;
			
				case NetworkProtocol.SAY_CHAT:
				{
					if(info.length == 2)
					{
						String chatMessage = info[1];
						
						sendChat(player.getID(), chatMessage);						
					}
				}break;
				
				case NetworkProtocol.ROOM_STATUS:
				{
					if(info.length == 3)
					{
						int gameMode = Integer.parseInt(info[1]);
						int numPlayers = Integer.parseInt(info[2]);
						
						Room room = player.getRoom();
						
						if(room != null)
						{
							room.updateRoom(numPlayers, gameMode);
							updateRoom(player.getRoom());
						}						
					}
				}break;
				
				case NetworkProtocol.CREATE_ROOM:
				{
					if(info.length == 3)
					{
						String roomName = info[1];
						int portNumber = Integer.parseInt(info[2]);
						String ipAddress = "";
						try 
						{
							ipAddress = URLEncoder.encode(player.getIpAddress(), "UTF-8");
						} catch (UnsupportedEncodingException e) 
						{
							e.printStackTrace();
						}
						
						Room room = new Room(roomName, ipAddress, portNumber);
						
						if(tryConnection(room))
						{
							synchronized(rooms)
							{
								rooms.add(room);	
							}
							
							player.setRoom(room);
							
							sendNewRoom(room);
						}
						else
						{
							sendRoomInvalid(player);
						}
					}
				}break;
				
				case NetworkProtocol.QUIT:
				{
					if(info.length == 1)
					{			
						if(player.getRoom() != null)
						{
							removeRoom(player.getRoom());	
						}
						
						removePlayer(player);												
					}
					
				}break;
				
				case NetworkProtocol.CLOSE_ROOM:
				{
					if(info.length == 1)
					{						
						if(player.getRoom() != null)
						{
							removeRoom(player.getRoom());	
						}					
					}
				}break;
				
			}
		}
	}
	
	private void checkRooms()
	{
		if(System.currentTimeMillis() - lastRoomCheck > 5*60*1000)
		{
			lastRoomCheck = System.currentTimeMillis();

			List<LobbyPlayer> playerSnapshot;
			synchronized(players)
			{
				playerSnapshot = new ArrayList<>(players);
			}

			synchronized(rooms)
			{
				ListIterator<Room> itr = rooms.listIterator();
				while(itr.hasNext())
				{
				    Room room = itr.next();

				    boolean roomOk = false;

                    for (LobbyPlayer tempPlayer : playerSnapshot) {
                        if (tempPlayer.getRoom() == room) {
                            roomOk = true;
                            break;
                        }
                    }

				    if(!roomOk)
				    {
				        System.out.println("Removing room: "+room.getName());

				        itr.remove();
				    }
				}

			}

			for(LobbyPlayer tempPlayer : playerSnapshot)
			{
				sendListRooms(tempPlayer);
			}
		}
	}

	public void run()
	{
		while(true) {
			try {
				bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
				workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

				try {
					final GlobalServer self = this;

					ChannelFuture tcpFuture = null;
					ServerBootstrap b = new ServerBootstrap();
					b.group(bossGroup, workerGroup)
							.channel(NioServerSocketChannel.class)
							.childHandler(new ChannelInitializer<SocketChannel>() {
								@Override
								protected void initChannel(SocketChannel ch) throws Exception {
									ChannelPipeline p = ch.pipeline();

									final Connection conn = new Connection(ch);
									final LobbyPlayer player = new LobbyPlayer(conn, self);

									ch.attr(ATTR_CONN).set(conn);
									ch.attr(ATTR_PLAYER).set(player);

									p.addLast(new LineBasedFrameDecoder(8192));
									p.addLast(new StringDecoder(java.nio.charset.StandardCharsets.UTF_8));
									p.addLast(new StringEncoder(java.nio.charset.StandardCharsets.UTF_8));

									p.addLast(new SimpleChannelInboundHandler<String>() {
										@Override
										protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
											Connection c = ctx.channel().attr(ATTR_CONN).get();
											if (c != null) c.offerInbound(msg);
										}

										@Override
										public void channelActive(ChannelHandlerContext ctx) throws Exception {
											LobbyPlayer p = ctx.channel().attr(ATTR_PLAYER).get();
											if (p != null) {
												new Thread(p).start();
												synchronized (players) {
													players.add(p);
												}
											}
										}

										@Override
										public void channelInactive(ChannelHandlerContext ctx) throws Exception {
											LobbyPlayer p = ctx.channel().attr(ATTR_PLAYER).get();
											if (p != null) {
												removePlayer(p);
												p.disconnect();
											}
										}

										@Override
										public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
											cause.printStackTrace();
											ctx.close();
										}
									});
								}
							})
							.option(ChannelOption.SO_BACKLOG, 128)
							.childOption(ChannelOption.SO_KEEPALIVE, true)
							.childOption(ChannelOption.TCP_NODELAY, true);

					tcpFuture = b.bind(Constants.GLOBAL_PORT).sync();
					System.out.println("GlobalServer listening on port " + Constants.GLOBAL_PORT);


					// Wait for bound channel to close
					if (tcpFuture != null) tcpFuture.channel().closeFuture().sync();

				} finally {
					bossGroup.shutdownGracefully();
					workerGroup.shutdownGracefully();
				}

			} catch (Exception e) {
				e.printStackTrace();
				LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1000));
			}
		}
	}
	
		
	public static void handleArgs(String[] args)
	{
		if(args.length > 0)
		{
			// Overrides ip to create local server
			Constants.GLOBAL_IP = args[0];
		}
	}	
	
	public static void main(String[] args)
	{
		copyResourceIfMissing("/globalserver.properties", "globalserver.properties");

		File f = new File("globalserver.properties");

		if (f.exists()) {
		    try (FileReader fr = new FileReader(f)) {
		        Properties p = new Properties();
		        p.load(fr);
		        String port = p.getProperty("port");
		        String ip = p.getProperty("ip");
		        if (port != null) {
		            try { graphwar.graphserver.Constants.GLOBAL_PORT = Integer.parseInt(port); } catch (NumberFormatException ignored) {}
		        }
		        if (ip != null) {
		            graphwar.graphserver.Constants.GLOBAL_IP = ip;
		        }
		        System.out.println("Loaded globalserver.properties: ip="+graphwar.graphserver.Constants.GLOBAL_IP+" port="+graphwar.graphserver.Constants.GLOBAL_PORT);
		    } catch (Exception e) { e.printStackTrace(); }
		}

		handleArgs(args);

		GlobalServer server = new GlobalServer();


		new Thread(() -> {
		    try {
		        server.run();
		    } catch (Exception e) {
		        e.printStackTrace();
		    }
		}).start();
	}

	private static void copyResourceIfMissing(String resourcePath, String destFileName) {
		try {
			File dest = new File(destFileName);
			if (dest.exists()) return;
			InputStream in = GlobalServer.class.getResourceAsStream(resourcePath);
			if (in == null) return;
			Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

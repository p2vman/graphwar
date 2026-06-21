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
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;


public class GlobalServer implements Runnable
{
	private List<LobbyPlayer> players;
	private List<Room> rooms;
		
	private long lastRoomCheck;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private Channel serverChannel;

	private static final AttributeKey<LobbyPlayer> ATTR_PLAYER = AttributeKey.valueOf("lobbyPlayer");
	private static final AttributeKey<Connection> ATTR_CONN = AttributeKey.valueOf("connection");

	public GlobalServer()
	{
		this.players = new Vector<LobbyPlayer>();
		this.rooms = new Vector<Room>();
		
		lastRoomCheck = System.currentTimeMillis();
	}
	
	public void registerNewPlayer(LobbyPlayer newPlayer)
	{
		if(newPlayer.isDummy() == false)
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
	    	ListIterator<LobbyPlayer> itr = players.listIterator();
	    	
	    	while(itr.hasNext())
	    	{
	    		LobbyPlayer tempPlayer = itr.next();
	    		
	    		if(tempPlayer.isDummy()==false)
	    		{
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
	    	ListIterator<Room> itr = rooms.listIterator();	    	
	    	while(itr.hasNext())
	    	{
	    		Room room = itr.next();
	    		
	    		message = message +"&"+room.getName()+"&"+room.getRoomID()+"&"+room.getIp()+"&"+room.getPort()+"&"+room.getGameMode()+"&"+room.getNumPlayers();
	    		
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

    	ListIterator<LobbyPlayer> itr = snapshot.listIterator();
    	while(itr.hasNext())
    	{
    		LobbyPlayer player = itr.next();
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
				playerSnapshot = new Vector<LobbyPlayer>(players);
			}

			synchronized(rooms)
			{
				ListIterator<Room> itr = rooms.listIterator();
				while(itr.hasNext())
				{
				    Room room = itr.next();

				    boolean roomOk = false;

				    ListIterator<LobbyPlayer> pitr = playerSnapshot.listIterator();
				    while(pitr.hasNext())
				    {
				        LobbyPlayer tempPlayer = pitr.next();

				        if(tempPlayer.getRoom() == room)
				        {
				            roomOk = true;
				            break;
				        }
				    }

				    if(roomOk == false)
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
		runWithProtocol("tcp", Constants.GLOBAL_PORT);
	}

	public void runWithProtocol(String protocol, int wsPort) {
		while(true) {
			try {
				bossGroup = new NioEventLoopGroup(1);
				workerGroup = new NioEventLoopGroup();

				try {
				    final GlobalServer self = this;

				    ChannelFuture tcpFuture = null;
				    ChannelFuture wsFuture = null;

				    if ("tcp".equals(protocol) || "both".equals(protocol)) {
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
				                         if(c != null) c.offerInbound(msg);
				                     }

				                     @Override
				                     public void channelActive(ChannelHandlerContext ctx) throws Exception {
				                         LobbyPlayer p = ctx.channel().attr(ATTR_PLAYER).get();
				                         if(p != null)
				                         {
				                             new Thread(p).start();
				                             synchronized(players)
				                             {
				                                 players.add(p);
				                             }
				                         }
				                     }

				                     @Override
				                     public void channelInactive(ChannelHandlerContext ctx) throws Exception {
				                         LobbyPlayer p = ctx.channel().attr(ATTR_PLAYER).get();
				                         if(p != null)
				                         {
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

				        ChannelFuture f = b.bind(Constants.GLOBAL_PORT).sync();
				        tcpFuture = f;
				        System.out.println("TCP GlobalServer listening on port " + Constants.GLOBAL_PORT);
				    }

				    if ("ws".equals(protocol) || "both".equals(protocol)) {
				        ServerBootstrap bws = new ServerBootstrap();
				        bws.group(bossGroup, workerGroup)
				           .channel(NioServerSocketChannel.class)
				           .childHandler(new ChannelInitializer<SocketChannel>() {
				               @Override
				               protected void initChannel(SocketChannel ch) throws Exception {
				                   ChannelPipeline p = ch.pipeline();

				                   p.addLast(new io.netty.handler.codec.http.HttpServerCodec());
				                   p.addLast(new io.netty.handler.codec.http.HttpObjectAggregator(65536));
				                   p.addLast(new io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler("/ws"));

				                   final Connection conn = new Connection(ch);
				                   final LobbyPlayer player = new LobbyPlayer(conn, self);

				                   ch.attr(ATTR_CONN).set(conn);
				                   ch.attr(ATTR_PLAYER).set(player);

				                   p.addLast(new SimpleChannelInboundHandler<io.netty.handler.codec.http.websocketx.TextWebSocketFrame>() {
				                       @Override
				                       protected void channelRead0(ChannelHandlerContext ctx, io.netty.handler.codec.http.websocketx.TextWebSocketFrame msg) throws Exception {
				                           Connection c = ctx.channel().attr(ATTR_CONN).get();
				                           if (c != null) c.offerInbound(msg.text());
				                       }

				                       @Override
				                       public void channelActive(ChannelHandlerContext ctx) throws Exception {
				                           LobbyPlayer p = ctx.channel().attr(ATTR_PLAYER).get();
				                           if(p != null) {
				                               new Thread(p).start();
				                               synchronized(players) { players.add(p); }
				                           }
				                       }

				                       @Override
				                       public void channelInactive(ChannelHandlerContext ctx) throws Exception {
				                           LobbyPlayer p = ctx.channel().attr(ATTR_PLAYER).get();
				                           if(p != null) { removePlayer(p); p.disconnect(); }
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

				        ChannelFuture fw = bws.bind(wsPort).sync();
				        wsFuture = fw;
				        System.out.println("WebSocket GlobalServer listening on port " + wsPort + " (path /ws)");
				    }

				    // Wait for any bound channel to close
				    if (tcpFuture != null) tcpFuture.channel().closeFuture().sync();
				    if (wsFuture != null) wsFuture.channel().closeFuture().sync();

				}
				finally {
				    bossGroup.shutdownGracefully();
				    workerGroup.shutdownGracefully();
				}

			} catch(Exception e) {
				e.printStackTrace();
				try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
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

		java.io.File f = new java.io.File("globalserver.properties");

		String protocol = "tcp";
		int wsPort = graphwar.graphserver.Constants.GLOBAL_PORT + 1;

		if (f.exists()) {
		    try (java.io.FileReader fr = new java.io.FileReader(f)) {
		        java.util.Properties p = new java.util.Properties();
		        p.load(fr);
		        String port = p.getProperty("port");
		        String ip = p.getProperty("ip");
		        if (port != null) {
		            try { graphwar.graphserver.Constants.GLOBAL_PORT = Integer.parseInt(port); } catch (NumberFormatException e) {}
		        }
		        if (ip != null) {
		            graphwar.graphserver.Constants.GLOBAL_IP = ip;
		        }
		        String proto = p.getProperty("protocol");
		        if (proto != null) protocol = proto.trim().toLowerCase();
		        String wsp = p.getProperty("wsPort");
		        if (wsp != null) {
		            try { wsPort = Integer.parseInt(wsp); } catch (NumberFormatException e) {}
		        }
		        System.out.println("Loaded globalserver.properties: ip="+graphwar.graphserver.Constants.GLOBAL_IP+" port="+graphwar.graphserver.Constants.GLOBAL_PORT+" protocol="+protocol+" wsPort="+wsPort);
		    } catch (Exception e) { e.printStackTrace(); }
		}

		handleArgs(args);

		GlobalServer server = new GlobalServer();

		final String protoFinal = protocol;
		final int wsPortFinal = wsPort;

		new Thread(() -> {
		    try {
		        server.runWithProtocol(protoFinal, wsPortFinal);
		    } catch (Exception e) {
		        e.printStackTrace();
		    }
		}).start();
	}

	private static void copyResourceIfMissing(String resourcePath, String destFileName) {
		try {
			java.io.File dest = new java.io.File(destFileName);
			if (dest.exists()) return;
			java.io.InputStream in = GlobalServer.class.getResourceAsStream(resourcePath);
			if (in == null) return;
			java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			in.close();
			System.out.println("Copied default config resource " + resourcePath + " to " + destFileName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

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

package graphwar.roomserver;

import graphwar.graphserver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RemoteGraphServer extends GraphServer
{
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteGraphServer.class);
	private final GlobalClient globalClient;
	private final PortPool port_pool;
	
	public int getNumClients()
	{
		return this.clients.size();
	}
	
	public int getNumPlayers()
	{
		return this.players.size();
	}
	
	public int getGameState()
	{
		return this.gameState;
	}
	
	public boolean isAcceptingConnections()
	{
		return this.acceptingConnections;
	}

	public void setAcceptingConnections(boolean accepting)
	{
		this.acceptingConnections = accepting;
		if (!accepting) {
		    LOGGER.info("RemoteGraphServer on port {} is now not accepting new connections", getPort());
		} else {
		    LOGGER.info("RemoteGraphServer on port {} is now accepting new connections", getPort());
		}

		tryLock(() -> {
			try {
				String welcome = !accepting ? "Room now not accepting new connections" : "Room now accepting new connections";
				String encoded = URLEncoder.encode(welcome, StandardCharsets.UTF_8.name());
				String msg = NetworkProtocol.CHAT_MSG + "&" + -1 + "&" + encoded;
				for (ClientConnection client : clients) {
					client.sendMessage(msg);
				}
			} catch (UnsupportedEncodingException e) {
				LOGGER.error("Failed to encode welcome message", e);
			}
		});
	}
		
	public RemoteGraphServer(GlobalClient globalClient, PortPool pool) throws IOException
	{
		super(pool.bind());
		this.port_pool = pool;
		
		this.globalClient = globalClient;		
	}

	protected void sendAddPlayerMessage(Player player, ClientConnection playerFrom)
	{
		super.sendAddPlayerMessage(player, playerFrom);
		
		globalClient.sendRoomStatus(this.gameMode, this.players.size());
	}
	
	protected boolean removePlayer(int playerID, ClientConnection client)
	{
		boolean v = super.removePlayer(playerID, client);
		
		globalClient.sendRoomStatus(this.gameMode, this.players.size());
		
		return v;
	}
	
	protected void sendModeMessage()
	{
		super.sendModeMessage();
		
		globalClient.sendRoomStatus(this.gameMode, this.players.size());
	}
	
	public void removeClient(ClientConnection client)
	{
		super.removeClient(client);
				
		if(this.clients.isEmpty())
		{
			if(this.gameState == Constants.GAME)
			{
				this.goPreGame();
				globalClient.recreateRoom();
			}
		}
		
		globalClient.sendRoomStatus(this.gameMode, this.players.size());
	}
	
	protected void startGame()
	{
		super.startGame();
		
		globalClient.hideRoom();
	}
	
	protected void finishGame(ClientConnection client)
	{
		super.finishGame(client);
		
		if(this.gameState == Constants.PRE_GAME)
		{
			globalClient.recreateRoom();
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		this.port_pool.unbind(getPort());
	}
}

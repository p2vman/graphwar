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

import graphwar.graphserver.Constants;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Room
{
	private final RemoteGraphServer gameServer;
	private final GlobalClient globalClient;
	@Getter
    private volatile boolean parked = false;
	@Getter
    private volatile long lastActiveTime = System.currentTimeMillis();
	
	@Getter
    private final int roomNum;

	private static final Logger LOGGER = LoggerFactory.getLogger(Room.class);
	
	public Room(int roomNum, PortPool pool) throws IOException
	{
		this.roomNum = roomNum;
		
		globalClient = new GlobalClient();
		
		//int port = Constants.PUBLIC_ROOM_PORT+roomNum;		
		gameServer = new RemoteGraphServer(globalClient, pool);
		int port = gameServer.getPort();
		
		new Thread(gameServer).start();
		
		globalClient.joinGlobalServer(Constants.GLOBAL_IP, Constants.GLOBAL_PORT, Constants.DUMMY_NAME);
		globalClient.createRoom("Сommunity: " + roomNum, port);
	}

	public int getPort() {
		return this.gameServer.getPort();
	}
	
	public int getNumCLients()
	{
		return gameServer.getNumClients();
	}

    public boolean isAcceptingConnections()
	{
		return !this.parked && gameServer.isAcceptingConnections() && globalClient.isRoomListed();
	}
	
	public void printInfo()
	{
		LOGGER.info("Room {}: {} clients; {} players; state: {}; accepting connections: {}; room listed: {}",
		        roomNum,
		        this.getNumCLients(),
		        gameServer.getNumPlayers(),
		        gameServer.getGameState(),
		        gameServer.isAcceptingConnections(),
		        globalClient.isRoomListed());
	}

	public int getNumPlayers() {
		return gameServer.getNumPlayers();
	}

	public int getGameState() {
		return gameServer.getGameState();
	}
	
	public void stop()
	{
		if (gameServer != null) {
			try { gameServer.shutdown(); } catch (Exception e) { }
		}
		globalClient.stop();
	}

	public void park() {
		this.parked = true;
		try {
		    gameServer.setAcceptingConnections(false);
		} catch (Throwable t) {
		    LOGGER.warn("Failed to set gameServer acceptingConnections to false: {}", t.toString());
		}
		try {
		    globalClient.hideRoom();
		} catch (Throwable t) {
		    LOGGER.warn("Failed to hide room in GlobalClient: {}", t.toString());
		}
	}

	public void unpark() {
		this.parked = false;
		try {
		    gameServer.setAcceptingConnections(true);
		} catch (Throwable t) {
		    LOGGER.warn("Failed to set gameServer acceptingConnections to true: {}", t.toString());
		}
		try {
		    globalClient.recreateRoom();
		} catch (Throwable t) {
		    LOGGER.warn("Failed to recreate room in GlobalClient: {}", t.toString());
		}
	}

    public void updateActiveTime() {
		if (this.getNumCLients() == 0) {
			this.lastActiveTime = System.currentTimeMillis();
		}
	}
}

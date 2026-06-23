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

import lombok.Getter;

public class Room 
{
	@Getter
    private final String name;
	@Getter
    private final int port;
	private final String ip;
	@Getter
    private int gameMode;
	@Getter
    private int numPlayers;
	@Getter
    private final int roomID;
	@Getter
	private long lastUpdate;
	
	private static int lastID = 1;
	
	public Room(String name, String ip, int port)
	{
		this.name = name;
		this.ip = ip;
		this.port = port;
		
		this.gameMode = 0;
		this.numPlayers = 0;
		this.roomID = lastID;
		
		this.lastUpdate = System.currentTimeMillis();
		
		lastID++;
	}
	
	public void updateRoom(int numPlayers, int gameMode)
	{
		this.gameMode = gameMode;
		this.numPlayers = numPlayers;
		
		this.lastUpdate = System.currentTimeMillis();
	}
	
	/*
	public long getTimeSinceLastUpdate()
	{
		long timePassed = System.currentTimeMillis() - this.lastUpdate;
		
		return timePassed;
	}*/

    public String getIp()
	{
		return this.ip;
	}
}

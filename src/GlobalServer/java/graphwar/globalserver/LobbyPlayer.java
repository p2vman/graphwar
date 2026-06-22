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
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.URLDecoder.decode;


public class LobbyPlayer implements Runnable
{
	private final Connection connection;
	private final GlobalServer globalServer;
	@Getter
    private String name;
	private final int playerID;
	private boolean running;
	@Getter
    @Setter
    private Room room;
	@Getter
    private boolean dummy;
	
	private static final AtomicInteger lastPlayerID = new AtomicInteger(0);
	
	public LobbyPlayer(Connection connection, GlobalServer globalServer)
	{
		this.connection = connection;
		this.globalServer = globalServer;
		this.running = true;
		this.playerID = lastPlayerID.getAndIncrement();
		this.name = "Player";
		this.room = null;
		this.dummy = true;
	}
	
	public String getIpAddress()
	{
		return this.connection.getIpAddress();
	}
	
	public int getID()
	{
		return this.playerID;
	}

    public boolean checkTimeout()
	{
		if(System.currentTimeMillis() - connection.getLastReceivedTime() > Constants.TIMEOUT_DROP)
		{
			return true;
		}
		
		return false;
	}

	public boolean checkStayAliveTime()
	{
		if(System.currentTimeMillis() - connection.getLastSentTime() > Constants.TIMEOUT_KEEPALIVE)
		{
			return true;
		}
		
		return false;
	}

	public void disconnect()
	{
		running = false;
		
		try 
		{
			connection.close();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	public void sendMessage(String message)
	{
		connection.sendMessage(message);
	}
	
	public void sendKeepAlive()
	{
		connection.sendMessage(NetworkProtocol.NO_INFO+"");
	}

	
	public void run()
	{		
		running = true;
		
		try {
			long start = System.currentTimeMillis();
			String incoming = null;
			while (true) {
				try {
					incoming = connection.readMessage();
				} catch (SocketTimeoutException ste) {
					if (System.currentTimeMillis() - start > Constants.TIMEOUT_DROP) {
						disconnect();
						return;
					}
					sendKeepAlive();
					continue;
				}
				if (incoming == null) {
					disconnect();
					return;
				}
				if (incoming.matches("^\\s*\\d+(?:&.*)?$")) {
					if (incoming.trim().equals(String.valueOf(NetworkProtocol.NO_INFO))) {
						sendKeepAlive();
					}
					continue;
				}
				break;
			}
			try {
				this.name = decode(incoming, "UTF-8");
			} catch (Exception e) {
				this.name = incoming;
			}
			this.dummy = this.name.equals(Constants.DUMMY_NAME);
		} catch (IOException e1) {
			e1.printStackTrace();
			disconnect();
			return;
		}
		
		this.globalServer.registerNewPlayer(this);
		this.globalServer.sendListPlayers(this);
		this.globalServer.sendListRooms(this);
		
		while(running)
		{
			try 
			{
				String message = connection.readMessage();
								
				if(message == null)
				{
					this.globalServer.removePlayer(this);
					disconnect();
				}
				else
				{
					this.globalServer.handleMessage(message, this);
										
					if(checkStayAliveTime())
					{
						sendKeepAlive();
					}
				}
			} 
			catch (SocketTimeoutException e)
			{
				if(checkTimeout())
				{
					this.globalServer.removePlayer(this);
					disconnect();
				}
				else
				{
					sendKeepAlive();
				}
			}
			catch (IOException e) 
			{
				e.printStackTrace();
				
				this.globalServer.removePlayer(this);
				disconnect();
			}
		}
	}
}

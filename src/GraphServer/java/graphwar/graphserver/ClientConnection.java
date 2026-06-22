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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientConnection
{
	@Getter
    private final Connection connection;
	@Getter
    private final ObjectList<Player> players;
	@Getter
    private final GraphServer server;
	@Setter
    @Getter
    private boolean leader;
	@Setter
    private boolean readyNextTurn;
	private boolean gameFinished;
	@Setter
    private boolean skipLevel;

	public ClientConnection(GraphServer server, Connection connection)
	{
		this.server     = server;
		this.connection = connection;

		this.players       = new ObjectArrayList<>();
		this.leader        = false;
		this.readyNextTurn = false;
		this.gameFinished  = false;
		this.skipLevel     = false;
	}

    public boolean getSkipLevel()
	{
		return skipLevel;
	}

    public boolean isFinished()
	{
		return gameFinished;
	}

	public void setFinished(boolean finished)
	{
		this.gameFinished = finished;
	}

	public void removePlayer(Player player)
	{
		players.remove(player);
	}

	public void addPlayer(Player player)
	{
		players.add(player);
	}

	public boolean getReadyNextTurn()
	{
		return this.readyNextTurn;
	}

    public boolean checkTimeout()
	{
		return System.currentTimeMillis() - connection.getLastReceivedTime() > Constants.TIMEOUT_DROP;
	}

	public boolean checkStayAliveTime()
	{
		return System.currentTimeMillis() - connection.getLastSentTime() > Constants.TIMEOUT_KEEPALIVE;
	}

	public void disconnect()
	{
		try {
			connection.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendMessage(String message)
	{
		connection.sendMessage(message);
	}

	public void sendKeepAlive()
	{
		connection.sendMessage(NetworkProtocol.NO_INFO + "");
	}

}
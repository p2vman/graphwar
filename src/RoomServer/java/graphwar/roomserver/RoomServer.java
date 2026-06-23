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
import graphwar.graphserver.commands.ArgumentsImpl;
import graphwar.graphserver.commands.IArguments;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class RoomServer implements Runnable
{
	private static final Logger LOGGER = LoggerFactory.getLogger(RoomServer.class);
	private final ObjectList<Room> rooms;
	private int numRooms;
	
	private boolean running;

	private static final int MIN_ROOMS = 2;
	private static final int MAX_ROOMS = 5;
	private static final int MAX_PLAYERS_PER_ROOM = 10;
	private static final long MAX_IDLE_TIME_MS = TimeUnit.MINUTES.toMillis(3);

	public static int INITIAL_NUM_ROOMS = 1;
	public final PortPool pool = new PortPool();

	public RoomServer()
	{
		pool.addPort(10001);
		pool.addPort(10002);
		pool.addPort(10003);
		pool.addPort(10004);
		pool.addPort(10005);
		pool.addPort(10006);
		rooms = new ObjectArrayList<>();
		
		numRooms = 0;
		
		for (int i = 0; i < INITIAL_NUM_ROOMS; i++)
		{
			try
			{
				Room room = new Room(numRooms, pool);
				rooms.add(room);
				numRooms++;
			}
			catch (IOException e)
			{
				LOGGER.error("Throw: ", e);
			}
		}
	}
	
	public void stop()
	{
		running = false;
	}


	@Override
	public void run()
	{
		running = true;

		while (running)
		{
			LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10));

			ListIterator<Room> itr = rooms.listIterator();

			while (itr.hasNext())
			{
				Room room = itr.next();
				room.printInfo();

				boolean noClients = room.getNumCLients() == 0;
				int state = room.getGameState();

				boolean isParked = room.isParked();

				boolean isIdleTimeout = noClients && (System.currentTimeMillis() - room.getLastActiveTime() > MAX_IDLE_TIME_MS);

				if ((noClients && state == Constants.NONE && isIdleTimeout) || (isParked && state == Constants.NONE))
				{
					LOGGER.info("Stopping/Restarting room {} (Reason: Idle/Parked)", room.getRoomNum());
					room.stop();
					itr.remove();

					if (!isParked)
					{
						try
						{
							Room newRoom = new Room(room.getRoomNum(), pool);
							itr.add(newRoom);
						}
						catch (IOException e)
						{
							LOGGER.error("Failed to restart room {}", room.getRoomNum(), e);
						}
					}
					else
					{
						LOGGER.info("Room {} successfully parked and removed.", room.getRoomNum());
					}
				}
			}

			boolean hasAvailableRoom = false;
			for (Room room : rooms)
			{
				int state = room.getGameState();
				if (state == Constants.PRE_GAME &&
						room.isAcceptingConnections() &&
						room.getNumPlayers() < MAX_PLAYERS_PER_ROOM &&
						!room.isParked())
				{
					hasAvailableRoom = true;
					break;
				}
			}

			if (!hasAvailableRoom && rooms.size() < MAX_ROOMS)
			{
				try
				{
					LOGGER.info("Adding room");
					Room room = new Room(numRooms, pool);
					rooms.add(room);
					numRooms++;
				}
				catch (IOException e)
				{
					LOGGER.error("Failed to create room", e);
				}
			}

			while (rooms.size() > MIN_ROOMS)
			{
				Room room = rooms.get(rooms.size() - 1);

				if (room.getGameState() != Constants.NONE || room.getNumCLients() != 0)
				{
					break;
				}

				LOGGER.info("Removing extra idle room {}", room.getRoomNum());

				room.stop();
				rooms.remove(rooms.size() - 1);
				numRooms--;
			}
		}

		LOGGER.info("Stopping");
		for (Room room : rooms)
		{
			room.stop();
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
	
	public static void main(String[] args) throws IOException {
		Terminal terminal = TerminalBuilder.builder().system(true).build();
		LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

		handleArgs(args);

		RoomServer roomServer = new RoomServer();

		Thread server = new Thread(roomServer);
		server.start();

		boolean loop = true;
		while (loop) {
			String line = reader.readLine(" > ");
			if (line == null) continue;
			IArguments arguments = new ArgumentsImpl(ObjectList.of(line.trim().split("\\s+")));
			switch (arguments.getString(0)) {
				case "exit": {
					loop = false;
					break;
				}
				case "park":
				{
					int port = arguments.getInt(1);
					for (Room room : roomServer.rooms) {
						if (room.getPort() == port)
							room.park();
					}
					break;
				}
				case "unpark":
				{
					int port = arguments.getInt(1);
					for (Room room : roomServer.rooms) {
						if (room.getPort() == port)
							room.unpark();
					}
					break;
				}
			}
		}

		server.interrupt();
	}
}

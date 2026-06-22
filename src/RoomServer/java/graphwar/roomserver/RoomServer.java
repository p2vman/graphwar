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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ListIterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomServer implements Runnable
{
	private static final Logger LOGGER = LoggerFactory.getLogger(RoomServer.class);
	private final ObjectList<Room> rooms;
	private int numRooms;
	
	private boolean running;
	
	public static int INITIAL_NUM_ROOMS = 1;

	public RoomServer()
	{
		rooms = new ObjectArrayList<>();
		
		numRooms = 0;
		
		for (int i = 0; i < INITIAL_NUM_ROOMS; i++)
		{
			try
			{
				Room room = new Room(numRooms);
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
	

	public void run() 
	{
		running = true;
		
		while(running)
		{
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10000));
			
			ListIterator<Room> itr = rooms.listIterator();
			
			int numEmpty = 0;
			while(itr.hasNext())
			{
				Room room = itr.next();
				
				room.printInfo();
				
				if(room.getNumCLients() == 0)
				{
					if(room.isAcceptingConnections())
					{
						numEmpty++;
					}
					else
					{
							LOGGER.info("Restarting room {}", room.getRoomNum());
						
						int num = room.getRoomNum();
						room.stop();
						try
						{
							Room newRoom = new Room(num);
							
							itr.remove();
							itr.add(newRoom);
						} 
						catch (IOException e)
						{
							e.printStackTrace();
						}
					}						
				}
			}

			LOGGER.info("numEmpty: {}", numEmpty);
			
			if(numEmpty<3)
			{
				try
				{
					LOGGER.info("Adding room");
					Room room = new Room(numRooms);
					rooms.add(room);
					numRooms++;
				} 
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			else if(numEmpty > 3)
			{
				Room room = rooms.get(rooms.size()-1);
				
				if(room.getNumCLients() == 0)
				{
					LOGGER.info("Removing room");

					room.stop();
					rooms.remove(room);
					numRooms--;
				}
			}
		}

		LOGGER.info("Stopping");

        for (Room room : rooms) {
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
	
	public static void main(String[] args)
	{
		handleArgs(args);

		RoomServer roomServer = new RoomServer();
		
		new Thread(roomServer).start();
		
	}
}

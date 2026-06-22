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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class RoomServer implements Runnable
{
	private final List<Room> rooms;
	private int numRooms;
	
	private boolean running;
	
	public static int INITIAL_NUM_ROOMS = 1;

	public RoomServer()
	{
		rooms = new ArrayList<Room>();
		
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
				e.printStackTrace();
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
			try 
			{
				Thread.sleep(10000);
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
			
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
						System.out.println("Restarting room "+room.getRoomNum());
						
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

			System.out.println("numEmpty: "+numEmpty);
			
			if(numEmpty<3)
			{
				try
				{
					System.out.println("Adding room");
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
					System.out.println("Removing room");

					room.stop();
					rooms.remove(room);
					numRooms--;
				}
			}
		}

		System.out.println("Stopping");
		
		ListIterator<Room> itr = rooms.listIterator();		
		while(itr.hasNext())
		{
			Room room = itr.next();			
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
		copyResourceIfMissing("/roomserver.properties", "roomserver.properties");

		java.io.File f = new java.io.File("roomserver.properties");
		if (f.exists()) {
			try (java.io.FileReader fr = new java.io.FileReader(f)) {
				java.util.Properties p = new java.util.Properties();
				p.load(fr);
				String gs = p.getProperty("globalServer");
				String init = p.getProperty("initialRooms");
				if (gs != null) {
					if (gs.contains("://")) {
						try {
							java.net.URI uri = new java.net.URI(gs);
							if (uri.getHost() != null) graphwar.graphserver.Constants.GLOBAL_IP = uri.getHost();
							if (uri.getPort() != -1) graphwar.graphserver.Constants.GLOBAL_PORT = uri.getPort();
						} catch (Exception e) { }
					} else {
						String[] hp = gs.split(":" );
						if (hp.length >= 1) graphwar.graphserver.Constants.GLOBAL_IP = hp[0];
						if (hp.length >= 2) {
							try { graphwar.graphserver.Constants.GLOBAL_PORT = Integer.parseInt(hp[1]); } catch (NumberFormatException e) {}
						}
					}
				}
				if (init != null) {
					try { INITIAL_NUM_ROOMS = Integer.parseInt(init); } catch (NumberFormatException e) {}
				}
			} catch (Exception e) { e.printStackTrace(); }
		}

		handleArgs(args);

		RoomServer roomServer = new RoomServer();
		
		new Thread(roomServer).start();
		
	}

	private static void copyResourceIfMissing(String resourcePath, String destFileName) {
		try {
			java.io.File dest = new java.io.File(destFileName);
			if (dest.exists()) return;
			java.io.InputStream in = RoomServer.class.getResourceAsStream(resourcePath);
			if (in == null) return;
			java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			in.close();
			System.out.println("Copied default config resource " + resourcePath + " to " + destFileName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

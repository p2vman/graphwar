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

package graphwar;

import graphwar.GlobalServers.ServerEntry;
import graphwar.graphserver.Constants;
import graphwar.graphserver.GraphServer;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class Graphwar extends JFrame
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Graphwar.class);

	private GraphServer gameServer;
	@Getter
    private GameData gameData;
	@Getter
    private GlobalClient globalClient;
	private GraphUI graphUI;
	
	public static void main(String[] args)
	{
		handleArgs(args);
		
		Graphwar graphwar = new Graphwar();
				
		graphwar.init();
		
		// This is this way because it was adapted from an applet
		while(true)
		{
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
	
	public void init()
	{
		copyResourceIfMissing("/global_servers.txt", "global_servers.txt");

		setTitle("Graphwar");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setPreferredSize(new Dimension(Constants.WIDTH,Constants.HEIGHT));
        pack();
		setLocationRelativeTo(null);
        setVisible(true);
        setResizable(false);
        
        
		try 
		{
			gameData = new GameData(this);
			globalClient = new GlobalClient(this);
			graphUI = new GraphUI(this); 		
		}
		catch (InterruptedException e) 
		{
			LOGGER.error("Throw: ", e);
		} 
		catch (Exception e) 
		{
			LOGGER.error("Throw: ", e);
		}
		
		this.add(graphUI);
		
		graphUI.setScreen(Constants.MAIN_MENU_SCREEN);
		
		this.validate();
		this.repaint();
	}
	
	public void start()
	{
		
	}
	
	public void stop()
	{
		globalClient.stop();
		gameData.disconnect();
		graphUI.stop();
		
		if(gameServer != null)
		{
			gameServer.shutdown();
			gameServer = null;
		}
	}
	
	public void destroy()
	{
		
	}
	
	public GraphUI getUI()
	{
		return graphUI;
	}

	private void copyResourceIfMissing(String resourcePath, String destFileName) {
		try {
			java.io.File dest = new java.io.File(destFileName);
			if (dest.exists()) return;
			java.io.InputStream in = Graphwar.class.getResourceAsStream(resourcePath);
			if (in == null) return;
			java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			in.close();
			LOGGER.info("Copied default config resource {} to {}", resourcePath, destFileName);
		} catch (Exception e) {
			LOGGER.error("Throw: ", e);
		}
	}

    public void finishGame()
	{
		if(gameServer != null)
		{
			gameServer.shutdown();
			gameServer = null;
		}
	}
	
	public void joinGlobal(String name) throws IOException
	{
		List<ServerEntry> servers = GlobalServers.load();

		String[] options = new String[servers.size()];
		for (int i = 0; i < servers.size(); i++) options[i] = servers.get(i).toString();

		String choice = (String) JOptionPane.showInputDialog(this,
		        "Select Global server:",
		        "Choose server",
		        JOptionPane.PLAIN_MESSAGE,
		        null,
		        options,
		        options[0]);

		if (choice == null) return;

		ServerEntry sel = null;
		for (ServerEntry s : servers) if (s.toString().equals(choice)) { sel = s; break; }
		if (sel == null) sel = servers.get(0);

		globalClient.joinGlobalServer(sel.host, sel.port, name);

		graphUI.setScreen(Constants.GLOBAL_ROOM_SCREEN);
	}
	
	public void joinGame(String ip, int port) throws IOException
	{
		gameData.connect(ip, port);		
	}
	
	public void createGame(int port) throws IOException
	{
		gameServer = new GraphServer(port);
		new Thread(gameServer).start();
		
		gameData.connect("localhost", port);
		
		graphUI.setScreen(Constants.PRE_GAME_SCREEN);
	}
}

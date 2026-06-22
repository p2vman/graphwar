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

import graphwar.graphserver.Constants;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;

public class Player
{
	@Getter
    private final String name;
	@Getter
    @Setter
    protected int team;
	@Getter
    private int numSoldiers;
	@Getter
    protected Soldier[] soldiers;
	protected int currentTurnSoldier;
	private final int playerID;
	@Getter
    private final boolean localPlayer;
	@Getter
    private final Color color;
	@Setter
    private boolean ready;
	@Getter
    private final int nameLength;
	@Getter
    private boolean disconnected;
	
	public Player(String name, int playerID, int team, boolean localPlayer, int numSoldiers, boolean ready)
	{
		this.name = name;		
		this.team = team;	
		this.numSoldiers = numSoldiers;
		
		this.soldiers = new Soldier[Constants.MAX_SOLDIERS_PER_PLAYER];
			for(int i=0; i<soldiers.length; i++)
			{
				soldiers[i] = new Soldier();
			}
		this.currentTurnSoldier = 0;
			
		this.localPlayer = localPlayer;
		
		this.playerID = playerID;
		
		this.color = GraphUtil.getRandomColor();
		
	@SuppressWarnings("deprecation")
		FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics (Constants.NAME_FONT);		
		this.nameLength = fontMetrics.stringWidth(name);
		
		this.disconnected = false;
	}

    public void markDisconnected()
	{
		this.disconnected = true;
	}

    public boolean getReady()
	{
		return this.ready;
	}

    public void startSoldier(int soldierNum, int x, int y)
	{
		this.soldiers[soldierNum] = new Soldier(x, y);
	}
	
	public void setSoldiers(int numSoldiers)
	{
		this.numSoldiers = numSoldiers;
	}

    public int getCurrentTurnSoldierIndex()
	{
		return this.currentTurnSoldier;
	}
	
	public Soldier getCurrentTurnSoldier()
	{
		return soldiers[currentTurnSoldier];
	}
	
	public int getID()
	{
		return this.playerID;
	}

    public void restartTurn()
	{
		currentTurnSoldier = 0;
	}
	
	public Soldier getNextTurnSoldier()
	{
		int nextSoldier = currentTurnSoldier;
		
		for(int i=0; i<numSoldiers; i++)
		{			
			nextSoldier = (nextSoldier+1)%numSoldiers;
			
			if(soldiers[nextSoldier].isAlive())
			{
				return soldiers[nextSoldier];
			}
		}
		
		return null;
	}
	
	public boolean nextTurn()
	{		
		for(int i=0; i<numSoldiers; i++)
		{
			currentTurnSoldier = (currentTurnSoldier+1)%numSoldiers;
			
			if(soldiers[currentTurnSoldier].isAlive())
			{
				return true;
			}
		}
		
		return false;
	}
}

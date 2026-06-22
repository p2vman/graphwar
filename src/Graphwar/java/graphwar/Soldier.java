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

import java.security.SecureRandom;
import java.util.Random;

public class Soldier
{
	@Getter
    private int x;
	@Getter
    private int y;
	@Setter
    @Getter
    private double angle;
	
	@Setter
    @Getter
    private boolean alive;
	@Getter
    private boolean exploding;
	private long timeExplodingStarted;
	@Setter
    @Getter
    private int killPosition;
	
	@Setter
    @Getter
    private String function;
	
	private boolean animating;
	@Getter
    private int animationNum;
	private long timeAnimationStarted;
	private long nextAnimation;
	
	private final static Random random = new Random();
	
	public Soldier()
	{
		this.x = 0;
		this.y = 0;
		
		this.angle = 0;
		
		this.alive = false;
		this.exploding = false;
		
		this.function = "";
	}
	
	public Soldier(int x, int y)
	{
		this.x = x;
		this.y = y;
		
		this.angle = 0;
		
		this.alive = true;
		
		this.function = "";
	}

    public void setExploding(boolean exploding)
	{
		this.exploding = exploding;
		
		if(exploding == true)
		{
			timeExplodingStarted = System.currentTimeMillis();
		}
	}
	
	public long getTimeExploding()
	{
		return System.currentTimeMillis() - timeExplodingStarted;
	}

    public boolean isAnimating()
	{
		if(animating)
		{
			return true;
		}
		else
		{
			if(System.currentTimeMillis() > nextAnimation)
			{
				animating = true;
				timeAnimationStarted = System.currentTimeMillis();
				animationNum = random.nextInt(Integer.MAX_VALUE);
				return true;
			}
			
			return false;
		}
	}

    public long getAnimationTime()
	{
		return System.currentTimeMillis() - timeAnimationStarted;
	}
	
	public void endAnimation()
	{
		animating = false;
		
		nextAnimation = (long) (System.currentTimeMillis() + Math.abs(random.nextGaussian()*Constants.SOLDIER_ANIMATION_DELAY_STANDARD_DEVIATION + Constants.SOLDIER_ANIMATION_MEAN_VALUE));
	}
	
}

package captureFlagPackage;

import java.util.ArrayList;

import lejos.hardware.motor.EV3LargeRegulatedMotor;
import modulePackage.*;
import basicPackage.*;

/**
 *A class which is responsible for locating and capturing the flag 
 *@author Fred Glozman, Abdel Kader Gaye 
 */
public class CaptureFlag extends Thread implements IObserver
{	
	//colorID value of the target flag. want to locate and capture the object with this colorID
	private final int targetFlagColorID; 
	
	//locates objects
	private LocateObject locator;
	
	//identifies objects
	private IdentifyObject identifier;
	
	//grabs object
	private PickupObject grabber; 
		
	//robot navigation and sensing
	private Navigation nav;	
	private Odometer odo;
	private UltrasonicModule us; 
	private ColorDetection cd; 
		
	//stores the location of the robot prior to navigating towards a located block
	//[x,y,theta]
	private double[] locationPreIdentifier; 
	
	ArrayList<double[]> objectsLocation;


	/**
	 *Constructor
	 *@param objectID the colorID value of the target flag
	 *@param navigator contains methods which navigates the robot 
	 *@param odometer keeps track of the robot's position 
	 *@param usm access to the ultrasonic sensor 
	 *@param cd access to the light sensor. color detection feature. 
	 *@param robotArmMotor is the motor that controls the robot's arm
	 */
	public CaptureFlag(int objectID, Navigation navigator, Odometer odometer, UltrasonicModule usm, ColorDetection cd, EV3LargeRegulatedMotor robotArmMotor)
	{
		this.targetFlagColorID = objectID;
		this.nav = navigator; 
		this.odo = odometer;
		this.us = usm; 
		this.cd = cd;
		
		locator = new LocateObject(this, nav, odo, us, this.cd);
		identifier = new IdentifyObject(this, us, this.cd);
		grabber = new PickupObject(robotArmMotor, navigator);	
		
		this.locationPreIdentifier = null;
	}

	/**
	 *Overrides the run method in the Thread superclass
	 */
	@Override
	public void run()
	{
		//start looking for object (it's initialized to active and not paused)
		locator.start();
		
		//start identifier. (it's initialized to active but paused)
		identifier.start();
	}
	
	/**
	 *Method required by the IObserver interface.
	 *LocateObject and IdentifyObject call this method in order to notify CaptureFlag of an event.
	 *This method then reacts to the event.
	 *@param x caller class unique identifier
	 *@throws InvalidCallerID if update is called with an invalid class caller ID
	 */
	public void update(ClassID x) 
	{
		//switch on the caller class ID
		switch (x)
		{
		
			//caller is LocateObject 
			case LOCATEOBJECT:
				
				//get the location of the found object
				objectsLocation = locator.getCurrentObjLoco();
				double[] currentObject = objectsLocation.remove(0);
				
				//if the location is not null (just being careful...)
				if(currentObject!=null)
				{
					//pause locator
					locator.pauseThread();
					
					//save the current locaiton of the robot
					locationPreIdentifier = odo.getPosition();
					
					//navigate towards object
					nav.travelTo(currentObject[0], currentObject[1]);
					
					//identify object
					identifier.resumeThread();
				}
												
				break;
				
			//caller is IdentifyObject 
			case IDENTIFYOBJECT:
				int objectColorID = identifier.getObjectID();
				
				//if the object is the target flag
				if(objectColorID == targetFlagColorID)
				{
					//end identifier
					identifier.deactivateThread();
					
					//end locator
					locator.deactivateThread();
					
					//pickup flag
					grabber.doPickup();
				}
				else
				{
					//pause identifier
					identifier.pauseThread();
					
					//navigate back to where you were prior to navigating towards object (check for null location. just to be safe)
					if(locationPreIdentifier != null)
					{
						nav.travelTo(locationPreIdentifier[0], locationPreIdentifier[1]);
						nav.turnTo(locationPreIdentifier[2], true);
						
						//reset saved location for next iteration
						locationPreIdentifier = null;
					}
					
					//if there are more objects to identify. 
					//call update again with LOCATEOBJECT
					if(!objectsLocation.isEmpty())
					{
						update(ClassID.LOCATEOBJECT);
					}
					else
					{
						//resume locator
						locator.resumeThread();
					}
				}
												
				break;	
		}
	}
}
package com.orbotix.sample.helloworld;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import orbotix.macro.MacroObject;
import orbotix.macro.MacroObject.MacroObjectMode;
import orbotix.robot.app.StartupActivity;
import orbotix.robot.base.AbortMacroCommand;
import orbotix.robot.base.RGBLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotControl;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.base.StabilizationCommand;

import com.orbotix.sample.helloworld.FileManager;
import com.orbotix.sample.helloworld.R;

/**
 * Connects to an available Sphero robot, and then flashes its LED.
 */
public class HelloWorldActivity extends Activity
{
    /**
     * ID for launching the StartupActivity for result to connect to the robot
     */
    private final static int STARTUP_ACTIVITY = 0;

    /**
     * The Sphero Robot
     */
    private Robot mRobot;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
   
    	ImageButton macrobutton1 = (ImageButton) findViewById(R.id.button1);  
    	macrobutton1.setOnClickListener(new View.OnClickListener() { 
    		
    	    public void onClick(View v) {  
                
    	        AbortMacroCommand.sendCommand(mRobot);
    	        StabilizationCommand.sendCommand(mRobot, true);
    	        RGBLEDOutputCommand.sendCommand(mRobot, 255, 255, 255);
    	        
    	    	if(mRobot != null){
                	FileManager files= new FileManager();
                    MacroObject macro= null;
                    try {
						 macro = files.getMacro(v.getContext(), "colorm.sphero");
						 macro.setMode(MacroObjectMode.Normal);
	                     macro.setRobot(mRobot);
	                     macro.playMacro(); 
					} catch (IOException e) {
						throw new RuntimeException(e);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
                }
    	    }  
    	});
    
    	
    	ImageButton macrobutton2 = (ImageButton) findViewById(R.id.button1);  
    	macrobutton2.setOnClickListener(new View.OnClickListener() { 
    		
    	    public void onClick(View v) {  
    	       
    	    	AbortMacroCommand.sendCommand(mRobot);
    	        StabilizationCommand.sendCommand(mRobot, true);
    	        RGBLEDOutputCommand.sendCommand(mRobot, 255, 255, 255);
    	    	
    	    	if(mRobot != null){
                	FileManager files= new FileManager();
                    MacroObject macro= null;
                    try {
						 macro = files.getMacro(v.getContext(), "colorm.sphero");
						 macro.setMode(MacroObjectMode.Normal);
	                     macro.setRobot(mRobot);
	                     macro.playMacro(); 
					} catch (IOException e) {
						throw new RuntimeException(e);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
                }
    	    }  
    	});
    	
    	
    	ImageButton stopbutton = (ImageButton) findViewById(R.id.button1);  
    	stopbutton.setOnClickListener(new View.OnClickListener() { 
    		
    	    public void onClick(View v) {  
    	        AbortMacroCommand.sendCommand(mRobot);
    	        StabilizationCommand.sendCommand(mRobot, true);
    	        RGBLEDOutputCommand.sendCommand(mRobot, 255, 255, 255);
    	    }  
    	});

    }

    
    
    
    @Override
    protected void onStart() {
    	super.onStart();

    	//Launch the StartupActivity to connect to the robot
        Intent i = new Intent(this, StartupActivity.class);
        startActivityForResult(i, STARTUP_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if(requestCode == STARTUP_ACTIVITY && resultCode == RESULT_OK){

            //Get the connected Robot
            final String robot_id = data.getStringExtra(StartupActivity.EXTRA_ROBOT_ID);
            if(robot_id != null && !robot_id.equals("")){
                mRobot = RobotProvider.getDefaultProvider().findRobot(robot_id);
            }
            
            //Start blinking
            blink(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        mRobot = null;

        //Disconnect Robot
        RobotProvider.getDefaultProvider().removeAllControls();
    }

    /**
     * Causes the robot to blink once every second.
     * @param lit
     */
    private void blink(final boolean lit){
        
        if(mRobot != null){
            
            //If not lit, send command to show blue light, or else, send command to show no light
            if(lit){
                RGBLEDOutputCommand.sendCommand(mRobot, 0, 0, 0);
            }else{
                RGBLEDOutputCommand.sendCommand(mRobot, 0, 0, 255);
            }
            
            //Send delayed message on a handler to run blink again
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    blink(!lit);
                }
            }, 1000);
        }
    }
}

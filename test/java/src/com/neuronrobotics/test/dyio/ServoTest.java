package com.neuronrobotics.test.dyio;
import com.neuronrobotics.sdk.dyio.DyIO;
import com.neuronrobotics.sdk.dyio.peripherals.ServoChannel;
import com.neuronrobotics.sdk.ui.ConnectionDialog;

public class ServoTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		DyIO dyio=new DyIO();
		if (!ConnectionDialog.getBowlerDevice(dyio)){
			System.exit(1);
		}
		ServoChannel srv = new ServoChannel (dyio.getChannel(12));
                //Loop 10 times setting the position of the servo 
                //the time the loop waits will be the time it takes for the servo to arrive
		float time = 5;
		for(int i = 0; i < 10; i++) {
			System.out.println("Moving.");
			// Set the value high every other time, exit if unsuccessful
			int pos = ((i%2==0)?255:0);
                        //This will move the servo from the position it is currentlly in
			srv.SetPosition(pos, time);
			
			// pause between cycles so that the changes are visible
			try {
				Thread.sleep((long) (time*1000));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
        dyio.disconnect();
        System.exit(0);


	}

}
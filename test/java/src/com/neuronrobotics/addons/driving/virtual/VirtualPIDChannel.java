package com.neuronrobotics.addons.driving.virtual;

import java.util.ArrayList;

import com.neuronrobotics.sdk.common.BowlerAbstractCommand;
import com.neuronrobotics.sdk.common.BowlerDatagram;
import com.neuronrobotics.sdk.common.InvalidResponseException;
import com.neuronrobotics.sdk.common.NoConnectionAvailableException;
import com.neuronrobotics.sdk.genericdevice.GenericPIDDevice;
import com.neuronrobotics.sdk.pid.PIDChannel;
import com.neuronrobotics.sdk.pid.PIDEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class VirtualPIDChannel extends GenericPIDDevice{
	private ArrayList<DriveThread>  driveThreads = new  ArrayList<DriveThread>();

	private double maxTicksPerSecond;
	
	private int numChannels = 10;
	
	
	public  VirtualPIDChannel( double maxTicksPerSecond) {
		this.maxTicksPerSecond=maxTicksPerSecond;
		
	}
	/**
	 * since there is no connection, this is an easy to nip off com functionality
	 *
	 */
	@Override
	public BowlerDatagram send(BowlerAbstractCommand command) throws NoConnectionAvailableException, InvalidResponseException {	
		//do nothing
		return null;
	}
	@Override
	public boolean ResetPIDChannel(int group, int valueToSetCurrentTo) {
		driveThreads.get(group).ResetEncoder(valueToSetCurrentTo);
		int val = GetPIDPosition(group);
		firePIDResetEvent(group,val);
		return true;
	}

	@Override
	public boolean SetPIDSetPoint(int group, int setpoint, double seconds) {
		driveThreads.get(group).SetPIDSetPoint(setpoint, seconds);
		return true;
	}
	@Override
	public boolean SetAllPIDSetPoint(int[] setpoints, double seconds) {
		for(int i=0;i<setpoints.length;i++){
			 SetPIDSetPoint(i,  setpoints[i], seconds);
		}
		return true;
	}
	@Override
	public int GetPIDPosition(int group) {
		// TODO Auto-generated method stub
		return driveThreads.get(group).getPosition();
	}
	@Override
	public int[] GetAllPIDPosition() {
		//This is the trigger to populate the number of PID channels
		int [] back = new int[numChannels];
		if(back.length != channels.size()){
			channels =  new ArrayList<PIDChannel>();
			lastPacketTime =  new long[back.length];
			for(int i=0;i<back.length;i++){
				back[i]=0;
				PIDChannel c =new PIDChannel(this,i);
				c.setCachedTargetValue(back[i]);
				channels.add(c);
				DriveThread d = new DriveThread(i);
				d.start();
				driveThreads.add(d);
			}
		}
		return back;
	}

	
	
	/**
	 * This class is designed to simulate a wheel driveing with a perfect controller
	 * @author hephaestus
	 *
	 */
	
	
	private class DriveThread extends Thread{

		private static final long threadTime=200;
		
		private long ticks=0;
		private long lastTick=ticks;
		private long setPoint;
		private long duration;
		private long startTime;
		private long startPoint;
		boolean pause = false;
		private int chan;
		public DriveThread(int index){
			setChan(index);
		}
		public int getPosition() {
			return (int) ticks;
		}
		public synchronized  void SetPIDSetPoint(int setpoint,double seconds){
			pause=true;
			ThreadUtil.wait((int)(threadTime*2));
			double TPS = (double)setpoint/seconds;
			//Models motor saturation
			if(TPS >  maxTicksPerSecond){
				//seconds = (double)setpoint/maxTicksPerSeconds;
				throw new RuntimeException("Saturated PID on channel: "+chan+" Attempted Ticks Per Second: "+TPS+", when max is"+maxTicksPerSecond+" set: "+setpoint+" sec: "+seconds);
			}
			duration = (long) (seconds*1000);
			startTime=System.currentTimeMillis();
			setPoint=setpoint;
			startPoint = ticks;
			
			pause=false;
			//System.out.println("Setting Setpoint Ticks to: "+setPoint);
		}
		public void run() {
			while(true) {
				while(pause){
					ThreadUtil.wait(10);
				}
				try {Thread.sleep(threadTime);} catch (InterruptedException e) {}
				interpolate();
				if(ticks!=lastTick) {
					lastTick=ticks;
					firePIDEvent(new PIDEvent(getChan(), (int)ticks, System.currentTimeMillis(),0));
				}
			}
		}
		public synchronized  void ResetEncoder(int value) {
			System.out.println("Resetting channel "+getChan());
			pause=true;
			ThreadUtil.wait((int)(threadTime*2));
			ticks=value;
			lastTick=value;
			setPoint=value;
			duration=0;
			startTime=System.currentTimeMillis();
			startPoint=value;
			pause=false;
		}
		private void setChan(int chan) {
			this.chan = chan;
		}
		public int getChan() {
			return chan;
		}
		private void interpolate() {
			float back;
			float diffTime;
			if(duration > 0){
				diffTime = System.currentTimeMillis()-startTime;
				if((diffTime < duration) && (diffTime>0)){
					float elapsed = 1-((duration-diffTime)/duration);
					float tmp=((float)startPoint+(float)(setPoint-startPoint)*elapsed);
					if(setPoint>startPoint){
						if((tmp>setPoint)||(tmp<startPoint))
							tmp=setPoint;
					}else{
						if((tmp<setPoint) || (tmp>startPoint))
							tmp=setPoint;
					}
					back=tmp;
				}else{
					// Fixes the overflow case and the timeout case
					duration=0;
					back=setPoint;
				}
			}else{
				back=setPoint;
				duration = 0;
			}
			ticks = (long) back;
		}
	}
	
}

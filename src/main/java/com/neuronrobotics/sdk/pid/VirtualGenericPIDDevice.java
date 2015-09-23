package com.neuronrobotics.sdk.pid;

import java.util.ArrayList;

import com.neuronrobotics.sdk.common.BowlerAbstractCommand;
import com.neuronrobotics.sdk.common.BowlerDatagram;
import com.neuronrobotics.sdk.common.InvalidConnectionException;
import com.neuronrobotics.sdk.common.InvalidResponseException;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.NoConnectionAvailableException;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class VirtualGenericPIDDevice extends GenericPIDDevice{
	
	private static final long threadTime=10;
	
	private ArrayList<DriveThread>  driveThreads = new  ArrayList<DriveThread>();
	private ArrayList<PIDConfiguration>  configs = new  ArrayList<PIDConfiguration>();
	private ArrayList<PDVelocityConfiguration>  PDconfigs = new  ArrayList<PDVelocityConfiguration>();
	SyncThread sync = new SyncThread ();
	private double maxTicksPerSecond;
	
	private int numChannels = 24;
	
	public  VirtualGenericPIDDevice( ) {
		this(1000000);
	}
	public  VirtualGenericPIDDevice( double maxTicksPerSecond) {
		this.setMaxTicksPerSecond(maxTicksPerSecond);
		getImplementation().setChannelCount(new Integer(numChannels));
		GetAllPIDPosition();
		for(int i=0; i<numChannels;i++){
			configs.add(new PIDConfiguration());
			PDconfigs.add(new PDVelocityConfiguration());
		}
			
		sync.start();
		
	}
	
	@Override
	public boolean ConfigurePDVelovityController(PDVelocityConfiguration config) {
		PDconfigs.set(config.getGroup(), config);
		
		return true;
	}

	@Override
	public PDVelocityConfiguration getPDVelocityConfiguration(int group) {
		return PDconfigs.get(group);
	}
	
	
	public boolean ConfigurePIDController(PIDConfiguration config) { 
		configs.set(config.getGroup(), config);
		
		return true;
	}
	public PIDConfiguration getPIDConfiguration(int group) {
		return configs.get(group);
	}
	
	@Override 
	public ArrayList<String> getNamespaces(){
		ArrayList<String> s = new ArrayList<String>();
		s.add("bcs.pid.*");
		return s;
	}

	@Override
	public boolean killAllPidGroups() {
		for(PIDConfiguration c:configs)
			c.setEnabled(false);
		return true;
	}
	

	/**
	 * since there is no connection, this is an easy to nip off com functionality
	 *
	 */
	@Override
	public BowlerDatagram send(BowlerAbstractCommand command) throws NoConnectionAvailableException, InvalidResponseException {	
		RuntimeException r = new RuntimeException("This method is never supposed to be called in the virtual PID");
		r.printStackTrace();
		throw r;
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
		Log.info("Virtual setpoint, group="+group+" setpoint="+setpoint);
		driveThreads.get(group).SetPIDSetPoint(setpoint, seconds);
		return true;
	}
	@Override
	public boolean SetPDVelocity(int group, int unitsPerSecond, double seconds)throws PIDCommandException {
		if(unitsPerSecond>getMaxTicksPerSecond())
			throw new RuntimeException("Saturated PID on channel: "+group+" Attempted Ticks Per Second: "+unitsPerSecond+", when max is"+getMaxTicksPerSecond()+" set: "+getMaxTicksPerSecond()+" sec: "+seconds);
		if(unitsPerSecond<-getMaxTicksPerSecond())
			throw new RuntimeException("Saturated PID on channel: "+group+" Attempted Ticks Per Second: "+unitsPerSecond+", when max is"+getMaxTicksPerSecond()+" set: "+getMaxTicksPerSecond()+" sec: "+seconds);
		if(seconds<0.1 && seconds>-0.1){
			//System.out.println("Setting virtual velocity="+unitsPerSecond);
			driveThreads.get(group).SetVelocity(unitsPerSecond);
		}
		else{
			SetPIDInterpolatedVelocity(group, unitsPerSecond, seconds);
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.neuronrobotics.sdk.namespace.bcs.pid.IPidControlNamespace#flushPIDChannels
	 */
	@Override
	public void flushPIDChannels(double time) {
		int [] data = new int[getChannels().size()];
		for(int i=0;i<data.length;i++){
			data[i]=getPIDChannel(i).getCachedTargetValue();
		}
		Log.info("Flushing in "+time+"ms");
		SetAllPIDSetPoint(data, time);
	}
	
	
	@Override
	public boolean SetAllPIDSetPoint(int[] setpoints, double seconds) {
		sync.setPause(true);
		for(int i=0;i<setpoints.length;i++){
			SetPIDSetPoint(i,  setpoints[i], seconds);
		}
		sync.setPause(false);
		return true;
	}
	@Override
	public int GetPIDPosition(int group) {
		// TODO Auto-generated method stub
		return driveThreads.get(group).getPosition();
	}
	@Override
	public boolean isAvailable(){
		return true;
	}
	@Override
	public int[] GetAllPIDPosition() {
		//This is the trigger to populate the number of PID channels
		int [] back = new int[numChannels];

		setChannels(  new ArrayList<PIDChannel>());
		//lastPacketTime =  new long[back.length];
		for(int i=0;i<back.length;i++){
			back[i]=0;
			PIDChannel c =new PIDChannel(this,i);
			c.setCachedTargetValue(back[i]);
			getChannels().add(c);
			DriveThread d = new DriveThread(i);
			driveThreads.add(d);
			configs.add(new PIDConfiguration());
		}
		return back;
	}
	
	
	public void setMaxTicksPerSecond(double maxTicksPerSecond) {
		this.maxTicksPerSecond = maxTicksPerSecond;
	}
	public double getMaxTicksPerSecond() {
		return maxTicksPerSecond;
	}



	/**
	 * This class is designed to simulate a wheel driveing with a perfect controller
	 * @author hephaestus
	 *
	 */
	private class SyncThread extends Thread{
		
		private boolean pause =false;
		public void run() {
			setName("Bowler Platform Virtual PID sync thread");
			while(true) {
				try {Thread.sleep(threadTime);} catch (InterruptedException e) {}
				while(isPause()){
					ThreadUtil.wait(10);
				}
				long time = System.currentTimeMillis();
				for(DriveThread dr : driveThreads){
					if(dr.update()){
						try{
							firePIDEvent(new PIDEvent(dr.getChan(), (int)dr.ticks, time,0));
						}catch (Exception ex){
							ex.printStackTrace();
						}
					}
				}
			}
		}
	
		public boolean isPause() {
			return pause;
		}
		public void setPause(boolean pause) {
			if(pause)
				try {Thread.sleep(threadTime*2);} catch (InterruptedException e) {}
			this.pause = pause;
		}
	}
	
	private class DriveThread {
		
		private double ticks=0;
		private double lastTick=ticks;
		private double lastInterpolationTime;
		private double setPoint;
		private double duration;
		private double startTime;
		private double startPoint;
		private boolean pause = false;
		private boolean velocityRun=false;
		private double unitsPerMs;
		private int chan;
		public DriveThread(int index){
			setChan(index);
		}
		public void SetVelocity(double unitsPerSecond) {
			//System.out.println("Setting velocity to "+unitsPerSecond+"ticks/second");
			setPause(true);
			
			this.unitsPerMs=unitsPerSecond/1000;
			lastInterpolationTime=System.currentTimeMillis();
			velocityRun=true;
			
			setPause(false);
		}
		public int getPosition() {
			return (int) ticks;
		}
		
		
		
		public synchronized  void SetPIDSetPoint(int setpoint,double seconds){
			configs.get(getChan()).setEnabled(true);
			velocityRun=false;
			setPause(true);
			//ThreadUtil.wait((int)(threadTime*2));
			double TPS = (double)setpoint/seconds;
			//Models motor saturation
			if(TPS >  getMaxTicksPerSecond()){
				seconds = (double)setpoint/ getMaxTicksPerSecond();
				//throw new RuntimeException("Saturated PID on channel: "+chan+" Attempted Ticks Per Second: "+TPS+", when max is"+getMaxTicksPerSecond()+" set: "+setpoint+" sec: "+seconds);
			}
			duration = (long) (seconds*1000);
			startTime=System.currentTimeMillis();
			setPoint=setpoint;
			startPoint = ticks;
			
			setPause(false);
			//System.out.println("Setting Setpoint Ticks to: "+setPoint);
		}
		private boolean update(){
			if(configs.get(getChan()).isEnabled())
				interpolate();
			if((ticks!=lastTick) && !isPause()) {
				lastTick=ticks;
				return true;
			}	
			return false;
		}

		public synchronized  void ResetEncoder(int value) {
			System.out.println("Resetting channel "+getChan());
			velocityRun=false;
			setPause(true);
			//ThreadUtil.wait((int)(threadTime*2));
			ticks=value;
			lastTick=value;
			setPoint=value;
			duration=0;
			startTime=System.currentTimeMillis();
			startPoint=value;
			setPause(false);	
		}
		private void setChan(int chan) {
			this.chan = chan;
		}
		public int getChan() {
			return chan;
		}
		private void interpolate() {
			double back;
			double diffTime;
			if(duration > 0 ){
				diffTime = System.currentTimeMillis()-startTime;
				if((diffTime < duration) && (diffTime>0) ){
					double elapsed = 1-((duration-diffTime)/duration);
					double tmp=((float)startPoint+(float)(setPoint-startPoint)*elapsed);
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
			if(velocityRun){
				double ms = (double) (System.currentTimeMillis()-lastInterpolationTime);
				back=(ticks+unitsPerMs*ms);
				System.out.println("Time Diff="+ms+" \n\ttick difference="+unitsPerMs*ms+" \n\tticksPerMs="+unitsPerMs +" \n\tCurrent value="+back );
			}
			ticks = back;
			lastInterpolationTime=System.currentTimeMillis();
		}
		public boolean isPause() {
			return pause;
		}
		private void setPause(boolean pause) {
			this.pause = pause;
		}
	}
	@Override
	public boolean connect(){
		fireConnectEvent();
		return true;
	}
	
	/**
	 * This method tells the connection object to disconnect its pipes and close out the connection. Once this is called, it is safe to remove your device.
	 */
	@Override
	public void disconnect(){
		fireDisconnectEvent();
		
	}
	
}

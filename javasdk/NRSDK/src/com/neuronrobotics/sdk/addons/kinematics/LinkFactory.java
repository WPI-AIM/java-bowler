package com.neuronrobotics.sdk.addons.kinematics;
import java.util.ArrayList;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.dyio.DyIO;
import com.neuronrobotics.sdk.dyio.peripherals.AnalogInputChannel;
import com.neuronrobotics.sdk.dyio.peripherals.ServoChannel;
import com.neuronrobotics.sdk.namespace.bcs.pid.IPidControlNamespace;
import com.neuronrobotics.sdk.pid.GenericPIDDevice;
import com.neuronrobotics.sdk.pid.ILinkFactoryProvider;

public class LinkFactory {
	private IPidControlNamespace pid=null;
	private DyIO dyio=null;
	//private VirtualGenericPIDDevice virtual = new VirtualGenericPIDDevice(1000000);
	private boolean hasPid=false;
	private boolean hasServo=false;
	private boolean hasStepper=false;
	private boolean forceVirtual = false;
	private ArrayList<AbstractLink> links = new ArrayList<AbstractLink>();
	private ArrayList<LinkConfiguration> linkConfigurations=null ;
	public LinkFactory (){
		hasPid=false;
		hasServo=false;
		hasStepper=false;
		forceVirtual=true;
	}
	
	public LinkFactory (DyIO d){
		if(d==null){
			forceVirtual=true;
			return;
		}
		dyio=d;
		pid=d;
		hasPid=true;
		hasServo=true;
		hasStepper=true;
	}
	public LinkFactory (GenericPIDDevice d){
		if(d==null){
			forceVirtual=true;
			//pid=virtual;
			hasPid=true;
			return;
		}
		pid=d;
		hasPid=true;
	}
	
	public LinkFactory(ILinkFactoryProvider connection,GenericPIDDevice d) {
		pid=d;
		hasPid=true;
		//Log.enableInfoPrint();
		//TODO fill in the auto link configuration
		LinkConfiguration first = connection.requestLinkConfiguration(0);
		first.setPidConfiguration( pid);
		getLink(first);
		
		for (int i=1;i<first.getTotlaNumberOfLinks();i++){
			LinkConfiguration tmp = connection.requestLinkConfiguration(i);
			tmp.setPidConfiguration(pid);
			getLink(tmp);
		}
		
	}
	
	

	public AbstractLink getLink(String name) {
		for(AbstractLink l:links){
			if(l.getLinkConfiguration().getName().equalsIgnoreCase(name))
				return l;
		}
		String data = "No linke of name '"+name+"' exists";
		for(AbstractLink l:links){
			data +="\n"+l.getLinkConfiguration().getName();
		}
		throw new RuntimeException(data);
	}
	
	public AbstractLink getLink(LinkConfiguration c){
		for(AbstractLink l:links){
			if(l.getLinkConfiguration() == c)
				return l;
		}
		AbstractLink tmp=null;
		if(!forceVirtual){
			if(c.getType().equals("servo-rotory")){
				
				tmp = new ServoRotoryLink(	new ServoChannel(dyio.getChannel(c.getHardwareIndex())), 
											(int)c.getIndexLatch(),
											(int)c.getLowerLimit(),
											(int)c.getUpperLimit(),
											c.getScale());
			}else if(c.getType().equals("analog-rotory")){
				
				tmp = new AnalogRotoryLink(	new AnalogInputChannel(dyio.getChannel(c.getHardwareIndex())), 
											(int)c.getIndexLatch(),
											(int)c.getLowerLimit(),
											(int)c.getUpperLimit(),
											c.getScale());
			} else if (c.getType().equals("dummy")){
//				tmp=new PidRotoryLink(	virtual.getPIDChannel(c.getHardwareIndex()),
//						(int)0,
//						(int)c.getLowerLimit(),
//						(int)c.getUpperLimit(),
//						c.getScale());
				tmp.setUseLimits(false);
			}else{
				tmp=new PidRotoryLink(	pid.getPIDChannel(c.getHardwareIndex()),
										(int)0,
										(int)c.getLowerLimit(),
										(int)c.getUpperLimit(),
										c.getScale());
			}
		}else{
			
			int home=0;
//			if(c.getType().equals("servo-rotory"))
//				home = c.getIndexLatch();
//			tmp=new PidRotoryLink(	virtual.getPIDChannel(c.getHardwareIndex()),
//					(int)home,
//					(int)c.getLowerLimit(),
//					(int)c.getUpperLimit(),
//					c.getScale());
			//tmp.setUseLimits(false);
		}
		tmp.setLinkConfiguration(c);
		links.add(tmp);
		return tmp;
	}
	
	public double [] getLowerLimits(){
		double [] up = new double [links.size()];
		for(int i=0;i< up.length;i++){
			up[i] = links.get(i).getMinEngineeringUnits();
		}
		return up;
	}
	
	public double [] getUpperLimits(){
		double [] up = new double [links.size()];
		for(int i=0;i< up.length;i++){
			up[i] = links.get(i).getMaxEngineeringUnits();
		}
		return up;
	}
	
	public void addLinkListener(ILinkListener l){
		for(AbstractLink lin:links){
			lin.addLinkListener(l);
		}
	}
	public void flush(final double seconds){
		long time = System.currentTimeMillis();
		if(hasServo){
			dyio.flushCache(seconds);
			Log.info("Flushing DyIO");
		}
		if(hasPid){
			pid.flushPIDChannels(seconds);
			Log.info("Flushing PID");
		}

		//System.out.println("Flush Took "+(System.currentTimeMillis()-time)+"ms");
	}
	public IPidControlNamespace getPid() {
		return pid;
	}
	public DyIO getDyio(){
		return dyio;
	}
	public void setCachedTargets(double[] jointSpaceVect) {
		if(jointSpaceVect.length!=links.size())
			throw new IndexOutOfBoundsException("Expected "+links.size()+" links, got "+jointSpaceVect.length);
		int i=0;
		for(AbstractLink lin:links){
			try{
				lin.setTargetEngineeringUnits(jointSpaceVect[i]);
			}catch (Exception ee){
				throw new RuntimeException("Joint "+i+" failed, "+ee.getMessage());
			}
			i++;
		}
	}

	public boolean isConnected() {
		if(hasPid){
			return pid.isAvailable();
		}
		if(hasServo){
			return dyio.isAvailable();
		}
		return true;
	}

	public ArrayList<LinkConfiguration> getLinkConfigurations() {
		if(linkConfigurations== null){
			linkConfigurations=new ArrayList<LinkConfiguration>();
			for(AbstractLink l:links){
				linkConfigurations.add(l.getLinkConfiguration());
			}
		}
		return linkConfigurations;
	}
}

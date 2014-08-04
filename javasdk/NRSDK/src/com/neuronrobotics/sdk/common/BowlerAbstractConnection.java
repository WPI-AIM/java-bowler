/*******************************************************************************
 * Copyright 2010 Neuron Robotics, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
/**
 *
 * Copyright 2009 Neuron Robotics, LLC
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.neuronrobotics.sdk.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import com.neuronrobotics.sdk.commands.bcs.core.NamespaceCommand;
import com.neuronrobotics.sdk.commands.bcs.core.PingCommand;
import com.neuronrobotics.sdk.commands.bcs.core.RpcArgumentsCommand;
import com.neuronrobotics.sdk.commands.bcs.core.RpcCommand;
import com.neuronrobotics.sdk.config.SDKBuildInfo;
import com.neuronrobotics.sdk.util.ThreadUtil;




/**
 * Connections create a bridge between a device and the SDK. Each connection is encapsulated to allow maximum
 * reuse and system changes without the need to restart / reconfigure.
 *
 */
public abstract class BowlerAbstractConnection {
	
	//private boolean threadedUpstreamPackets=false;
	private static boolean useThreadedStack=true;
	
	/** The sleep time. */
	private int sleepTime = 1000;
	
	private long lastWrite = -1;
	
	private long heartBeatTime=1000;
	
	private int chunkSize = 64;
	
	
	/** The response. */
	private BowlerDatagram response = null;
	
	/** The listeners. */
	private ArrayList<IBowlerDatagramListener> listeners = new ArrayList<IBowlerDatagramListener>();
	ArrayList<IConnectionEventListener> disconnectListeners = new ArrayList<IConnectionEventListener> ();
	private ISynchronousDatagramListener syncListen = null;
	
	/** The queue. */
	private QueueManager syncQueue = null;
	private QueueManager asyncQueue=null;
	
	/** The connected. */
	private boolean connected = false;
	
	/** The data ins. */
	private DataInputStream dataIns;
	
	/** The data outs. */
	private DataOutputStream dataOuts;
	
	//private Updater updater = null;

	
	private ArrayList<NamespaceEncapsulation> namespaceList=null;
	private ArrayList<String> nameSpaceStrings = null;
	private boolean beater = false;
	private ReentrantLock executingLock = new ReentrantLock();
	
	
	/**
	 * Attempt to establish a connection. Return if the attempt was successful.
	 *
	 * @return true, if successful
	 */
	abstract public boolean connect();
	
	/**
	 * Attempt to re-establish a connection. Return if the attempt was successful.
	 *
	 * @return true, if successful
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	abstract public boolean reconnect() throws IOException;
	
	/**
	 * Attempt to re-establish a connection. Return if the attempt was successful.
	 *
	 * @return true, if successful
	 */
	abstract public boolean waitingForConnection();
	
	/**
	 * Tells the connection to use asynchronous packets as threads or not. 
	 * @param up
	 */
	public void setThreadedUpstreamPackets(boolean up){
		//threadedUpstreamPackets=up;
	}
	
	

	
	/**
	 * Sends any "universal" data to the connection and returns either the syncronous response or null in the
	 * event that the connection has determined a timeout. Before sending, use clearLastSyncronousResponse()
	 * and use getLastSyncronousResponse() to get the last response since clearing.
	 *
	 * @param sendable the sendable
	 * @return the bowler datagram
	 */
	public synchronized BowlerDatagram sendSynchronusly(BowlerDatagram sendable){
		
		if(!isConnected()) {
			Log.error("Can not send message because the engine is not connected.");
			return null;
		}
		executingLock.lock();
		clearLastSyncronousResponse();
		try {
			long send = System.currentTimeMillis();
			sendable.setUpstream(false);
			Log.info("\nT>>"+sendable);
			write(sendable.getBytes());
			Log.info("Transmit took: "+(System.currentTimeMillis()-send)+" ms");
		} catch (IOException e1) {
			executingLock.unlock();
			throw new RuntimeException(e1);
		}
		long startOfReciveTime = System.currentTimeMillis();
		

		do{
			if(isUseThreadedStack())
				ThreadUtil.wait(0,10);
			else{
				syncQueue.runPacketUpdate();
			}
		}while (((System.currentTimeMillis()-startOfReciveTime)<getSleepTime())  && (getLastSyncronousResponse() == null));
		long rcvTime = (System.currentTimeMillis()-startOfReciveTime);

		if(rcvTime>(getSleepTime()*getPercentagePrint() /100) ){
			Log.warning("Receive took: "+rcvTime +" ms. This is greater then "+getPercentagePrint() +"% of the sleep timeout");
		}else{
			Log.info("Receive took: "+rcvTime +" ms");
		}
		
		if (getLastSyncronousResponse() == null){
			try {
				//new RuntimeException().printStackTrace();
				Log.error("No response from device, no response in "+(System.currentTimeMillis()-startOfReciveTime)+" ms");
				reconnect();
			} catch (IOException e) {
				clearLastSyncronousResponse();
				executingLock.unlock();
				throw new RuntimeException(e);
			}
		}
		BowlerDatagram b = getLastSyncronousResponse();
		clearLastSyncronousResponse();
		executingLock.unlock();
		return b;
	}
	
	/**
	 * Sends any "universal" data to the connection and returns either the syncronous response or null in the
	 * event that the connection has determined a timeout. Before sending, use clearLastSyncronousResponse()
	 * and use getLastSyncronousResponse() to get the last response since clearing.
	 *
	 * @param sendable the sendable
	 * @throws IOException 
	 */
	public void sendAsync(BowlerDatagram sendable) throws IOException{
		if(!isConnected()) {
			//Log.error("Can not send message because the engine is not connected.");
			return;
		}
		sendable.setUpstream(true);
		try {
			write(sendable.getBytes());
		} catch (IOException e1) {
			Log.error("No response from device...");
			reconnect();
			throw  e1;		
		}
	}
	
	/**
	 * Disconnect and deactive the current connection.
	 */
	public void disconnect(){
		if(!isConnected()) {
			return;
		}
		Log.info("Disconnecting Bowler Connection");
		ThreadedTimeout t = new ThreadedTimeout();
		t.setStartTime(100);
		while(!t.isTimedOut()){
			 try {
				 if(dataIns!=null)
					 getDataIns().read();
			} catch (NullPointerException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
		}
		Log.info("Shutting down streams");
		setConnected(false);
		
	}

	/**
	 * Sets the sleep time.
	 *
	 * @param sleepTime the new sleep time
	 */
	public void setSynchronusPacketTimeoutTime(int sleepTime) {
		this.sleepTime = sleepTime;
		if(sleepTime*2>BowlerDatagramFactory.getPacketTimeout())
			BowlerDatagramFactory.setPacketTimeout(sleepTime*2);
		Log.warning("Setting the synchronus packet timeout to "+sleepTime);
	}
	
	
	/**
	 * Gets the sleep time.
	 *
	 * @return the sleep time
	 */
	public int getSleepTime() {
		return sleepTime;
	}
	

	public long msSinceLastSend() {
		if(getLastWrite()<0){
			return 0;
		}
		return System.currentTimeMillis() - getLastWrite() ;
	}
	/**
	 * Write.
	 *
	 * @param data the data
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	//private ByteList outgoing = new ByteList();
	public void write(byte[] data) throws IOException {
		waitForConnectioToBeReady();
		setLastWrite(System.currentTimeMillis());
		if(dataOuts != null){
			try{
				//Log.info("Writing: "+data.length+" bytes");
				
				//while(outgoing.size()>0){
					//byte[] b =outgoing.popList(getChunkSize());
				//System.out.println("Writing "+new ByteList(data));
				getDataOuts().write(data);
				getDataOuts().flush();
				//}
			}catch (Exception e){
				//e.printStackTrace();
				Log.error("Write failed. "+e.getMessage());
				reconnect();
			}
		}else{
			Log.error("No data sent, stream closed");
		}
		
	}
	
	/**
	 * Sets the connected.
	 *
	 * @param connected the new connected
	 */
	public synchronized void  setConnected(boolean c) {
		if(connected == c)
			return;
		connected = c;
		Log.info("Setting connection to "+c);
		if(connected){
			setSyncQueue(new QueueManager(true));
			setAsyncQueue(new QueueManager(false));
			
			fireConnectEvent();
		}else{
			try {
				if(dataIns !=null)
					getDataIns().close();
			} catch (Exception e) {
				//return;
			}
			try {
				if(dataOuts !=null)
					getDataOuts().close();
			} catch (Exception e) {
				//return;
			}
			setDataIns(null);
			setDataOuts(null);
			if(getSyncQueue() != null) {
				getSyncQueue().kill();
				setSyncQueue(null);
			}
			if(getAsyncQueue() != null) {
				getAsyncQueue().kill();
				
				setAsyncQueue(null);
			}
			fireDisconnectEvent();
		}
	}
	
	/**
	 * Checks if is connected.
	 *
	 * @return true, if is connected
	 */
	public boolean isConnected() {
		return connected;
	}
	
	/**
	 * This should be done before sending. 
	 */
	public void clearLastSyncronousResponse() {
		response = null;
	}
	
	/**
	 * Return the synchronous response buffer.
	 *
	 * @return the last synchronous response
	 */
	public BowlerDatagram getLastSyncronousResponse() {
		return response;
	}
	
	/**
	 * Whenever a connection has received a full datagram from its "physical" connection, it should then call
	 * <code>onDataReceived</code> with the datagram. This will set it to the last received data if it is
	 * synchronous and will add it to the appropriate queues to be picked up and send to the listeners.
	 *
	 * @param data the data
	 */
	protected void onDataReceived(BowlerDatagram data) {
		if(data.isSyncronous()) {
			
			if(syncListen!=null){
				// this is a server and the packet needs to processed
				getSyncQueue().addDatagram(data);
				Log.info("Added packet to the response queue");
			}else{
				response = data;
			}
		}else {
			getAsyncQueue().addDatagram(data);
		}
		
	}
	
	/**
	 * Fire On Response.
	 *
	 * @param datagram the datagram
	 */

	protected BowlerDatagram  fireSyncOnReceive(BowlerDatagram datagram) {
		if(datagram.isSyncronous()){
			if (syncListen!=null){
				return syncListen.onSyncReceive(datagram);
			}
		}
		return null;
	}

	
	protected void fireAsyncOnResponse(BowlerDatagram datagram) {
		if(!datagram.isSyncronous()){
			if(isInitializedNamespaces()){
				Log.info("\nASYNC to "+listeners.size()+" listeners<<\n"+datagram);
				for(IBowlerDatagramListener l : listeners) {

					Log.info("\nASYNC listener: "+l.getClass());

					try{
						l.onAsyncResponse(datagram);
					}catch (Exception ex){
						ex.printStackTrace();
					}
				}
			}else{
				Log.warning("\nASYNC Not ready<<");
			}
			
		}
		
	}


	/**
	 * Add a listener that will be notified whenever an asyncronous packet arrives.
	 *
	 * @param listener the listener
	 */
	public void addDatagramListener(IBowlerDatagramListener listener) {
		if(listeners.contains(listener)) {
			return;
		}
		//synchronized(listeners){
			listeners.add(listener);
		//}
	}
	
	/**
	 * Remove a listener from new packet notifications.
	 *
	 * @param listener the listener
	 */
	public void removeDatagramListener(IBowlerDatagramListener listener) {
		if(!listeners.contains(listener)) {
			return;
		}
		
		listeners.remove(listener);
	}

	
	/**
	 * Start builder.
	 */

	
	/**
	 * Sets the data ins.
	 *
	 * @param dataIns the new data ins
	 */
	public void setDataIns(DataInputStream dataIns) {
		this.dataIns = dataIns;
	}

	/**
	 * Gets the data ins.
	 *
	 * @return the data ins
	 */
	public DataInputStream getDataIns() throws NullPointerException{
		if(dataIns==null)
			throw new NullPointerException();
		return dataIns;
	}

	/**
	 * Sets the data outs.
	 *
	 * @param dataOuts the new data outs
	 */
	public void setDataOuts(DataOutputStream dataOuts) {
		
		this.dataOuts = dataOuts;
	}

	/**
	 * Gets the data outs.
	 *
	 * @return the data outs
	 */
	public DataOutputStream getDataOuts() throws NullPointerException{
		if(dataOuts==null)
			throw new NullPointerException();
		return dataOuts;
	}
	
	/**
	 * Wait for connectio to be ready.
	 */
	protected void waitForConnectioToBeReady(){
		if(!waitingForConnection()) {
			return;
		}
		
		Log.info("Waiting for connection...");
		long start = System.currentTimeMillis() ;
		while(true){
			if(System.currentTimeMillis()> (start + 20000)){
				break;
			}
			
			if(!waitingForConnection()) {
				break;
			}
			
			ThreadUtil.wait(10);
		}
		Log.info("Connection ready");
	}
	
	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public int getChunkSize() {
		return chunkSize;
	}
	public void setAsyncQueue(QueueManager asyncQueue) {
		this.asyncQueue = asyncQueue;
		if(this.asyncQueue != null && isUseThreadedStack())
			this.asyncQueue.start();
	}
	public void setSyncQueue(QueueManager syncQueue) {
		this.syncQueue = syncQueue;
		if(this.syncQueue != null && isUseThreadedStack())
			this.syncQueue.start();
	}
	public  QueueManager getAsyncQueue() {
		return asyncQueue;
	}
	public QueueManager getSyncQueue() {
		return syncQueue;
	}
	
	

	
	private void pushUp(BowlerDatagram b) throws IOException{
		b.setFree(false);
		if(b.isSyncronous()){
			BowlerDatagram ret = fireSyncOnReceive(b);
			if(ret !=null){
				// Sending response to server
				sendAsync(ret);
			}
		}else
			fireAsyncOnResponse(b);

	}
	
	
	
	public void addConnectionEventListener(IConnectionEventListener l ) {
		if(!disconnectListeners.contains(l)) {
			disconnectListeners.add(l);
		}
	}
	public void removeConnectionEventListener(IConnectionEventListener l ) {
		if(disconnectListeners.contains(l)) {
			disconnectListeners.remove(l);
		}
	} 
	private void fireDisconnectEvent() {
		for(IConnectionEventListener l:disconnectListeners) {
			l.onDisconnect(this);
		}
	}
	private void fireConnectEvent() {
		for(IConnectionEventListener l:disconnectListeners) {
			l.onConnect(this);
		}
	}
	
	public void setSynchronousDatagramListener(ISynchronousDatagramListener l ) {
		if (syncListen == null){
			syncListen = l;
		}else{
			if(syncListen == l)
				return;
			throw new RuntimeException("There is already a listener "+syncListen);
		}
	}
	public void removeSynchronousDatagramListener(ISynchronousDatagramListener l ) {
		if(syncListen!= null){
			if(syncListen!= l){
				throw new RuntimeException("There is a different listener "+syncListen);
			}
		}
		syncListen=null;
	} 

	/**
	 * This is the scripting interface to Bowler devices. THis allows a user to describe a namespace, rpc, and array or 
	 * arguments to be paced into the packet based on the data types of the argument. The response in likewise unpacked 
	 * into an array of objects.
	 * @param namespace The string of the desired namespace
	 * @param rpcString The string of the desired RPC
	 * @param arguments An array of objects corresponding to the data to be stuffed into the packet.
	 * @return The return arguments parsed and packet into an array of arguments
	 * @throws DeviceConnectionException If the desired RPC's are not available then this will be thrown
	 */
	public Object [] send(MACAddress addr,String namespace,BowlerMethod method, String rpcString, Object[] arguments, int retry) throws DeviceConnectionException{
		if(namespaceList == null){
			getNamespaces(addr);
		}
		for (NamespaceEncapsulation ns:namespaceList){
			if(ns.getNamespace().toLowerCase().contains(namespace.toLowerCase())){
				//found the namespace
				for(RpcEncapsulation rpc:ns.getRpcList()){
					if(		rpc.getRpc().toLowerCase().contains(rpcString.toLowerCase()) &&
							rpc.getDownstreamMethod() == method){
						//Found the command in the namespace

							BowlerDatagram dg =  send(rpc.getCommand(arguments),addr,retry);
							Object [] en =rpc.parseResponse(dg);//parse and return
							BowlerDatagramFactory.freePacket(dg);
							return en;
					}
				}
			}
		}
		Log.error("No method found, attempted "+namespace+" RPC: "+rpcString);
		for (NamespaceEncapsulation ns:namespaceList){
			Log.error("Namespace \n"+ns);
		}
		throw new DeviceConnectionException("Device does not contain command NS="+namespace+" Method="+method+" RPC="+rpcString+"'");
	}
	
	private boolean namespacesFinishedInitializing = false;

	private double percentagePrint =10.0;
	
	public boolean isInitializedNamespaces(){
		return namespaceList!=null && namespacesFinishedInitializing ;
	}

	/**
	 * Get all the namespaces.
	 *
	 * @return the namespaces
	 */
	public ArrayList<String>  getNamespaces(MACAddress addr){	
		if(namespaceList == null){
			namespaceList = new ArrayList<NamespaceEncapsulation>();
			nameSpaceStrings = new ArrayList<String>();
			int numTry=0;
			boolean done=false;
			while(!done){
				numTry++;
				try {
					BowlerDatagram namespacePacket = send(new NamespaceCommand(0),addr,5);
					int num;
					String tmpNs =namespacePacket.getData().asString();
					if(tmpNs.length() ==  namespacePacket.getData().size()){
						//Done with the packet
						BowlerDatagramFactory.freePacket(namespacePacket);
						//System.out.println("Ns = "+tmpNs+" len = "+tmpNs.length()+" data = "+b.getData().size());
						namespacePacket = send(new NamespaceCommand(),addr,5);
						
						num= namespacePacket.getData().getByte(0);
						//Done with the packet
						BowlerDatagramFactory.freePacket(namespacePacket);
						Log.warning("This is an older implementation of core, depricated");
					}else{
						num= namespacePacket.getData().getByte(namespacePacket.getData().size()-1);
						//Done with the packet
						BowlerDatagramFactory.freePacket(namespacePacket);
						Log.info("This is the new core");
					}
					
	//					if(num<1){
	//						Log.error("Namespace request failed:\n"+namespacePacket);
	//					}else{
	//						Log.info("Number of Namespaces="+num);
	//					}
					
					
					for (int i=0;i<num;i++){
	
						BowlerDatagram nsStringPacket= send(new NamespaceCommand(i),addr,5);
						String space = nsStringPacket.getData().asString();
						//Done with the packet
						BowlerDatagramFactory.freePacket(nsStringPacket);
						Log.debug("Adding Namespace: "+space);
						
						namespaceList.add(new NamespaceEncapsulation(space));
					}
					Log.debug("There are "+num+" namespaces on this device");
					Log.debug("Attempting to populate RPC lists for all "+namespaceList.size());
					for(NamespaceEncapsulation ns:namespaceList){
						getRpcList(ns.getNamespace(),addr);
					}
					done = true;
				} catch (InvalidResponseException e) {
					Log.error("Invalid response from Namespace");
					if(numTry>3)
						throw e;
					
				} catch (NoConnectionAvailableException e) {
					Log.error("No connection is available.");
					if(numTry>3)
						throw e;
				}catch (Exception e) {
					Log.error("Other exception");
					e.printStackTrace();
					if(numTry>3)
						throw new RuntimeException(e);
				}
				if(!done){
					//failed coms, reset list
					namespaceList = new ArrayList<NamespaceEncapsulation>();
				}
			}
		
		}
		
		if(nameSpaceStrings.size() != namespaceList.size()){
			for(NamespaceEncapsulation ns:namespaceList){
				nameSpaceStrings.add(ns.getNamespace());
				getRpcList(ns.getNamespace(), addr);
			}
		}
		namespacesFinishedInitializing = true;
		return nameSpaceStrings;
		
	}
	
	/**
	 * Check the device to see if it has the requested namespace
	 * @param string
	 * @return
	 */
	public boolean hasNamespace(String string,MACAddress addr) {
		if(namespaceList == null)
			getNamespaces(addr);
		for(NamespaceEncapsulation ns:namespaceList){
			if(ns.getNamespace().contains(string))
				return true;
		}
		return false;
	}
	
	/**
	 * Requests all of the RPC's from a namespace
	 * @param s
	 * @return
	 */
	public ArrayList<RpcEncapsulation> getRpcList(String namespace,MACAddress addr) {
		int namespaceIndex = 0;
		boolean hasCoreRpcNS = false;
		
		for (int i=0;i<namespaceList.size();i++){
			if(namespaceList.get(i).getNamespace().contains(namespace)){
				namespaceIndex=i;
			}
			if(namespaceList.get(i).getNamespace().contains("bcs.rpc.*")){
				hasCoreRpcNS=true;
			}
		}
		if(!hasCoreRpcNS){
			//this device has no RPC namespace, failing out
			Log.info("Device has no RPC identification namespace");
			return new ArrayList<RpcEncapsulation>();
		}
		if(namespaceList.get(namespaceIndex).getRpcList()!=null){
			//fast return if list is already populated
			return namespaceList.get(namespaceIndex).getRpcList();
		}
		
		try{
			//populate RPC set
			BowlerDatagram b = send(new  RpcCommand(namespaceIndex),addr,5);
			if(!b.getRPC().contains("_rpc")){
				System.err.println(b);
				throw new RuntimeException("This RPC index request has failed");
			}
			//int ns = b.getData().getByte(0);// gets the index of the namespace
			//int rpcIndex = b.getData().getByte(1);// gets the index of the selected RPC
			int numRpcs = b.getData().getByte(2);// gets the number of RPC's
			if(numRpcs<1){
				Log.error("RPC request failed:\n"+b);
			}else{
				Log.info("Number of RPC's = "+numRpcs);
			}
			Log.debug("There are "+numRpcs+" RPC's in "+namespace);
			namespaceList.get(namespaceIndex).setRpcList(new ArrayList<RpcEncapsulation>());
			for (int i=0;i<numRpcs;i++){
				b = send(new RpcCommand(namespaceIndex,i),addr,5);
				if(!b.getRPC().contains("_rpc")){
					System.err.println(b);
					throw new RuntimeException("This RPC section failed");
				}
				String rpcStr = new String(b.getData().getBytes(3, 4));
				//Done with the packet
				BowlerDatagramFactory.freePacket(b);
				b = send(new RpcArgumentsCommand(namespaceIndex,i),addr,5);
				if(!b.getRPC().contains("args")){
					System.err.println(b);
					throw new RuntimeException("This RPC section failed");
				}
				byte []data = b.getData().getBytes(2);
				//Done with the packet
				BowlerDatagramFactory.freePacket(b);
				BowlerMethod downstreamMethod = BowlerMethod.get(data[0]);
				int numDownArgs = data[1];
				BowlerMethod upstreamMethod   = BowlerMethod.get(data[numDownArgs+2]);
				int numUpArgs = data[numDownArgs+3];
				
				BowlerDataType [] downArgs = new BowlerDataType[numDownArgs];
				BowlerDataType [] upArgs = new BowlerDataType[numUpArgs];
				
				for(int k=0;k<numDownArgs;k++){
					downArgs[k] = BowlerDataType.get(data[k+2]);
				}
				for(int k=0;k<numUpArgs;k++){
					upArgs[k] = BowlerDataType.get(data[k+numDownArgs+4]);
				}
				RpcEncapsulation tmpRpc = new RpcEncapsulation(namespaceIndex,namespace, rpcStr, downstreamMethod,downArgs,upstreamMethod,upArgs);
				//System.out.println(tmpRpc);
				namespaceList.get(namespaceIndex).getRpcList().add(tmpRpc);
			}
			
		}catch(InvalidResponseException ex){
			Log.debug("Older version of core, discovery disabled");
		}
		return namespaceList.get(namespaceIndex).getRpcList();
	}
	
	/**
	 * Send a command to the connection.
	 *
	 * @param command the command
	 * @return the syncronous response
	 * @throws NoConnectionAvailableException the no connection available exception
	 * @throws InvalidResponseException the invalid response exception
	 */
	public BowlerDatagram send(BowlerAbstractCommand command,MACAddress addr, int retry) throws NoConnectionAvailableException, InvalidResponseException {	
		for(int i=0;i<retry;i++){
			BowlerDatagram ret;
			try{
				ret = send( command,addr);
				if(ret != null)
					//if(!ret.getRPC().contains("_err"))
						return ret;
			}catch(Exception ex){
				//ex.printStackTrace();
				Log.error(ex.getMessage());
			}
			Log.error("Sending Synchronus packet and there was a failure, will retry "+(retry-i-1)+" more times");
			ThreadUtil.wait(150*i);
		}
		return null;
	}
	

	

	/**
	 * Send a command to the connection.
	 *
	 * @param command the command
	 * @return the syncronous response
	 * @throws NoConnectionAvailableException the no connection available exception
	 * @throws InvalidResponseException the invalid response exception
	 */
	public BowlerDatagram send(BowlerAbstractCommand command,MACAddress addr) throws NoConnectionAvailableException, InvalidResponseException {	
		if(!isConnected()) {
			if(!connect())
				throw new NoConnectionAvailableException();
		}
		BowlerDatagram cmd= BowlerDatagramFactory.build(addr, command);
		BowlerDatagram back = sendSynchronusly(cmd);
		//BowlerDatagramFactory.freePacket(cmd);
		return command.validate(back);
	}
	
	/**
	 * Implementation of the Bowler ping ("_png") command
	 * Sends a ping to the device returns the device's MAC address.
	 *
	 * @return the device's address
	 */
	private boolean ping() {
		try {
			BowlerDatagram bd = send(new PingCommand(),new MACAddress(), 5);
			if(bd !=null){
				BowlerDatagramFactory.freePacket(bd);
				return true;
			}
		} catch (InvalidResponseException e) {
			Log.error("Invalid response from Ping ");
			e.printStackTrace();
		} catch (Exception e) {
			Log.error("No connection is available.");
			e.printStackTrace();
		}
		return false;
	}
	
	public void startHeartBeat(){
		beater=true;
	}
	
	public void startHeartBeat(long msHeartBeatTime){
		if (msHeartBeatTime<10)
			msHeartBeatTime = 10;
		heartBeatTime= msHeartBeatTime;
		startHeartBeat();
	}
	public void stopHeartBeat(){
		beater=false;
	}
	
	private void runHeartBeat(){
		if((msSinceLastSend())>heartBeatTime){
			try{
				if(!ping()){
					Log.debug("Ping failed, disconnecting");
					disconnect();
				}
			}catch(Exception e){
				Log.debug("Ping failed, disconnecting");

			}
		}
	}
	
	public double getPercentagePrint() {
		return percentagePrint;
	}

	public void setPercentagePrint(double percentagePrint) {
		this.percentagePrint = percentagePrint;
	}

	public long getLastWrite() {
		return lastWrite;
	}

	public void setLastWrite(long lastWrite) {
		this.lastWrite = lastWrite;
	}

	/**
	 * Thread safe queue manager.
	 * @author rbreznak
	 *
	 */
	private class QueueManager extends Thread {
		// stack extends vector and gives thread safety
		/** The queue buffer. */
		private ArrayList<BowlerDatagram> queueBuffer = new ArrayList<BowlerDatagram>();
		private ByteList bytesToPacketBuffer = new ByteList();
		private boolean isSystemQueue=false;
		private boolean killSwitch=false;
		
		public QueueManager(boolean b) {
			isSystemQueue = b;
		}


		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			Log.info("Starting the Queue Manager as "+isSystemQueue);
			ThreadUtil.wait(100);
			while(isConnected() && !killSwitch) {

				long start = System.currentTimeMillis();
				if(isSystemQueue)
					runPacketUpdate();
				else{ 
					if(beater)
						runHeartBeat();
					
				}
				long packetUpdate = System.currentTimeMillis();
				if(queueBuffer.isEmpty()){
					// prevents thread lock
					ThreadUtil.wait(1);
				}else{
					try{
						//send(queueBuffer.remove(queueBuffer.size()-1)	);
						
						BowlerDatagram b = queueBuffer.remove(0);
						long pulledPacket = System.currentTimeMillis();
						pushUp(b);
						long pushedPacket = System.currentTimeMillis();
						
						if((System.currentTimeMillis()-getLastWrite())>(getSleepTime()*(getPercentagePrint() /100.0))&& b.isSyncronous()){
							Log.error("Packet recive took more then "+getPercentagePrint()+"%. " +
											"\nPacket Update\t"+(packetUpdate- start)+"" +
											"\nPulled Packet\t"+(pulledPacket-packetUpdate)+"" +
											"\nPushed Packet\t"+(pushedPacket-pulledPacket));
						}
					}catch(Exception e){
						e.printStackTrace();
					}
				}
				
				int index = 0;
				int max = 500;
				while(queueBuffer.size()>max){
					if(queueBuffer.get(index).isFree()){
						Log.error("Removing packet because freed "+queueBuffer.remove(index));
					}else{
						if(!queueBuffer.get(index).isSyncronous() && queueBuffer.get(index).getMethod() != BowlerMethod.CRITICAL){
							int state = Log.getMinimumPrintLevel();
							Log.enableErrorPrint();
							Log.error("Removing packet from overflow: "+queueBuffer.remove(index));
							Log.setMinimumPrintLevel(state);
						}else{
							index++;
						}
					}
					if(index >= max){
						break;
					}
				}
				
				
			}
			
			Log.error("Queue Manager thread exited! Connected="+isConnected()+" kill switch="+killSwitch);
			//throw new RuntimeException();
		}
		

		private boolean runPacketUpdate() {
			try {
				if(loadPacketFromPhy(bytesToPacketBuffer))
					bytesToPacketBuffer= new ByteList();
			} catch (Exception e) {
				if(isConnected()){
					Log.error("Data read failed "+e.getMessage());
					try {
						reconnect();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
			return false;
		}
		
		/**
		 * Adds the datagram.
		 *
		 * @param dg the dg
		 */
		private void addDatagram(BowlerDatagram dg) {
			queueBuffer.add(dg);
		}
		
		/**
		 * Kill.
		 */
		public void kill() {
			killSwitch=true;
			//new RuntimeException("Killing the Queue").printStackTrace();
		}
	}
	
	
	public boolean loadPacketFromPhy(ByteList bytesToPacketBuffer) throws NullPointerException, IOException{
		if(dataIns!=null){	
			while(getDataIns().available()>0){
				long dataRead=System.currentTimeMillis();
				//we want to run this until the buffer is clear or a packet is found
				int b = getDataIns().read();
				long dataReadEnd=System.currentTimeMillis();
				if(b<0){
					Log.error("Stream is broken - unexpected: claimed to have "+getDataIns().available()+" bytes, read in "+b);
					reconnect();
					//something went wrong
					new RuntimeException().printStackTrace();
					return false;
				}else{
					
					bytesToPacketBuffer.add(b);
					long dataAdd=System.currentTimeMillis();
					BowlerDatagram bd = BowlerDatagramFactory.build(bytesToPacketBuffer);
					long dataBuild=System.currentTimeMillis();
					if (bd!=null) {
						Log.info("\nR<<"+bd);
						onDataReceived(bd);

						long dataSet=System.currentTimeMillis();
						//bytesToPacketBuffer.clear();
						
						long bufferClear=System.currentTimeMillis();
						
						if((System.currentTimeMillis()-getLastWrite())>(getSleepTime()*(getPercentagePrint() /100.0))&& bd.isSyncronous() && syncListen==null){
							Log.error("Packet recive took more then "+getPercentagePrint()+"%. " +
									//"\nRaw receive\t"+(start-getLastWrite() )+"" +
									//"\nStart Section\t"+(dataRead- start)+"" +
									"\nData Read\t"+(dataReadEnd-dataRead)+"" +
									"\nAdd data\t"+(dataAdd-dataReadEnd)+
									"\nBuild Packet\t"+(dataBuild-dataAdd)+
									"\nSet Packet\t"+(dataSet-dataBuild)+
									"\nClear Packet buffer\t"+(bufferClear-dataSet)											
									);
						}

						//Packet found, break the loop and deal with it
						return true;
					}
				}
				//Log.info("buffer: "+buffer);
			}
		}else{
			Log.error("Data In is null");
		}
		return false;
	}

	public static boolean isUseThreadedStack() {
		return useThreadedStack;
	}

	public static void setUseThreadedStack(boolean useThreadedStack) {
		BowlerAbstractConnection.useThreadedStack = useThreadedStack;
	}
	

}

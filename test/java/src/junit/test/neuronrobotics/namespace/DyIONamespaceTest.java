package junit.test.neuronrobotics.namespace;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.MACAddress;
import com.neuronrobotics.sdk.dyio.DyIO;
import com.neuronrobotics.sdk.dyio.DyIOChannelMode;
import com.neuronrobotics.sdk.serial.SerialConnection;

public class DyIONamespaceTest {
	
	private static DyIO harness=null;
	private static DyIO testDevice=null;
	
	@Before
	public void setUp() throws Exception {
		DyIO.disableFWCheck();
		Log.enableDebugPrint();
		
		SerialConnection testerConection = SerialConnection.getConnectionByMacAddress(new MACAddress("74:F7:26:80:00:7C"));
		assertTrue(testerConection!=null);
		harness = new DyIO(testerConection);
		harness.connect();
		
		SerialConnection targetConection = SerialConnection.getConnectionByMacAddress(new MACAddress("74:F7:26:00:00:00"));
		assertTrue(targetConection!=null);
		targetConection.setSynchronusPacketTimeoutTime(10000);
		testDevice = new DyIO(targetConection);
		testDevice.connect();
		
		
	}
	
	@After
	public void shutdownDevices(){
		
		testDevice.disconnect();
		if(harness!=null)
			harness.disconnect();
	}
	
	@Test public void DyIONameTest(){

		if(!testDevice.isAvailable())
			fail();
		
		String name = testDevice.getInfo();
		
		String setName;
		if(name.contains("My DyIO"))
			setName="My DyIO2";
		else
			setName="My DyIO";
		
		testDevice.setInfo(setName);
		
		String newName = testDevice.getInfo();
		

		testDevice.setInfo(name);
	
		assertTrue(setName.contains(newName));
		assertTrue(name.contains(testDevice.getInfo()));
		assertTrue(testDevice.ping() );

	}
	
	@Test public void DyIOPowerTest(){
		if(!testDevice.isAvailable())
			fail();
		double volts = testDevice.getBatteryVoltage(true);
		
		testDevice.setServoPowerSafeMode(true);


	}
	
	@Test public void DyIOModesTest(){
		if(!testDevice.isAvailable())
			fail();

		ArrayList<DyIOChannelMode> modes = testDevice.getAllChannelModes();
		for(int i=0;i<modes.size();i++){
			
			if(modes.get(i)==DyIOChannelMode.DIGITAL_IN){
				modes.set(i, DyIOChannelMode.DIGITAL_OUT);
			}else{
				modes.set(i, DyIOChannelMode.DIGITAL_IN);
			}
			testDevice.setMode(i, modes.get(i));
		}
		
		ArrayList<DyIOChannelMode> modesAfter = testDevice.getAllChannelModes();
		for(int i=0;i<modes.size();i++){
			assertTrue(modes.get(i)==modesAfter.get(i));
			assertTrue(modes.get(i)==testDevice.getMode(i));
		}
	}
	@Test public void DyIOInputTest(){
		if(!testDevice.isAvailable() || harness == null)
			fail();
		int numPins = testDevice.getDyIOChannelCount();
		
		//Test device as input
		for(int i=0;i<numPins;i++){
			if(!(i==16 || i==17)){
				int testerIndex = numPins-1-i;
				harness.setMode(testerIndex, DyIOChannelMode.DIGITAL_OUT);
				testDevice.setMode(i, DyIOChannelMode.DIGITAL_IN);
				
				boolean state=true;
				for(int j=0;j<5;j++){
					int pinState = state?1:0;
					harness.setValue(testerIndex, pinState);
					int gotValue = testDevice.getValue(i);
					System.out.println(" Pin:"+i+" Tester:"+testerIndex+" setting to: "+pinState+" got:"+gotValue);
					assertTrue(gotValue==pinState);
					state = !state;
				}
			}
		}
		
	}
	
	@Test public void DyIOOutputTest(){
		if(!testDevice.isAvailable() || harness == null)
			fail();
		int numPins = testDevice.getDyIOChannelCount();

		//test device as output
		for(int i=0;i<numPins;i++){
			if(!(i==16 || i==17)){
				int testerIndex = numPins-1-i;
				harness.setMode(testerIndex, DyIOChannelMode.DIGITAL_IN);
				testDevice.setMode(i, DyIOChannelMode.DIGITAL_OUT);
				
				boolean state=true;
				for(int j=0;j<5;j++){
					int pinState = state?1:0;
					testDevice.setValue(i, pinState);
					int gotValue = harness.getValue(testerIndex);
					System.out.println(" Pin:"+i+" Tester:"+testerIndex+" setting to: "+pinState+" got:"+gotValue);
					assertTrue(gotValue==pinState);
					state = !state;
				}
			}
		}
		
	}

	@Test
	public void dyioNamespaceTest() {
		if(!testDevice.isAvailable())
			fail();
		assertTrue(testDevice.hasNamespace("neuronrobotics.dyio.*"));
		testDevice.getRevisions();
	
	}

}

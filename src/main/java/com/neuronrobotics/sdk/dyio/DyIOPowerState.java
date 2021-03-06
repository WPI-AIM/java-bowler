package com.neuronrobotics.sdk.dyio;

// TODO: Auto-generated Javadoc
/**
 * The Enum DyIOPowerState.
 */
public enum DyIOPowerState {
	
	/** The regulated. */
	REGULATED,
	
	/** The battery unpowered. */
	BATTERY_UNPOWERED,
	
	/** The battery powered. */
	BATTERY_POWERED;
	
	/**
	 * Value of.
	 *
	 * @param code the code
	 * @param batteryVoltage the battery voltage
	 * @return the dy io power state
	 */
	public static DyIOPowerState valueOf(int code, double batteryVoltage) {
		switch(code) {
		case 1:
			return DyIOPowerState.REGULATED;
		case 0:
			if(batteryVoltage<5.0)
				return DyIOPowerState.BATTERY_UNPOWERED;
		default:
			return DyIOPowerState.BATTERY_POWERED;
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Enum#toString()
	 */
	@Override
	public String toString(){
		String s="";
		switch(this){
		case BATTERY_POWERED:
			s="BATTERY POWERED";
			break;
		case BATTERY_UNPOWERED:
			s="BATTERY UN-POWERED";
			break;
		case REGULATED:
			s="REGULATED";
			break;
		}
		return s;
	}
}

package com.algomeet.multitenancycore.context;

/**
 * Used to switch off the schema
 */
public class TenantAwareSwitchOffContext {

    private static final ThreadLocal<Boolean> SWITCH_OFF = new ThreadLocal<>();

    private TenantAwareSwitchOffContext() {
        // Utility class
    }

    public static void switchOff(){
    	SWITCH_OFF.set(true);
    }

    public static boolean isSwitchOff() {    	
    	if(SWITCH_OFF.get() != null) {
    		return SWITCH_OFF.get();
    	}
    	
        return false;
    }

    public static void clear() {
    	SWITCH_OFF.remove();
    }
}
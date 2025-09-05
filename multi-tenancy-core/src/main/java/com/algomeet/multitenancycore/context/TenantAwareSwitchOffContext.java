package com.algomeet.multitenancycore.context;

/**
 * Used to switch off the schema
 */
public class TenantAwareSwitchOffContext {

    private static final ThreadLocal<Boolean> switchOff = new ThreadLocal<>();

    private TenantAwareSwitchOffContext() {
        // Utility class
    }

    public static void switchOff(){
    	switchOff.set(true);
    }

    public static boolean isSwitchOff() {    	
    	if(switchOff.get() != null) {
    		return switchOff.get();
    	}
    	
        return false;
    }

    public static void clear() {
    	switchOff.remove();
    }
}
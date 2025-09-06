package com.algomeet.multitenancy.context;

/**
 * Used to switch off the tenant aware session
 */
public class UsePublicSchemaContext {

    private static final ThreadLocal<Boolean> IS_USING_PUBLIC_SCHEMA = new ThreadLocal<>();

    private UsePublicSchemaContext() {
        // Utility class
    }

    public static void switchToPublicSchema(){
    	IS_USING_PUBLIC_SCHEMA.set(true);
    }

    public static boolean isSwitchedToPublicSchema() {    	
    	if(IS_USING_PUBLIC_SCHEMA.get() != null) {
    		return IS_USING_PUBLIC_SCHEMA.get();
    	}
    	
        return false;
    }

    public static void clear() {
    	IS_USING_PUBLIC_SCHEMA.remove();
    }
}
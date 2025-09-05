package com.algomeet.multitenancycore.context;

/**
 * Used to initialize the current tenant globally.
 */
public class TenantContext {

	private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> SWITCH_TENANT_EXPLICITLY = new ThreadLocal<>();

	private TenantContext() {
		// Utility class
	}

	/**
	 * Method used for switching current tenant schema, it must be used in web request level schema switching only
	 * such as inside web filters and interceptors. But once the database connection has been established you 
	 * must use @see #switchTenantExplicitly(String) method instead unless you have properly configured the 
	 * hibernate to get new connection from Multi-tenant connection provider every JPA repository method invocation.
	 * 
	 * To switch current schema explicitly/manually within your code.
	 * @see #switchTenantExplicitly(String)
	 *    
	 * @param tenantId
	 */
	public static void setCurrentTenant(String tenantId) {
		CURRENT_TENANT.set(tenantId);
	}

	public static String getCurrentTenant() {
		return CURRENT_TENANT.get();
	}

	public static void clear() {
		CURRENT_TENANT.remove();
		SWITCH_TENANT_EXPLICITLY.remove();
	}

	/**
	 * Method used for explicitly/manually switching tenant schema within your code. It will guarantee the switching of schema even the 
	 * "getConnection" method was already invoke from Multi-tenant connection provider because it used AOP to force the switching. 
	 * It must be used for explicitly/manually switch schema within your code.
	 * 
	 * Caution: Don't use this method at web request level schema switching such as inside filters and interceptors. It might cause a lot of 
	 * side effects specially in the application performance and possible data integrity instead use @see #setCurrentTenant(String) for 
	 * web request level switching.
	 * 
	 * Note: After calling this method you have to manually call the @see #clear(String) for housekeeping.
	 * 
	 * @param tenantId
	 */
	public static void switchTenantExplicitly(String tenantId) {
		setCurrentTenant(tenantId);
		SWITCH_TENANT_EXPLICITLY.set(true);
	}

	public static boolean isTenantSwitchedExplicitly() {
		if (SWITCH_TENANT_EXPLICITLY.get() != null) {
			return SWITCH_TENANT_EXPLICITLY.get();
		}

		return false;
	}
}
package com.algomeet.multitenancy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Used to switch off tenant aware user session. When method annotated with this annotation  
 * call TenantContext.getCurrentTenant() within the method will return null value. It will 
 * force the app to use the default schema.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAwareSwitchOff {
}

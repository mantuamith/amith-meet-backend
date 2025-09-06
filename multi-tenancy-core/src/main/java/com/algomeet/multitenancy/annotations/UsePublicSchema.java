package com.algomeet.multitenancy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Used to switch off tenant aware user session. When method annotated with this annotation  
 * call TenantContext.getCurrentTenant() within the method will return null value. It will 
 * force the app to use the default schema.
 * 
 * Warning: Be careful when using this annotation, succeeding JPA repository calls inside or outside the methods annotated with this annotation
 * are manually committed using AOP interceptor. Meaning you cannot use/ annotate your method with @Transactional annotation to handle the data 
 * rollback. If there is error in the later part of the sequence of JPA calls the rollback must be handled programmatically by manually removing
 * the saved records prior to the error occurred.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsePublicSchema {
    String tenantId() default ""; // treat "" as null
}
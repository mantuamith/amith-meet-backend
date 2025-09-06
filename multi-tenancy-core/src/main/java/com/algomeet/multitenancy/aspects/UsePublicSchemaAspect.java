package com.algomeet.multitenancy.aspects;

import java.lang.reflect.Method;
import java.sql.Connection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.annotations.UsePublicSchema;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.multitenancy.context.UsePublicSchemaContext;
import com.algomeet.multitenancy.util.SchemaUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/** 
 * Used to intercept the methods annotated with @TenantAwareSwitchOff().
 */
@Slf4j
@Aspect
public class UsePublicSchemaAspect {	
	@Autowired
    private PlatformTransactionManager txManager;
		
	@PersistenceContext
    private EntityManager em;

	@Pointcut("@annotation(usePublicSchema)")
	public void callAt(UsePublicSchema usePublicSchema) {}

	@Around("callAt(usePublicSchema)")
	public Object around(ProceedingJoinPoint pjp, UsePublicSchema usePublicSchema) throws Throwable {
		String tenantId = TenantContext.getCurrentTenant();  
		Object result = null;
		
        // Get the method being called
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        // Get annotation
        UsePublicSchema annotation = method.getAnnotation(UsePublicSchema.class);
        tenantId = StringUtils.hasLength(annotation.tenantId()) ? annotation.tenantId() : null;
		
		try {    
			log.info("Using public schema - start");			
			// Clear the tenant Id 
			TenantContext.clear();
			
			// Set public tenant Id
			TenantContext.setCurrentTenant(tenantId);

			// Continue execution
			result = pjp.proceed();               

		} finally {
			UsePublicSchemaContext.clear();
			log.info("Using public schema - end");
			
			// Re-initialize back the tenant Id
			TenantContext.setCurrentTenant(tenantId);	
			// Switch back to previous schema
			switchSchema(SchemaUtil.getSchemaName(tenantId));
		}
		
		return result;
	}
	
	private void switchSchema(String newSchema) {
		Session session = em.unwrap(Session.class);
    	if (session == null) {
    		return;
    	}
    	
    	DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		TransactionStatus status = txManager.getTransaction(def);    		

		Connection connection = session.doReturningWork(conn -> {
		    // Switch schema
			conn.createStatement().execute("SET search_path TO " + newSchema);
			log.info("AOP - Switching to schema: " + newSchema);
		    return conn;
		});
		// Commit 
		txManager.commit(status);
    }
}
package com.algomeet.multitenancy.aspects;

import java.sql.Connection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.multitenancy.util.SchemaUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Used to force the switching of schema for code explicitly calling @see #TenantContext.switchTenantExplicitly(String) 
 * and methods annotated with @see @TenantAwareSwitchOff().
 */
@Slf4j
@Aspect
public class RepositoryTransactionInterceptor {

	@Autowired
    private PlatformTransactionManager txManager;
		
	@PersistenceContext
    private EntityManager em;

    @Around("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..)) || " +
    		"execution(* com.algomeet..repository..*(..))")
    public Object forceNewTransaction(ProceedingJoinPoint pjp) throws Throwable {    	
   		return switchSchema(pjp, SchemaUtil.getSchemaName(TenantContext.getCurrentTenant()));
    }
    
    private Object switchSchema(ProceedingJoinPoint pjp, String newSchema) throws Throwable {    	
    	Session session = em.unwrap(Session.class);
    	if (session == null) {
    		return pjp.proceed();
    	}
    	
    	DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		TransactionStatus status = txManager.getTransaction(def);    		

		Connection connection = session.doReturningWork(conn -> {			
			if (StringUtils.hasLength(newSchema) 
					&& newSchema.trim().equalsIgnoreCase(conn.getSchema().trim())) {
				// Current and new schema is the same.
				return conn;
			}
			
		    // Switch schema
			conn.createStatement().execute("SET search_path TO " + newSchema);
			log.info("AOP - Swith to schema: " + newSchema);
		    return conn;
		});
		
		try {
			Object result = pjp.proceed(); // run the actual repository method
			txManager.commit(status);

			return result;
		} catch (Throwable ex) {
			try {
				txManager.rollback(status);
			} catch (Exception ex2) {}
			throw ex;
		}
    }
}

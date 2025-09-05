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

import com.algomeet.multitenancy.context.TenantAwareSwitchOffContext;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.multitenancy.hibernate.resolver.TenantIdentifierResolver;
import com.algomeet.multitenancy.util.SchemaUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

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
    	
    	if (TenantAwareSwitchOffContext.isSwitchOff()) {
    		log.info("Tenant aware is switch off");
    		
    		return switchSchema(pjp, TenantIdentifierResolver.DEFAULT_TENANT);
    		
    	} else if(TenantContext.isTenantSwitchedExplicitly()) {
    		return switchSchema(pjp, SchemaUtil.getSchemaName(TenantContext.getCurrentTenant()));
    	}
    	
    	return pjp.proceed();
    }
    
    private Object switchSchema(ProceedingJoinPoint pjp, String schemaName) throws Throwable {
    	DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		TransactionStatus status = txManager.getTransaction(def);    		

		Session session = em.unwrap(Session.class);
		Connection connection = session.doReturningWork(conn -> {
		    // If tenant aware is swich-off use the default schema
			conn.createStatement().execute("SET search_path TO " + schemaName);
			log.info("AOP - Using schema: " + schemaName);
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

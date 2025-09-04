package com.algomeet.multitenancycore.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import com.algomeet.multitenancycore.annotations.TenantAwareSwitchOff;
import com.algomeet.multitenancycore.context.TenantAwareSwitchOffContext;
import com.algomeet.multitenancycore.context.TenantContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
public class TenantAwareSwitchOffAspect {

	@Pointcut("@annotation(tenantAwareSwitchOff)")
	public void callAt(TenantAwareSwitchOff tenantAwareSwitchOff) {}

	@Around("callAt(tenantAwareSwitchOff)")
	public Object around(ProceedingJoinPoint pjp, TenantAwareSwitchOff tenantAwareSwitchOff) throws Throwable {
		String tenantId = TenantContext.getCurrentTenant();  
		Object result = null;
		
		try {    
			log.info("Tenant aware switch off - start");
			
			// Clear the tenant Id to use the default 
			TenantContext.clear();
			// Switch off
			TenantAwareSwitchOffContext.switchOff();

			// Continue execution
			result = pjp.proceed();               

		} finally {
			// re-initialize back the tenant Id
			TenantContext.setCurrentTenant(tenantId);
			TenantAwareSwitchOffContext.clear();
			log.info("Tenant aware switch off - end");
		}

		return result;
	}
}
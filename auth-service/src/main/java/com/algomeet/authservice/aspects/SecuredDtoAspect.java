package com.algomeet.authservice.aspects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.RegisterVerifyResponse;
import com.algomeet.authservice.dto.SecuredDto;
import com.algomeet.authservice.dto.UserResponse;

import lombok.extern.slf4j.Slf4j;
/**
 * Used to secure DTO fields from unauthorized access.
 */
@Slf4j
@Aspect
@Component
public class SecuredDtoAspect {
	
	// Pointcut: target all public methods in @RestController classes
	@Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
	public void controllerMethods() {}

	@Around("controllerMethods()")
	public Object logRequestAndResponse(ProceedingJoinPoint joinPoint) throws Throwable {
		String className = joinPoint.getSignature().getDeclaringTypeName();
		String methodName = joinPoint.getSignature().getName();
		Object[] args = joinPoint.getArgs();

		// Check method parameters
		if (args != null) {
			for (Object arg : args) {
				handleRequestParameter(arg);
			}
		}
		// Proceed with the actual method
		Object result = joinPoint.proceed();		
		if (result != null) {
			handleResponse(result) ;
		}
		
		return result;
	}
	
	private void handleRequestParameter(Object arg) {
		try {			

			if (arg instanceof SecuredDto) {	
				((SecuredDto) arg).secured();
			} 
		} catch(Exception ex) {
			log.error("Error securing request parameter {} ", ex.getMessage(), ex);
			throw ex;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void handleResponse(Object response) {
		try {	
			if (response instanceof RegisterVerifyResponse) {
				RegisterVerifyResponse resp = (RegisterVerifyResponse) response;
				UserResponse userResp = resp.getUser();
				if (userResp instanceof SecuredDto) {
					userResp.secured();
				}
			} else if(response instanceof ResponseEntity) {
				ResponseEntity<Object> respEntity = (ResponseEntity<Object>) response;

				if (respEntity.getBody() instanceof AuthResponse) {
					UserResponse user = ((AuthResponse) respEntity.getBody()).getUser();
					if(user != null) {
						user.secured();
					}
				} else if(respEntity.getBody() instanceof CommonResponse) {
					Object data = ((CommonResponse<Object>) respEntity.getBody()).getData();

					if(data instanceof SecuredDto) {
						((SecuredDto) data).secured();
					}
				}
			}

		} catch(Exception ex) {
			log.error("Error securing response {} ", ex.getMessage(), ex);
			throw ex;
		}
	}
}
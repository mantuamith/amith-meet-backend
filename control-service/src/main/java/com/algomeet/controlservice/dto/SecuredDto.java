package com.algomeet.controlservice.dto;
/**
 * Used to secure the DTO object for any unauthorized access of its fields.
 * Developer should implement the security measures inside secured() method,
 * the method is automatically invoke from AOP class com.algomeet.authservice.aspects.SecuredDtoAspect.java
 */
public interface SecuredDto {
	/**
	 * Implement inside this method the logic on securing the DTO.
	 */
	void secured();
}

package com.algomeet.authservice.dto;
/**
 * Used to secure the DTO object for any unauthorized access of its fields.
 * Security is implemented using AOP class com.algomeet.authservice.aspects.SecuredDtoAspect.java
 */
public interface SecuredDto {
	/**
	 * Implement inside this method the logic on securing the DTO.
	 */
	void secured();
}

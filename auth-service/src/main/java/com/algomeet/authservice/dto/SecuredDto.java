package com.algomeet.authservice.dto;
/**
 * Used to secure the DTO object for any unauthorized access of its fields.
 * Security is implemented using AOP class com.algomeet.authservice.aspects.SecuredDtoAspect.java
 */
public interface SecuredDto {
	void secured();
}

package com.algomeet.notificationservice.util;
/**
 * 
 */

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.constants.JwtConstants;
import com.algomeet.notificationservice.constant.Constants;

import java.security.Key;
import java.util.Base64;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

    private final Key secretKey;
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }
    
    public String getUsername(String token) {        	
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey) // same key used for signing
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("username", String.class); // extract claim
    }
    
    public String getTenantId(String token) {     
		if (StringUtils.hasLength(token)) {
			try {

				Claims claims = Jwts.parserBuilder()
						.setSigningKey(secretKey) // same key used for signing
						.build()
						.parseClaimsJws(token)
						.getBody();

				Object tenantId =  claims.get(JwtConstants.CLAIM_TENANT_ID, Object.class); // extract tenant Id
				return (tenantId != null ? String.valueOf(tenantId) : null);
			} catch (Exception ex) {
				log.error("Error reading token {}" , ex.getMessage(), ex);
			}
		}

		return null;
	}   
    
    public static String getAutorizationToken(HttpServletRequest request) {
    	String token = request.getHeader(Constants.AUTHORIZATION_TOKEN);
    	if (StringUtils.hasLength(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
    		token = token.replace(Constants.TOKEN_PREFIX, "").trim();
    	}
    	
    	return token;
    }
}

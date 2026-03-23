package com.algomeet.xmpp.chatservice.util;
/**
 * 
 */

import java.security.Key;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.constants.JwtConstants;
import com.algomeet.xmpp.chatservice.constant.Constants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtUtil {

    private final Key secretKey;
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }
    
    public boolean validate(String token) {   
    	try {
    		Claims claims = Jwts.parserBuilder()
    				.setSigningKey(secretKey) // same key used for signing
    				.build()
    				.parseClaimsJws(token)
    				.getBody();

    		return true;
    	} catch (Exception ex) {
    		log.error("Error token validation {}", ex.getMessage(), ex);
    	}

    	return false;
    }
    
    public String getUsername(String token) {        	
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey) // same key used for signing
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("username", String.class); // extract claim
    }
    
    public String getUserKey(String token) {        	
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey) // same key used for signing
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("user_key", String.class); // extract claim
    }
        
    public Integer getTenantId(String token) {     
		if (StringUtils.hasLength(token)) {
			try {

				Claims claims = Jwts.parserBuilder()
						.setSigningKey(secretKey) // same key used for signing
						.build()
						.parseClaimsJws(token)
						.getBody();

				return claims.get(JwtConstants.CLAIM_TENANT_ID, Integer.class); // extract tenant Id

			} catch (Exception ex) {
				log.error("Error reading token {}" , ex.getMessage(), ex);
			}
		}

		return null;
	}   
    
    public static String getAutorizationToken(HttpServletRequest request) {
    	String token = request.getHeader(Constants.AUTHORIZATION);
    	
    	if (StringUtils.hasLength(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
    		return token.replace(Constants.TOKEN_PREFIX, "").trim();
    	}
    	    	
    	return token;
    }   
    
    public static String getAutorizationToken(ServerHttpRequest request) {
    	List<String> authorizations = request.getHeaders().get(Constants.AUTHORIZATION);
    	
    	if (!CollectionUtils.isEmpty(authorizations)) {
    		return authorizations.get(0).replace(Constants.TOKEN_PREFIX, "").trim();
    	}
    	     	
    	// Get from header attribute "Sec-WebSocket-Protocol"    		
    	List<String> protocols = request.getHeaders().get(Constants.SEC_WEBSOCKET_PROTOCOL);
     	
    	if (!CollectionUtils.isEmpty(protocols) 
    			&& protocols.get(0).startsWith(Constants.TOKEN_PREFIX)) {
    		
    		return protocols.get(0).replaceAll((Constants.TOKEN_PREFIX + "\\.|" + Constants.TOKEN_PREFIX), "").trim();
    	}
    	    	
    	return null;
    }     
}

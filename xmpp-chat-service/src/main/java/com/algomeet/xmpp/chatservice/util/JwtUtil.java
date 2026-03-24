package com.algomeet.xmpp.chatservice.util;
/**
 * 
 */

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.constants.JwtConstants;
import com.algomeet.xmpp.chatservice.constant.Constants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
    	
    	if (StringUtils.hasLength(token) && token.startsWith(Constants.BEARER_PREFIX)) {
    		return token.replace(Constants.BEARER_PREFIX, "").trim();
    	}
    	    	
    	return token;
    }  
        
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Validate if token is expired or malformed
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            log.error("Token expired: " + e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
        	 log.error("Invalid token: " + e.getMessage());
        }
        return false;
    }

    public String extractUserKey(String token) {
        return extractClaim(token, claims -> claims.get("user_key", String.class));
    }
    
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }
    
    public Integer extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", Integer.class));
    }
}

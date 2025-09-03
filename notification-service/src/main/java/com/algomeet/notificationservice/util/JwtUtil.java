package com.algomeet.notificationservice.util;
/**
 * 
 */

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.constant.Constants;

import java.security.Key;
import java.util.Base64;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final Key secretKey;
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }
    
    public String extractUsername(String token) {        	
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey) // same key used for signing
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("username", String.class); // extract claim
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
    
    public static String getAutorizationToken(HttpServletRequest request) {

    	String token = request.getHeader(Constants.AUTHORIZATION_TOKEN);
    	if (StringUtils.hasLength(token) && token.startsWith(Constants.TOKEN_PREFIX))
    	{
    		token = token.replace(Constants.TOKEN_PREFIX, "").trim();
    	}
    	return token;

    }
}

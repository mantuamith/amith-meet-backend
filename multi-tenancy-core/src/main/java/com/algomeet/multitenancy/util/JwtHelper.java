package com.algomeet.multitenancy.util;
/**
 * 
 */

import java.security.Key;
import java.util.Base64;

import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.constants.HttpHeaderConstants;
import com.algomeet.multitenancy.constants.JwtConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtHelper {
	private static final String TOKEN_PREFIX = "Bearer";
	private final Key secretKey;

	public JwtHelper(String secret) {
		this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
	}

	public String getTenantId(HttpServletRequest request) {     
		String token = getAuthorizationToken(request);
		return getTenantId(token);
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

	private String getAuthorizationToken(HttpServletRequest request) {
		String token = request.getHeader(HttpHeaderConstants.AUTHORIZATION);
		if (StringUtils.hasLength(token) 
				&& token.startsWith(TOKEN_PREFIX)) {

			token = token.replace(TOKEN_PREFIX, "").trim();
			return token;
		}

		return null;
	}
}

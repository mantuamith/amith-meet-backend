package com.algomeet.authservice.util;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.authservice.enums.UserRole;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecurityUtil {

	private static final String KEY_TENANT_ID = "tenantId";
	private static final String KEY_USER_KEY  = "user_key";

	/** Returns the first mapped UserRole or null if not found/invalid. */
	public static UserRole getUserRole() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return null;

		Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
		if (CollectionUtils.isEmpty(authorities))
			return null;

		String raw = null;
		try {
			raw = authorities.iterator().next().getAuthority();
			if (!StringUtils.hasText(raw))
				return null;

			return UserRole.valueOf(raw);
		} catch (IllegalArgumentException iae) {
			log.warn("Unknown authority '{}' for principal '{}'. Enable DEBUG for stacktrace.",
					raw, safeName(auth));
			if (log.isDebugEnabled())
				log.debug("Authority parse failure", iae);
			return null;
		} catch (Exception ex) {
			log.error("Failed retrieving user role for principal '{}'. Enable DEBUG for stacktrace.", safeName(auth));
			if (log.isDebugEnabled())
				log.debug("Unexpected error retrieving role", ex);
			return null;
		}
	}

	/** Returns tenant id, or 0 (public schema) when missing/invalid. */
	public static Integer getTenantId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) return 0;

		try {
			Integer fromDetails = extractTenantId(fromDetailsMap(auth));
			if (fromDetails != null)
				return fromDetails;

			Integer fromPrincipal = extractTenantId(fromPrincipalMap(auth));
			if (fromPrincipal != null)
				return fromPrincipal;
		} catch (Exception ex) {
			log.error("Failed retrieving tenant id for principal '{}'. Enable DEBUG for stacktrace.", safeName(auth));
			if (log.isDebugEnabled())
				log.debug("Unexpected error retrieving tenant id", ex);
		}
		return 0;
	}

	/** Returns user key UUID or null when missing/invalid. */
	public static UUID getUserKey() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) return null;

		try {
			UUID fromDetails = extractUserKey(fromDetailsMap(auth));
			if (fromDetails != null) return fromDetails;

			UUID fromPrincipal = extractUserKey(fromPrincipalMap(auth));
			if (fromPrincipal != null)
				return fromPrincipal;
		} catch (Exception ex) {
			log.error("Failed retrieving user key for principal '{}'. Enable DEBUG for stacktrace.", safeName(auth));
			if (log.isDebugEnabled())
				log.debug("Unexpected error retrieving user key", ex);
		}
		return null;
	}

	public static boolean isAdminUser() {
		return UserRole.ROLE_ADMIN.equals(getUserRole());
	}

	public static boolean isSAUser() {
		return UserRole.ROLE_SA.equals(getUserRole());
	}

	public static boolean isUserHasAdminRole() {
		return isAdminUser() || isSAUser();
	}

	// ---------- helpers ----------

	private static String safeName(Authentication auth) {
		try { return auth.getName(); } catch (Exception ignored) { return "unknown"; }
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> fromDetailsMap(Authentication auth) {
		Object details = auth.getDetails();
		if (details instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> fromPrincipalMap(Authentication auth) {
		Object principal = auth.getPrincipal();
		if (principal instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		return null;
	}

	private static Integer extractTenantId(Map<String, Object> source) {
		if (source == null) return null;
		Object v = source.get(KEY_TENANT_ID);
		if (v == null) return null;

		if (v instanceof Integer i) return i;
		if (v instanceof Number n) return n.intValue();
		if (v instanceof String s) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException ignored) {

			}
		}
		log.warn("Unrecognized type for tenantId: {}", v.getClass().getSimpleName());
		return null;
	}

	private static UUID extractUserKey(Map<String, Object> source) {
		if (source == null) return null;
		Object v = source.get(KEY_USER_KEY);
		if (v == null) return null;

		if (v instanceof UUID u) return u;
		if (v instanceof String s && StringUtils.hasText(s)) {
			try { return UUID.fromString(s); }
			catch (IllegalArgumentException iae) {
				log.warn("Invalid UUID format for '{}': {}", KEY_USER_KEY, s);
			}
		}
		return null;
	}
}

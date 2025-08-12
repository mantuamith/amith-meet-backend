package com.algomeet.authservice.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

import java.util.*;
import java.util.stream.Collectors;

public final class FeignErrorUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private FeignErrorUtil() {
        // Utility class → prevent instantiation
    }

    /**
     * Try to read ["fields"] from the 409 JSON body: { ..., "fields": ["email","username"] }
     */
    public static Set<String> extractDuplicateFields(FeignException e) {
        try {
            byte[] content = e.content();
            if (content == null) return Set.of();

            Map<String, Object> err =
                    objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            Object f = err.get("fields");
            if (f instanceof Collection<?> c) {
                return c.stream().map(Object::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
            return Set.of();
        } catch (Exception ignore) {
            return Set.of();
        }
    }

    /**
     * Fallback: read "code" from body when "fields" is missing.
     */
    public static String extractCode(FeignException e) {
        try {
            byte[] content = e.content();
            if (content == null) return null;

            Map<String, Object> err =
                    objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            Object code = err.get("code");
            return code == null ? null : code.toString();
        } catch (Exception ignore) {
            return null;
        }
    }
}

package com.algomeet.userservice.converter;

import com.algomeet.userservice.dto.Argon2Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class Argon2ConfigConverter implements AttributeConverter<Argon2Config, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Argon2Config attribute) {
        if (attribute == null) return null;
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error serializing Argon2Config", e);
        }
    }

    @Override
    public Argon2Config convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return null;
        try {
            return mapper.readValue(dbData, Argon2Config.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error deserializing Argon2Config", e);
        }
    }
}


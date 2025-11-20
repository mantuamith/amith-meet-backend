// src/main/java/com/algomeet/contactservice/dto/CommonResponse.java
package com.algomeet.contactservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard envelope */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> CommonResponse<T> of(String code, String message, T data) {
        return new CommonResponse<>(code, message, data);
    }
}

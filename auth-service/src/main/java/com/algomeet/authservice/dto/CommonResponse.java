package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.ResponseCode;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommonResponse<T> {
	private String code;
	private String message;
	private T data;

    public CommonResponse() {

    }

    public static <T> CommonResponse<T> from(ResponseCode responseCode, T data) {
        return new CommonResponse<T>(responseCode.getCode(), responseCode.getDefaultMessage(), data);
    }
}

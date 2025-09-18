package com.algomeet.controlservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import com.algomeet.controlservice.enums.ResponseCode;

@Data
@AllArgsConstructor
public class CommonResponse<T> {
	private String code;
	private String message;
	private T data;

    public static <T> CommonResponse<T> from(ResponseCode responseCode, T data) {
        return new CommonResponse<T>(responseCode.getCode(), responseCode.getDefaultMessage(), data);
    }
}

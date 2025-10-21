package com.algomeet.signalingservice.dto;

import com.algomeet.signalingservice.enums.ResponseCode;

import lombok.Data;

@Data
public class CommonResponse<T> {
    protected String code;
    protected String message;
    protected T data;
    
    public CommonResponse() {    	
    }
    
	public CommonResponse(String code, String message) {
		this.code = code;
		this.message = message;
	}   
	
	public CommonResponse(T data, String code, String message) {
		this.data = data;
		this.code = code;
		this.message = message;
	}  
	
	public static <T> CommonResponse<T> from(ResponseCode responseCode) {
		return new CommonResponse<T>(responseCode.getCode(), 
				responseCode.getMessage());
	}
	
	public static <T> CommonResponse<T> from( ResponseCode responseCode, T data) {
		return new CommonResponse<T>(data, responseCode.getCode(), 
				responseCode.getMessage());
	}
}

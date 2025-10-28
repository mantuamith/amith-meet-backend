package com.algomeet.notificationservice.enums;

public enum Language {
	ENGLISH("en"),
	ARABIC("ar"),
	VIETNAMESE("vi"),
	THAI("th"),
	JAPANESE ("ja"),
	CHINESE_TAIWAN("zh-TW"),
	CHINESE_CHINA("zh-CN");
	
	private String code;
	
	Language(String code) {
		this.code = code;
	}
	
	public String getCode() {
		return code;
	}	
}

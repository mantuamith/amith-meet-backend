package com.algomeet.authservice.dto;

import lombok.Data;

@Data
public class SearchUsersFilter {
	 private int page;
	 private int size;
	 private String sortBy;
	 private String direction = "desc";
     
    public SearchUsersFilter() {
    	this.page = 100;
    	this.direction = "desc";
    }
}

package com.algomeet.userservice.dto;

import lombok.Data;

@Data
public class SearchUsersFilter {
	 private String username;
	 private String email;
	 private String phoneNumber;
	 private int page;
	 private int size;
	 private String sortBy;
	 private String direction = "desc";
	 private Integer tenantId;
     
    public SearchUsersFilter() {
    } 
}

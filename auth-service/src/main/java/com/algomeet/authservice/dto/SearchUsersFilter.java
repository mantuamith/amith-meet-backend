package com.algomeet.authservice.dto;

import com.algomeet.authservice.util.SecurityUtil;

import lombok.Data;

@Data
public class SearchUsersFilter implements SecuredDto{
	 private String username;
	 private String email;
	 private String phoneNumber;
	 private int page;
	 private int size;
	 private String sortBy;
	 private String direction = "desc";
	 private Integer tenantId;
     
    public SearchUsersFilter() {
    	this.size = 100;
    	this.sortBy = "id";
    	this.direction = "desc";
    }

	@Override
	public void secured() {
		if (!SecurityUtil.isSAUser()) {
			// Limit the access of non-super admin users to its group tenant Id only.
			tenantId = SecurityUtil.getTenantId();
		}
		
	}
}

package com.algomeet.controlservice.dto;

import java.time.Instant;

import com.algomeet.controlservice.util.SecurityUtil;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantResponse implements SecuredDto{
    private Integer id;
    private String companyName;
    private String brandName;
    private String registrationNumber;
    private String industry;

    private String contactName;
    private String contactEmail;
    private String contactPhone;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;
    private String country;

    private String logoUrl;
    private String themeColor;
    private String timeZone;

    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

	@Override
	public void secured() {
		if (!SecurityUtil.isUserHasAdminRole()) {
			id = null;
		}
	}
}
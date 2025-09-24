package com.algomeet.controlservice.service;

import org.springframework.stereotype.Service;

import com.algomeet.controlservice.util.SecurityUtil;

@Service("securityService") // name matters for SpEL
public class SecurityService {
	public boolean isTenantOwner(Integer tenantId) {
        // Check tenant Id
        return (SecurityUtil.isSAUser()
        		|| tenantId == 0
        		|| SecurityUtil.getTenantId() == tenantId);
    }
}

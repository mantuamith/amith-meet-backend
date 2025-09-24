package com.algomeet.controlservice.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    private Integer id;

    /** Company details */
    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName; // legal/company name

    @Column(name = "brand_name", length = 255)
    private String brandName;   // display/brand name

    @Column(name = "registration_no", length = 100)
    private String registrationNumber;

    @Column(name = "industry", length = 100)
    private String industry;

    /** Contact */
    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    /** Address */
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    /** Branding */
    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(name = "theme_color", length = 20)
    private String themeColor;

    @Column(name = "time_zone", length = 50)
    private String timeZone;

    /** Status */
    @Column(nullable = false)
    private boolean active = true;

    /** Audit */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }    
}
// dto/RegisterInitRequest.java
package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import com.algomeet.authservice.util.SecurityUtil;

//import com.algomeet.authservice.enums.VerificationType; // EMAIL or SMS
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterInitRequest implements SecuredDto{
    @NotBlank private String username;

    @Email private String email;                 // optional (email or phone required)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$",
            message = "phone must be E.164-ish digits with optional +")
    private String phone;                        // optional (email or phone required)

    @NotBlank private String password;           // raw; will be BCrypted at commit

    @NotBlank private String deviceId;
    @NotNull  private DeviceType deviceType;     // WEB | ANDROID | IOS | DESKTOP

    //@NotNull  private VerificationType type;     // EMAIL | SMS  <-- required by your curl

    // optional profile-ish fields
    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    
    private String role;
    private Integer tenantId;

    @AssertTrue(message = "Either email or phone must be provided")
    public boolean isContactProvided() {
        return (email != null && !email.isBlank()) ||
                (phone != null && !phone.isBlank());
    }

	@Override
	public void secured() {
		if (!SecurityUtil.isAdminUser()) {
			setTenantId(null);
			setRole(null);
		}
	}
}

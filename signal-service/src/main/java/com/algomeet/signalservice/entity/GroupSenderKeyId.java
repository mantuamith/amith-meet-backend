package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class GroupSenderKeyId implements Serializable {

    private static final long serialVersionUID = 1L;
	private UUID senderUserKey;	
    
	private Integer senderDeviceId;	
	
	private UUID receiverUserKey;
    
    private Integer receiverDeviceId;    	
    
	private UUID groupId;
	@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupSenderKeyId that = (GroupSenderKeyId) o;
        return Objects.equals(senderUserKey, that.senderUserKey)
                && Objects.equals(senderDeviceId, that.senderDeviceId)
                && Objects.equals(receiverUserKey, that.receiverUserKey)
                && Objects.equals(receiverDeviceId, that.receiverDeviceId)
                && Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderUserKey, senderDeviceId, receiverUserKey, receiverDeviceId, groupId);
    }
}
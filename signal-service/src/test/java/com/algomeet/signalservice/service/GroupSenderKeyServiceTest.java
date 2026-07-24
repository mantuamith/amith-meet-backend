package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;
import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.GroupSenderKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;
import com.algomeet.signalservice.view.GroupSenderKeyView;

@ExtendWith(MockitoExtension.class)
class GroupSenderKeyServiceTest {

    @Mock
    private GroupSenderKeyRepository repository;

    @Mock
    private UserDeviceRepository deviceRepository;

    @InjectMocks
    private GroupSenderKeyService service;

    private static final UUID SENDER_USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RECEIVER_USER_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-3333-222222222222");
    private static final Integer SENDER_DEVICE_ID = 1;
    private static final Integer RECEIVER_DEVICE_ID = 2;

    private List<GroupSenderKeyRequest> requests;

    @BeforeEach
    void setup() {
        requests = new ArrayList<>();
        
        GroupSenderKeyRequest request = new GroupSenderKeyRequest();
        request.setReceiverUserKey(RECEIVER_USER_KEY);
        request.setReceiverDeviceId(RECEIVER_DEVICE_ID);
        request.setSkdmCipher("U29tZVNhbXBsZVNlbmRlclNLRE1EYXRh"); // valid Base64
        requests = List.of(request);
    }

    /* -------------------------------------------------
     * CREATE
     * ------------------------------------------------- */
    @SuppressWarnings("unchecked")
	@Test
    void create_success() {    	
    	UserDevice userDevice = new UserDevice();
    	userDevice.setId(new UserDeviceId(SENDER_USER_KEY, 1));
    	
        GroupSenderKey groupSenderKey = new GroupSenderKey();
        groupSenderKey.setId(new GroupSenderKeyId(SENDER_USER_KEY, SENDER_DEVICE_ID, RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID));
        
        when(deviceRepository.findById(new UserDeviceId(SENDER_USER_KEY, SENDER_DEVICE_ID)))
                .thenReturn(Optional.of(userDevice));

        when(repository.saveAll(any(List.class)))
                .thenReturn(List.of(groupSenderKey));

        List<GroupSenderKeyResponse> response = service.create(SENDER_USER_KEY, SENDER_DEVICE_ID, GROUP_ID, requests);

        assertNotNull(response);
    }

    @Test
    void create_deviceNotFound() {
        when(deviceRepository.findById(new UserDeviceId(SENDER_USER_KEY, SENDER_DEVICE_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.create(SENDER_USER_KEY, SENDER_DEVICE_ID, GROUP_ID, requests)
        );
    }

    /* -------------------------------------------------
     * GET LIST
     * ------------------------------------------------- */
    @Test
    void getList_success() {
    	
    	UserDevice userDevice = new UserDevice();
    	userDevice.setId(new UserDeviceId(SENDER_USER_KEY, 1));
    	
        when(deviceRepository.findById(new UserDeviceId(SENDER_USER_KEY, SENDER_DEVICE_ID)))
                .thenReturn(Optional.of(userDevice));

        GroupSenderKeyView groupSenderKey = new GroupSenderKeyView() {

			@Override
			public UUID getGroupId() {
				return GROUP_ID;
			}

			@Override
			public UUID getReceiverUserKey() {
				return RECEIVER_USER_KEY;
			}

			@Override
			public Integer getReceiverDeviceId() {
				return RECEIVER_DEVICE_ID;
			}

			@Override
			public UUID getSenderUserKey() {
				return SENDER_USER_KEY;
			}

			@Override
			public Integer getSenderDeviceId() {
				return SENDER_DEVICE_ID;
			}

			@Override
			public Instant getCreatedAt() {
				return null;
			}

			@Override
			public Instant getDeletedAt() {
				return null;
			}

			@Override
			public UUID getDistributionId() {
				return null;
			}        	
        };
       
        when(repository.findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
                SENDER_USER_KEY, SENDER_DEVICE_ID, GROUP_ID))
                .thenReturn(List.of(groupSenderKey));

        List<GroupSenderKeyResponse> list = service.getList(SENDER_USER_KEY, SENDER_DEVICE_ID, GROUP_ID);

        assertEquals(1, list.size());
    }

    @Test
    void getList_deviceNotFound() {
        when(deviceRepository.findById(new UserDeviceId(SENDER_USER_KEY, SENDER_DEVICE_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.getList(SENDER_USER_KEY, SENDER_DEVICE_ID, GROUP_ID)
        );
    }

    /* -------------------------------------------------
     * LONG POLL
     * ------------------------------------------------- */
    @Test
    void longPoll_success() {
    	
    	UserDevice userDevice = new UserDevice();
    	userDevice.setId(new UserDeviceId(SENDER_USER_KEY, 1));
    	
        GroupSenderKey groupSenderKey = new GroupSenderKey();
        groupSenderKey.setId(new GroupSenderKeyId(SENDER_USER_KEY, SENDER_DEVICE_ID, RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID));
        
        
        when(deviceRepository.findById(new UserDeviceId(RECEIVER_USER_KEY, RECEIVER_DEVICE_ID)))
                .thenReturn(Optional.of(userDevice));

        when(repository.findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupIdAndDeletedAtIsNull(
                RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID))
                .thenReturn(List.of(groupSenderKey));

        List<GroupSenderKeyResponse> result = service.longPoll(RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID, 1000);

        assertEquals(1, result.size());
    }

    @Test
    void longPoll_deviceNotFound() {
        when(deviceRepository.findById(new UserDeviceId(RECEIVER_USER_KEY, RECEIVER_DEVICE_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.longPoll(RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID, 1000)
        );
    }

    @Test
    void longPoll_timeout_returnsEmpty() {
        when(deviceRepository.findById(new UserDeviceId(RECEIVER_USER_KEY, RECEIVER_DEVICE_ID)))
                .thenReturn(Optional.of(new UserDevice()));

        when(repository.findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupIdAndDeletedAtIsNull(
                RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID))
                .thenReturn(List.of()); // no pending keys

        List<GroupSenderKeyResponse> result = service.longPoll(RECEIVER_USER_KEY, RECEIVER_DEVICE_ID, GROUP_ID, 200);

        assertEquals(0, result.size());
    }    
}

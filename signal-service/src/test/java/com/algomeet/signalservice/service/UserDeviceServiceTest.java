package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.algomeet.signalservice.dto.DevicePreKeyBundleRequest;
import com.algomeet.signalservice.dto.DevicePreKeyBundleResponse;
import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.entity.*;
import com.algomeet.signalservice.exceptions.DeviceExistsException;
import com.algomeet.signalservice.exceptions.OneTimePreKeyExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.*;

@ExtendWith(MockitoExtension.class)
class UserDeviceServiceTest {

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserDeviceRepository repository;

    @Mock
    private SignedPreKeyRepository signedPreKeyRepository;

    @Mock
    private KyberPreKeyRepository kyberPreKeyRepository;

    @Mock
    private OneTimePreKeyRepository oneTimePreKeyRepository;

    @InjectMocks
    private UserDeviceService service;

    private UserDeviceRequest deviceRequest;

    @BeforeEach
    void setup() {
        deviceRequest = new UserDeviceRequest();
        deviceRequest.setIdentityKey("Base64IdentityKey==");
        deviceRequest.setRegistrationId(1);
    }

    /* -------------------------------------------------
     * REGISTER DEVICE
     * ------------------------------------------------- */

    @Test
    void registerDevice_success() {
        when(repository.findMaxDeviceIdByUserKey(USER_KEY))
                .thenReturn(Optional.of(0));

        when(repository.save(any(UserDevice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserDeviceResponse response =
                service.registerDevice(USER_KEY, deviceRequest);

        assertNotNull(response);
        verify(repository).save(any(UserDevice.class));
    }

    @Test
    void registerDevice_duplicate() {
        when(repository.findMaxDeviceIdByUserKey(USER_KEY))
                .thenReturn(Optional.of(0));

        when(repository.save(any(UserDevice.class)))
                .thenThrow(DataIntegrityViolationException.class);

        assertThrows(DeviceExistsException.class,
                () -> service.registerDevice(USER_KEY, deviceRequest));
    }

    /* -------------------------------------------------
     * GET DEVICES
     * ------------------------------------------------- */

    @Test
    void getDevicesByUser_success() {
        UserDevice device = new UserDevice();
        device.setId(new UserDeviceId(USER_KEY, 1));

        when(repository.findByIdUserKeyIn(List.of(USER_KEY)))
                .thenReturn(List.of(device));

        List<UserDeviceResponse> result =
                service.getDevicesByUserKeys(List.of(USER_KEY));

        assertEquals(1, result.size());
    }

    /* -------------------------------------------------
     * UPDATE DEVICE
     * ------------------------------------------------- */

    @Test
    void updateDevice_success() {
        UserDevice device = new UserDevice();
        device.setId(new UserDeviceId(USER_KEY, 1));

        when(repository.findById(any()))
                .thenReturn(Optional.of(device));

        when(repository.save(any()))
                .thenReturn(device);

        UserDeviceResponse response =
                service.updateDevice(USER_KEY, 1, deviceRequest);

        assertNotNull(response);
        verify(repository).save(device);
    }

    @Test
    void updateDevice_notFound() {
        when(repository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.updateDevice(USER_KEY, 1, deviceRequest));
    }

    /* -------------------------------------------------
     * DELETE DEVICE
     * ------------------------------------------------- */

    @Test
    void deleteDevice_success() {
        when(repository.findById(any()))
                .thenReturn(Optional.of(new UserDevice()));

        doNothing().when(signedPreKeyRepository).deleteById(any());
        doNothing().when(kyberPreKeyRepository).deleteById(any());
        doNothing().when(oneTimePreKeyRepository)
                .deleteByUserKeyAndDeviceId(any(), anyInt());
        doNothing().when(repository).deleteById(any());

        assertDoesNotThrow(() ->
                service.deleteDevice(USER_KEY, 1));
    }

    @Test
    void deleteDevice_notFound() {
        when(repository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.deleteDevice(USER_KEY, 1));
    }

    /* -------------------------------------------------
     * CREATE DEVICE PRE-KEY BUNDLE
     * ------------------------------------------------- */

    @Test
    void createDevicePreKeyBundle_success() {
        DevicePreKeyBundleRequest request = TestFixtures.devicePreKeyBundleRequest();

        when(repository.findById(any()))
                .thenReturn(Optional.of(new UserDevice()));

        when(signedPreKeyRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        when(kyberPreKeyRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        when(oneTimePreKeyRepository.saveAll(any()))
                .thenAnswer(i -> i.getArgument(0));

        DevicePreKeyBundleResponse response =
                service.createDevicePreKeyBundle(USER_KEY, 1, request);

        assertNotNull(response);
        assertNotNull(response.getSignedPreKey());
        assertNotNull(response.getKyberPreKey());
        assertEquals(1, response.getOneTimePreKeys().size());
    }

    @Test
    void createDevicePreKeyBundle_deviceNotFound() {
        when(repository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.createDevicePreKeyBundle(
                        USER_KEY, 1, TestFixtures.devicePreKeyBundleRequest()));
    }

    @Test
    void createDevicePreKeyBundle_oneTimeKeyExists() {
        when(repository.findById(any()))
                .thenReturn(Optional.of(new UserDevice()));

        when(oneTimePreKeyRepository.saveAll(any()))
                .thenThrow(DataIntegrityViolationException.class);

        assertThrows(OneTimePreKeyExistsException.class,
                () -> service.createDevicePreKeyBundle(
                        USER_KEY, 1, TestFixtures.devicePreKeyBundleRequest()));
    }

    /* -------------------------------------------------
     * MARK DEVICE UPDATED
     * ------------------------------------------------- */

    @Test
    void markDeviceAsUpdated_success() {
        UserDevice device = new UserDevice();
        device.setUpdatedAt(Instant.EPOCH);

        when(repository.findById(any()))
                .thenReturn(Optional.of(device));

        when(repository.save(any()))
                .thenReturn(device);

        service.markDeviceAsUpdated(USER_KEY, 1);

        assertTrue(device.getUpdatedAt().isAfter(Instant.EPOCH));
    }

    @Test
    void markDeviceAsUpdated_notFound() {
        when(repository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.markDeviceAsUpdated(USER_KEY, 1));
    }
}

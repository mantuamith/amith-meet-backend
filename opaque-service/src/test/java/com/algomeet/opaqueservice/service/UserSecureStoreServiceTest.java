package com.algomeet.opaqueservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.opaqueservice.dto.UserMasterSecretRequest;
import com.algomeet.opaqueservice.entity.UserSecureStore;
import com.algomeet.opaqueservice.entity.UserSecureStoreId;
import com.algomeet.opaqueservice.enums.CredentialType;
import com.algomeet.opaqueservice.repository.UserSecureStoreRepository;

@ExtendWith(MockitoExtension.class)
class UserSecureStoreServiceTest {

    @Mock
    private UserSecureStoreRepository repository;

    @InjectMocks
    private UserSecureStoreService service;

    private UUID userKey;
    private CredentialType type;

    @BeforeEach
    void setUp() {
        userKey = UUID.randomUUID();
        type = CredentialType.PASSCODE;
    }

    // -------------------- GET --------------------

    @Test
    void getMasterSecret_shouldReturnEntity() {
        UserSecureStore store = mock(UserSecureStore.class);
        when(repository.findByIdUserKeyAndIdType(userKey, type)).thenReturn(store);

        UserSecureStore result = service.getMasterSecret(userKey, type);

        assertThat(result).isSameAs(store);
        verify(repository).findByIdUserKeyAndIdType(userKey, type);
    }

    @Test
    void getSecrets_shouldReturnList() {
        List<UserSecureStore> stores = List.of(mock(UserSecureStore.class));
        when(repository.findByIdUserKey(userKey)).thenReturn(stores);

        List<UserSecureStore> result = service.getSecrets(userKey);

        assertThat(result).hasSize(1);
        verify(repository).findByIdUserKey(userKey);
    }

    // -------------------- SAVE (CREATE) --------------------

    @Test
    void save_shouldCreateNewWhenNotExists() {
        UserMasterSecretRequest req = new UserMasterSecretRequest();
        req.setType(type);
        req.setMasterSecretKey("secret");
        req.setAlgorithm("alg");
        req.setVersion("v1");
        req.setSalt("salt");

        when(repository.findByIdUserKeyAndIdType(userKey, type)).thenReturn(null);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSecureStore result = service.save(userKey, "rec", req);

        ArgumentCaptor<UserSecureStore> captor = ArgumentCaptor.forClass(UserSecureStore.class);
        verify(repository).save(captor.capture());

        UserSecureStore saved = captor.getValue();
        UserSecureStoreId id = saved.getId();

        assertThat(id.getUserKey()).isEqualTo(userKey);
        assertThat(id.getType()).isEqualTo(type);
        assertThat(saved.getRec()).isEqualTo("rec");
        assertThat(saved.getMasterSecretKey()).isEqualTo("secret");
        assertThat(saved.getAlgorithm()).isEqualTo("alg");
        assertThat(saved.getVersion()).isEqualTo("v1");
        assertThat(saved.getSalt()).isEqualTo("salt");

        assertThat(result).isSameAs(saved);
    }

    // -------------------- SAVE (UPDATE) --------------------

    @Test
    void save_shouldUpdateExistingOnlyProvidedFields() {
        UserSecureStore existing = mock(UserSecureStore.class);

        UserMasterSecretRequest req = new UserMasterSecretRequest();
        req.setType(type);
        req.setMasterSecretKey("new-secret");
        req.setAlgorithm("");
        req.setVersion(null);
        req.setSalt("new-salt");

        when(repository.findByIdUserKeyAndIdType(userKey, type)).thenReturn(existing);
        when(repository.save(existing)).thenReturn(existing);

        UserSecureStore result = service.save(userKey, "new-rec", req);

        verify(existing).setMasterSecretKey("new-secret");
        verify(existing).setRec("new-rec");
        verify(existing).setSalt("new-salt");

        verify(existing, never()).setAlgorithm(any());
        verify(existing, never()).setVersion(any());

        assertThat(result).isSameAs(existing);
    }

    // -------------------- DELETE ONE --------------------

    @Test
    void delete_shouldDeleteWhenExists() {
        UserSecureStore store = mock(UserSecureStore.class);
        when(repository.findByIdUserKeyAndIdType(userKey, type)).thenReturn(store);

        service.delete(userKey, type);

        verify(repository).delete(store);
    }

    @Test
    void delete_shouldDoNothingWhenNotExists() {
        when(repository.findByIdUserKeyAndIdType(userKey, type)).thenReturn(null);

        service.delete(userKey, type);

        verify(repository, never()).delete(any());
    }

    // -------------------- DELETE ALL --------------------

    @Test
    void deleteAll_shouldDeleteAllForUser() {
        List<UserSecureStore> stores = List.of(
                mock(UserSecureStore.class),
                mock(UserSecureStore.class)
        );

        when(repository.findByIdUserKey(userKey)).thenReturn(stores);

        service.deleteAll(userKey);

        verify(repository).deleteAll(stores);
    }
}


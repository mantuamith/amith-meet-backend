package com.algomeet.authservice.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private RefreshTokenStore refreshTokenStore;

    private final String testToken = "sample-jwt-token";
    private final String testEmail = "user@example.com";
    private final long ttlDays = 30;

    @BeforeEach
    void setUp() {
        // Inject the @Value field manually since we aren't using a full Spring context
        ReflectionTestUtils.setField(refreshTokenStore, "refreshTokenTtlDays", ttlDays);      
    }

    @Test
    @DisplayName("Should save token and add to user set with correct TTL")
    void save_Success() {
    	
        // Connect the ops to the template mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        
        refreshTokenStore.save(testToken, testEmail);

        // Verify String entry (RT:token -> email)
        verify(valueOperations).set(eq("RT:" + testToken), eq(testEmail), eq(ttlDays), eq(TimeUnit.DAYS));
        
        // Verify Set entry (RTE:email -> token)
        verify(setOperations).add(eq("RTE:" + testEmail), eq(testToken));
        
        // Verify TTL on the set
        verify(redisTemplate).expire(eq("RTE:" + testEmail), eq(ttlDays + 1), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Should return true if token exists in Redis")
    void exists_ReturnsTrue() {
        when(redisTemplate.hasKey("RT:" + testToken)).thenReturn(true);

        boolean exists = refreshTokenStore.exists(testToken);

        assertTrue(exists);
        verify(redisTemplate).hasKey("RT:" + testToken);
    }

    @Test
    @DisplayName("Should remove token from both String and User Set")
    void remove_Success() {
        // Connect the ops to the template mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        
        when(valueOperations.get("RT:" + testToken)).thenReturn(testEmail);

        refreshTokenStore.remove(testToken);

        verify(redisTemplate).delete("RT:" + testToken);
        verify(setOperations).remove("RTE:" + testEmail, testToken);
    }

    @Test
    @DisplayName("Should do nothing if removing a non-existent token")
    void remove_NonExistentToken() {
    	when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        refreshTokenStore.remove("fake-token");

        verify(redisTemplate, never()).delete(anyString());
        verify(setOperations, never()).remove(anyString(), any());
    }

    @Test
    @DisplayName("Should revoke all tokens associated with an email")
    void revokeAllForUser_Success() {
    	when(redisTemplate.opsForSet()).thenReturn(setOperations);
        
        Set<String> tokens = Set.of("token1", "token2");
        String userKey = "RTE:" + testEmail;

        when(setOperations.members(userKey)).thenReturn(tokens);

        refreshTokenStore.revokeAllForUser(testEmail);

        // Verify each token was deleted
        verify(redisTemplate).delete("RT:token1");
        verify(redisTemplate).delete("RT:token2");
        
        // Verify the user set itself was deleted
        verify(redisTemplate).delete(userKey);
    }

    @Test
    @DisplayName("Should return email for a valid token")
    void getEmailForToken_ReturnsEmail() {
    	when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("RT:" + testToken)).thenReturn(testEmail);

        String result = refreshTokenStore.getEmailForToken(testToken);

        assertEquals(testEmail, result);
    }
}
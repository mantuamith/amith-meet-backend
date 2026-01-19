package com.algomeet.notificationservice.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.properties.RedisStreamConfigProperties;

class NotificationStreamConsumerTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Mock
    private NotificationMessageHandler notificationConsumer;

    @Mock
    private RedisStreamConfigProperties redisStreamConfigProperties;

    @InjectMocks
    private NotificationStreamConsumer consumer;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // Mock stream key
        when(redisStreamConfigProperties.getNotificationStreamKey()).thenReturn("test-stream");

        // Mock Redis connection
        when(connectionFactory.getConnection()).thenReturn(connection);
    }

    @Test
    void init_shouldCreateConsumerGroupAndStartContainer() throws Exception {
        when(connection.xGroupCreate(
                any(byte[].class),
                anyString(),
                any(),
                anyBoolean()
        )).thenReturn("OK");

        consumer.init();

        verify(connection).xGroupCreate(
                eq("test-stream".getBytes()),
                anyString(),
                any(),
                eq(true)
        );
    }

    @Test
    void onMessage_shouldHandleAndAckMessage() throws SQLException {
        RecordId recordId = mock(RecordId.class);
        when(recordId.getValue()).thenReturn("123");

        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(recordId);
        when(record.getValue())
            .thenReturn(Map.of(Constants.REDIS_STREAM_MESSAGE_KEY_MESSAGE, "payload"));

        consumer.onMessage(record);

        verify(notificationConsumer).handleMessage("payload");

        verify(connectionFactory.getConnection()).xAck(
            eq("test-stream".getBytes()),
            anyString(),
            eq(recordId)
        );
    }
}

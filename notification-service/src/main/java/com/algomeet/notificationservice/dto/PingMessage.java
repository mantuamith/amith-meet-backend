package com.algomeet.notificationservice.dto;

import java.util.Date;

import lombok.Data;

@Data
public class PingMessage extends ExchangeMessage {
    private Date timestamp;   
}
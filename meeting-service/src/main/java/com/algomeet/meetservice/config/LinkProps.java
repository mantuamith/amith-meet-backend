package com.algomeet.meetservice.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class LinkProps {
    @Value("${algomeet.links.base:https://meet.algoframe.in}")
    private String base;
}

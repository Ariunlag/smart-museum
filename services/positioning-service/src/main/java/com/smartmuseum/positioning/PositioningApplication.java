package com.smartmuseum.positioning;

import com.smartmuseum.positioning.config.MuseumProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MuseumProperties.class)
public class PositioningApplication {
    public static void main(String[] args) {
        SpringApplication.run(PositioningApplication.class, args);
    }
}
package com.smartmuseum.artinfo;

import com.smartmuseum.artinfo.config.MuseumProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MuseumProperties.class)
public class ArtInfoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArtInfoApplication.class, args);
    }
}
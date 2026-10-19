package com.evela.sigotrab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SigotrabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigotrabApplication.class, args);
    }

}

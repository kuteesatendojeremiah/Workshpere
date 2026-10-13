package com.worksphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorksphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorksphereApplication.class, args);
    }
}

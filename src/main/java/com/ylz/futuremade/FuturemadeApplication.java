package com.ylz.futuremade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FuturemadeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuturemadeApplication.class, args);
    }

}

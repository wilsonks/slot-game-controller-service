package com.slotcentral.gamecontroller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GameControllerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameControllerApplication.class, args);
    }
}

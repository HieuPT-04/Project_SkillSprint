package com.skillsprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillSprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillSprintApplication.class, args);
    }
}

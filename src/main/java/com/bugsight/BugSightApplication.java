package com.bugsight;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bugsight.mapper")
public class BugSightApplication {
    public static void main(String[] args) {
        SpringApplication.run(BugSightApplication.class, args);
    }
}

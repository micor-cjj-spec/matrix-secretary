package com.kailei.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.dromara.autotable.springboot.EnableAutoTable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAutoTable(basePackages = "com.kailei.demo.entity")
@MapperScan("com.kailei.demo.mapper")
@EnableScheduling
@SpringBootApplication
public class AiSecretaryDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSecretaryDemoApplication.class, args);
    }
}

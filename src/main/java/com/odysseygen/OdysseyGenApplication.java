package com.odysseygen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.odysseygen.mapper")
public class OdysseyGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(OdysseyGenApplication.class, args);
    }

}

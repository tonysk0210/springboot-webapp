package com.company.ConsumingRestService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.company.ConsumingRestService.proxy") // 指定 OpenFeign REST Client 的所在套件
public class ConsumingRestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumingRestServiceApplication.class, args);
    }

}

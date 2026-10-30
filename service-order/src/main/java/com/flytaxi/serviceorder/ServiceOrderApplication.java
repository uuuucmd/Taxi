package com.flytaxi.serviceorder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.flytaxi.internalCommon.trace.TraceIdConfiguration;

@SpringBootApplication
@MapperScan("com.flytaxi.serviceorder.mapper")
@EnableFeignClients
@EnableScheduling
@Import(TraceIdConfiguration.class)
public class ServiceOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceOrderApplication.class, args);
    }

}

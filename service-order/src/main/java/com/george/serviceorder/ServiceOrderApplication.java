package com.george.serviceorder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.george.internalCommon.trace.TraceIdConfiguration;

@SpringBootApplication
@MapperScan("com.george.serviceorder.mapper")
@EnableFeignClients
@EnableScheduling
@Import(TraceIdConfiguration.class)
public class ServiceOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceOrderApplication.class, args);
    }

}

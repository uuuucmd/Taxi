package com.flytaxi.servicepay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import com.flytaxi.internalCommon.trace.TraceIdConfiguration;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(TraceIdConfiguration.class)
public class ServicePayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicePayApplication.class, args);
    }



}

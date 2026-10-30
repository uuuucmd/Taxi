package com.flytaxi.apipassenger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.flytaxi.internalCommon.trace.TraceIdConfiguration;
import org.springframework.context.annotation.Import;

/**
 * @Date: 2024-10-12-18:23
 * @Description: com.flytaxi.apipassenger
 */

@EnableDiscoveryClient
@EnableFeignClients
@Import(TraceIdConfiguration.class)
@SpringBootApplication
public class ApiPassengerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiPassengerApplication.class);
    }
}

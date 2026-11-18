package com.flytaxi.servicessepush.remote;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.DriverUserExistsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("service-driver-user")
public interface DriverUserClient {

    @GetMapping("/check-driver/{driverPhone}")
    ResponseResult<DriverUserExistsResponse> getUser(@PathVariable("driverPhone") String driverPhone);
}

package com.flytaxi.servicessepush.remote;

import com.flytaxi.internalCommon.dto.PassengerUser;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("service-passenger-user")
public interface PassengerUserClient {

    @GetMapping("/get-user/{phone}")
    ResponseResult<PassengerUser> getUser(@PathVariable("phone") String phone);
}

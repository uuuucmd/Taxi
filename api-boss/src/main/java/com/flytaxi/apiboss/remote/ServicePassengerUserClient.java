package com.flytaxi.apiboss.remote;

import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("service-passenger-user")
public interface ServicePassengerUserClient {

    @RequestMapping(method = RequestMethod.GET, value = "/users")
    ResponseResult listPassengers(@RequestParam("page") long page,
            @RequestParam("pageSize") long pageSize,
            @RequestParam("keyword") String keyword,
            @RequestParam("state") Integer state);

    @RequestMapping(method = RequestMethod.PUT, value = "/user/state")
    ResponseResult updatePassengerState(@RequestParam("passengerPhone") String passengerPhone,
            @RequestParam("state") Integer state);
}

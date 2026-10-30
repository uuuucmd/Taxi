package com.flytaxi.apiboss.remote;

import com.flytaxi.internalCommon.dto.Car;
import com.flytaxi.internalCommon.dto.DriverCarBindingRelationship;
import com.flytaxi.internalCommon.dto.DriverUser;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @Date: 2024-11-01-10:57
 * @Description: This class provides connection with the remote server
 */

@FeignClient("service-driver-user")
public interface ServiceDriverUserClient {

    @RequestMapping(method = RequestMethod.GET, value = "/users")
    ResponseResult listDrivers(@RequestParam("page") long page,
            @RequestParam("pageSize") long pageSize, @RequestParam("keyword") String keyword,
            @RequestParam("state") Integer state);

    @RequestMapping(method = RequestMethod.POST, value = "/user")
    ResponseResult addDriverUser(@RequestBody DriverUser driverUser);

    @RequestMapping(method = RequestMethod.PUT, value = "/user")
    ResponseResult updateDriverUser(@RequestBody DriverUser driverUser);

    @RequestMapping(method = RequestMethod.POST, value = "/car")
    ResponseResult addCar(@RequestBody Car car);

    @RequestMapping(method = RequestMethod.POST, value = "/driver-car-binding-relationship/bind")
    ResponseResult bind(@RequestBody DriverCarBindingRelationship relationship);

    @RequestMapping(method = RequestMethod.POST, value = "/driver-car-binding-relationship/unbind")
    ResponseResult unbind(@RequestBody DriverCarBindingRelationship relationship);
}

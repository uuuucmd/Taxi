package com.george.serviceorder.remote;


import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.OrderDriverResponse;
import com.george.internalCommon.response.TerminalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * This class is used to connect with the operating system of driver users to get information about
 * both drivers and their cars
 */
@FeignClient("service-driver-user")
public interface ServiceDriverUserClient {

    @GetMapping("/city-driver/is-alailable-driver")
    ResponseResult<Boolean> isAvailableDriver(@RequestParam String cityCode);

    @GetMapping("/city-driver/available-cars")
    ResponseResult<List<TerminalResponse>> getAvailableCars(@RequestParam String cityCode);

    @GetMapping("/get-available-driver/{carId}")
    ResponseResult<OrderDriverResponse> getAvailableDriver(@PathVariable("carId") Long carId);

    @GetMapping("/driver-car-binding-relationship")
    ResponseResult<DriverCarBindingRelationship> getBindingByPhone(@RequestParam String driverPhone);

    @GetMapping("/car")
    ResponseResult<Car> getCarById(@RequestParam  Long carId);
}

package com.flytaxi.apidriver.remote;

import com.flytaxi.internalCommon.dto.*;
import com.flytaxi.internalCommon.response.DriverUserExistsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * @Date: 2024-11-01-18:26
 * @Description: This class is used to connect to the driver's back office system
 */
@FeignClient("service-driver-user")
public interface ServiceDriverUserClient {

    @GetMapping("/driver-vehicle")
    ResponseResult<Car> getVehicle(@RequestParam String driverPhone);

    @PostMapping("/driver-vehicle")
    ResponseResult<Car> registerVehicle(@RequestParam String driverPhone, @RequestBody Car car);

    @PostMapping("/driver-vehicle/lock")
    ResponseResult<Car> lockVehicle(@RequestParam String driverPhone, @RequestParam String vehicleNo);

    @PostMapping("/driver-vehicle/map")
    ResponseResult<Car> initializeVehicleMap(@RequestParam String driverPhone);

    // The function used to update the driver info
    @RequestMapping(method = RequestMethod.PUT, value = "/user")
    ResponseResult updateUser(@RequestBody DriverUser user);

    // The function is used to check the identity of the driver using phone number
    @RequestMapping(method = RequestMethod.GET, value = "/check-driver/{driverPhone}")
    ResponseResult<DriverUserExistsResponse> checkDriver(@PathVariable("driverPhone") String driverPhone);

    // The function is used to obtain the vehicle info
    @RequestMapping(method = RequestMethod.GET, value = "/car")
    ResponseResult<Car> getCarById(@RequestParam Long carId);

    // This method is used to modify the driver's working state
    @RequestMapping(method = RequestMethod.POST, value="/driver-user-work-status")
    ResponseResult changeWorkStatus(@RequestBody DriverUserWorkStatus driverUserWorkStatus);

    // This method is used to obtain the driver-vehicle binding relationship
    @GetMapping("/driver-car-binding-relationship")
    ResponseResult<DriverCarBindingRelationship> getDriverCarRelationShip(@RequestParam String driverPhone);

    // This method is used to obtain the driver's working status
    @GetMapping("/work-status")
    ResponseResult<DriverUserWorkStatus> getWorkStatus(@RequestParam Long driverId);
}

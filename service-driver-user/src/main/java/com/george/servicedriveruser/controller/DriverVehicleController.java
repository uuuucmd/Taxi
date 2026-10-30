package com.george.servicedriveruser.controller;

import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.service.DriverVehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/driver-vehicle")
public class DriverVehicleController {
    @Autowired
    private DriverVehicleService service;

    @GetMapping
    public ResponseResult<Car> get(@RequestParam String driverPhone) {
        return service.getVehicle(driverPhone);
    }

    @PostMapping
    public ResponseResult<Car> register(@RequestParam String driverPhone, @RequestBody Car car) {
        return service.register(driverPhone, car);
    }

    @PostMapping("/map")
    public ResponseResult<Car> initializeMap(@RequestParam String driverPhone) {
        return service.initializeMap(driverPhone);
    }
}

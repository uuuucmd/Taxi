package com.flytaxi.apiboss.controller;

import com.flytaxi.apiboss.service.PassengerUserService;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PassengerUserController {

    @Autowired
    private PassengerUserService passengerUserService;

    @GetMapping("/passenger-users")
    public ResponseResult listPassengers(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer state) {
        return passengerUserService.listPassengers(page, pageSize, keyword, state);
    }

    @PutMapping("/passenger-user/state")
    public ResponseResult updatePassengerState(
            @RequestParam String passengerPhone,
            @RequestParam Integer state) {
        return passengerUserService.updatePassengerState(passengerPhone, state);
    }
}

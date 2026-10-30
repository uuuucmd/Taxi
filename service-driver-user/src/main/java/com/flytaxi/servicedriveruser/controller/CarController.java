package com.flytaxi.servicedriveruser.controller;


import com.flytaxi.internalCommon.dto.Car;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.servicedriveruser.service.CarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * This class is to control the client side
 */
@RestController
public class CarController {
    @Autowired
    private CarService service;

    /**
     * The function is to add a mew car in dataset
     * @param car - the car to be added
     * @return
     */
    @PostMapping("/car")
    public ResponseResult addCar(@RequestBody Car car) {

        return service.addCar(car);
    }

    /**
     * This function aims to return the car info by its id
     * @param carId
     * @return
     */
    @GetMapping("/car")
    public ResponseResult getCarById(Long carId){

        return service.getCarById(carId);
    }
}

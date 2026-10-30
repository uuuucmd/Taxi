package com.flytaxi.apiboss.service;

import com.flytaxi.apiboss.remote.ServiceDriverUserClient;
import com.flytaxi.internalCommon.dto.Car;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Date: 2024-11-03-16:35
 * @Description: This class is aimed to add a car using remote connection
 */
@Service
public class CarService {

    @Autowired
    ServiceDriverUserClient client;

    public ResponseResult addCar(Car car) {
        return client.addCar(car);
    }
}

package com.flytaxi.apidriver.service;

import com.flytaxi.apidriver.remote.ServiceDriverUserClient;
import com.flytaxi.internalCommon.dto.DriverCarBindingRelationship;
import com.flytaxi.internalCommon.dto.DriverUser;
import com.flytaxi.internalCommon.dto.DriverUserWorkStatus;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Date: 2024-11-01-18:24
 * @Description: This class provides specific services to the controller layer to process driver user information
 */
@Service
public class UserService {

    @Autowired
    private ServiceDriverUserClient client;

    public ResponseResult updateUser(DriverUser driverUser) {
        return client.updateUser(driverUser);
    }

    public ResponseResult changeWorkStatus(DriverUserWorkStatus driverUserWorkStatus){
        return client.changeWorkStatus(driverUserWorkStatus);
    }

    public ResponseResult<DriverCarBindingRelationship> getDriverCarBindingRelationship(String driverPhone){
        return client.getDriverCarRelationShip(driverPhone);

    }

    public ResponseResult<DriverUserWorkStatus> getWorkStatus(Long driverId){
        return client.getWorkStatus(driverId);
    }
}

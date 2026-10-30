package com.flytaxi.apiboss.service;

import com.flytaxi.apiboss.remote.ServiceDriverUserClient;
import com.flytaxi.internalCommon.dto.DriverCarBindingRelationship;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Date: 2024-11-03-17:26
 * @Description: This class provides
 */
@Service
public class DriverCarBindingRelationshipService {

    @Autowired
    ServiceDriverUserClient client;

    public ResponseResult bind(DriverCarBindingRelationship relationship) {
        return client.bind(relationship);
    }

    public ResponseResult unbind(DriverCarBindingRelationship relationship) {
        return client.unbind(relationship);
    }
}

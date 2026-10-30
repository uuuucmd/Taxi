package com.flytaxi.servicemap.service;


import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.servicemap.remote.ServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceFromMapService {

    @Autowired
    private ServiceClient serviceClient;


    public ResponseResult add(String name){

        return serviceClient.add(name);

    }
}

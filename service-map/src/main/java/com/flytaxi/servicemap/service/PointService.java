package com.flytaxi.servicemap.service;


import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.PointRequest;
import com.flytaxi.servicemap.remote.PointClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PointService {

    @Autowired
    PointClient pointClient;

    public ResponseResult upload(PointRequest pointRequest){

        return pointClient.upload(pointRequest);
    }
}

package com.flytaxi.servicemap.service;


import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.TrackResponse;
import com.flytaxi.servicemap.remote.TrackClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrackService {

    @Autowired
    TrackClient trackClient;

    public ResponseResult<TrackResponse> add(String tid){

        return trackClient.add(tid);
    }
}

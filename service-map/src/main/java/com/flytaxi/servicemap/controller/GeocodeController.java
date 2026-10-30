package com.flytaxi.servicemap.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.servicemap.remote.GeocodeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GeocodeController {

    @Autowired
    private GeocodeClient geocodeClient;

    @GetMapping("/locations/city-code")
    public ResponseResult<Map<String, String>> cityCode(@RequestParam String address) {
        return geocodeClient.cityCode(address);
    }

    @GetMapping("/geocode")
    public ResponseResult<Map<String, String>> geocode(@RequestParam String address) {
        return geocodeClient.geocode(address);
    }
}

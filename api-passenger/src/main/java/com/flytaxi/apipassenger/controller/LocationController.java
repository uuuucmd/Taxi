package com.flytaxi.apipassenger.controller;

import com.flytaxi.apipassenger.remote.ServiceMapClient;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LocationController {

    @Autowired
    private ServiceMapClient serviceMapClient;

    @GetMapping("/locations/geocode")
    public ResponseResult<Map<String, String>> geocode(@RequestParam String address) {
        if (address.isBlank() || address.length() > 200 || address.contains("|")) {
            return ResponseResult.fail(0, "请输入有效的详细地址");
        }
        try {
            ResponseResult<Map<String, String>> result = serviceMapClient.geocode(address.trim());
            return result == null ? ResponseResult.fail(0, "地址解析服务暂不可用，请稍后重试") : result;
        } catch (RuntimeException exception) {
            return ResponseResult.fail(0, "地址解析服务暂不可用，请稍后重试");
        }
    }
}

package com.george.apipassenger.remote;

import com.george.internalCommon.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient("service-map")
public interface ServiceMapClient {

    @GetMapping("/geocode")
    ResponseResult<Map<String, String>> geocode(@RequestParam("address") String address);
}

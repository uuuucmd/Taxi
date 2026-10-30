package com.flytaxi.apidriver.remote;


import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.PointRequest;
import com.flytaxi.internalCommon.request.ApiDriverPointRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * This class is ued to connect with the Amap service
 */
@FeignClient("service-map")
public interface ServiceMapClient {

    // This class is used to upload the tracks of a terminal
    @RequestMapping(method = RequestMethod.POST, value = "/point/upload")
    ResponseResult upload(@RequestBody PointRequest pointRequest);

    @RequestMapping(method = RequestMethod.POST, value = "/local-locations/upload")
    ResponseResult uploadLocal(@RequestBody ApiDriverPointRequest request);

    @RequestMapping(method = RequestMethod.GET, value = "/geocode")
    ResponseResult<Map<String, String>> geocode(@RequestParam String address);

}

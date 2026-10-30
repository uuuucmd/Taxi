package com.flytaxi.apipassenger.remote;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.DataResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Date: 2024-10-16-12:48
 * @Description: com.flytaxi.apipassenger.remote
 */

/**
 * Class use to build connection with service function using openFeign
 */
@FeignClient("service-verificationCode")
public interface serviceVerificationCodeClient {

    @RequestMapping(method = RequestMethod.GET, value = "/numberCode/{size}")
    ResponseResult<DataResponse> getResponse(@PathVariable int size);
}

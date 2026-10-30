package com.flytaxi.apipassenger.remote;

import com.flytaxi.internalCommon.dto.PassengerUser;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.VerificationCodeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Date: 2024-10-18-14:34
 * @Description: This class is used to connect with the remote server
 */

@FeignClient("service-passenger-user")
public interface ServicePassengerUserClient {

    @RequestMapping(method = RequestMethod.POST, value = "/user")
    ResponseResult loginOrRegister(@RequestBody VerificationCodeDTO verificationCodeDTO);


    @RequestMapping(method = RequestMethod.GET, value = "/get-user/{phone}")
    ResponseResult<PassengerUser> getUser(@PathVariable("phone") String phoneNum);
}

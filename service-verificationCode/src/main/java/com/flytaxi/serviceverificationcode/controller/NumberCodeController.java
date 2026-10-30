package com.flytaxi.serviceverificationcode.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.DataResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2024-10-13-13:45
 * @Description: com.flytaxi.servicevirificationcode.controller
 */
@RestController
public class NumberCodeController {

    @GetMapping("/numberCode/{size}")
    public ResponseResult<DataResponse> getResponse(@PathVariable("size") int size) {

        // generate random verification code
        int power = size - 1;
        // generate a double from 1 to 9
        double random = (Math.random()*9 + 1);
        // extract the first size number and set them as verification code
        int code = (int)(Math.pow(10, size - 1) * random);


        // define a return value
        DataResponse response = new DataResponse();
        response.setNumberCode(code);

        return ResponseResult.success(response);
    }


}

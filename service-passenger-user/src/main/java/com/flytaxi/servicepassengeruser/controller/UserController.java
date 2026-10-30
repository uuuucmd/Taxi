package com.flytaxi.servicepassengeruser.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.VerificationCodeDTO;
import com.flytaxi.servicepassengeruser.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Date: 2024-10-17-16:47
 * @Description: This class is used to check the existences of user or
 *               promote them to register for new accounts.
 */
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/user")
    public ResponseResult loginOrRegister(@RequestBody VerificationCodeDTO codeDTO) {
        String phoneNum = codeDTO.getPassengerPhone();
        return userService.checkOrRegister(phoneNum);
    }


    @GetMapping("/get-user/{phone}")
    public ResponseResult getUser(@PathVariable("phone") String phoneNum) {
        return userService.getUserByPhone(phoneNum);
    }
}



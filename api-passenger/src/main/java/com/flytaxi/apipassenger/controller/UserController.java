package com.flytaxi.apipassenger.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.apipassenger.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @Date: 2024-10-22-17:01
 * @Description: This class is used to return the information of passenger users
 */
@RestController
public class UserController {
    @Autowired
    private UserService service;

    /**
     * This function is to return the user info in response according to the access token
     * @param request
     * @return
     */
    @GetMapping("/users")
    public ResponseResult getUser(HttpServletRequest request) {
        // get accessToken from request
        String accessToken = request.getHeader("Authorization");

        return service.getUserInfo(accessToken);
    }
}

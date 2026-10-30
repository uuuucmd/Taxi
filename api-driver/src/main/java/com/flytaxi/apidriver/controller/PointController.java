package com.flytaxi.apidriver.controller;


import com.flytaxi.apidriver.service.PointService;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.TokenResult;
import com.flytaxi.internalCommon.util.JwtUtils;
import com.flytaxi.internalCommon.request.ApiDriverPointRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * This class is used to upload the traces of a track of a terminal in Amap
 */

@RestController
@RequestMapping("/point")
public class PointController {

    @Autowired
    PointService pointService;

    @PostMapping("/upload")
    public ResponseResult upload(@RequestBody ApiDriverPointRequest apiDriverPointRequest, HttpServletRequest request){
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.DRIVER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(0, "请先登录司机账号");
        }
        return pointService.upload(apiDriverPointRequest, token.getPhone());
    }
}

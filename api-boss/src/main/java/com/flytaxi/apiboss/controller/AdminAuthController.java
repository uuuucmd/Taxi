package com.flytaxi.apiboss.controller;

import com.flytaxi.apiboss.request.AdminLoginRequest;
import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.constant.TokenConstant;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.TokenResponse;
import com.flytaxi.internalCommon.util.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/admin-auth")
public class AdminAuthController {

    @Value("${boss.admin.username:admin}")
    private String adminUsername;

    @Value("${boss.admin.password:admin123}")
    private String adminPassword;

    @PostMapping("/login")
    public ResponseResult login(@RequestBody AdminLoginRequest request) {
        if (request == null || !matches(adminUsername, request.getUsername())
                || !matches(adminPassword, request.getPassword())) {
            return ResponseResult.fail(CommonStatus.VERIFICATION_ERROR.getCode(), "管理员账号或密码错误");
        }

        String identity = UserIdentity.ADMIN.getIdentity();
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(JwtUtils.generateToken(request.getUsername().trim(), identity,
                TokenConstant.ACCESS_TOKEN_TYPE));
        tokenResponse.setRefreshToken(JwtUtils.generateToken(request.getUsername().trim(), identity,
                TokenConstant.REFRESH_TOKEN_TYPE));
        return ResponseResult.success(tokenResponse);
    }

    private boolean matches(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

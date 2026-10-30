package com.flytaxi.apipassenger.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.TokenResponse;
import com.flytaxi.apipassenger.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2024-10-21-15:54
 * @Description: com.flytaxi.apipassenger.controller
 */
@RestController
public class TokenController {

    @Autowired
    private TokenService service;

    @PostMapping("/token-refresh")
    public ResponseResult refreshToken (@RequestBody TokenResponse tokenResponse) {

        String inputToken = tokenResponse.getRefreshToken();


        return service.refreshToken(inputToken);
    }
}

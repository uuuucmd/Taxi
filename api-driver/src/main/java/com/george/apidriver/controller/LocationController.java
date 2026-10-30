package com.george.apidriver.controller;

import com.george.apidriver.remote.ServiceMapClient;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class LocationController {
    @Autowired
    private ServiceMapClient maps;

    @GetMapping("/locations/geocode")
    public ResponseResult<Map<String, String>> geocode(@RequestParam String address, HttpServletRequest request) {
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.DRIVER.getIdentity().equals(token.getIdentity())) {
            return ResponseResult.fail(0, "请先登录司机账号");
        }
        if (address == null || address.isBlank() || address.length() > 200 || address.contains("|")) {
            return ResponseResult.fail(0, "请输入有效的详细地址");
        }
        try {
            ResponseResult<Map<String, String>> result = maps.geocode(address.trim());
            return result == null ? ResponseResult.fail(1700, "地址解析服务暂不可用，请稍后重试") : result;
        } catch (RuntimeException exception) {
            return ResponseResult.fail(1700, "地址解析服务暂不可用，请稍后重试");
        }
    }
}

package com.flytaxi.apipassenger.controller;


import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.OrderInfo;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.dto.TokenResult;
import com.flytaxi.internalCommon.request.OrderRequest;
import com.flytaxi.internalCommon.util.JwtUtils;
import com.flytaxi.apipassenger.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;


/**
 * This class is used to manipulate orders from the passenger side
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    OrderService orderService;
    /**
     * This method is used to create order/place order from the passenger side
     * @return
     */
    @PostMapping("/add")
    public ResponseResult add(@RequestBody OrderRequest orderRequest, HttpServletRequest request){
        log.debug("Passenger order add request received");
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.PASSENGER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(0, "请先登录乘客账号");
        }
        return orderService.add(orderRequest, token.getPhone());
    }

    /**
     * This method is used to create cancle order from the passenger side
     * @param orderId
     * @return
     */
    @PostMapping("/cancel")
    public ResponseResult cancel(@RequestParam Long orderId, HttpServletRequest request){
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.PASSENGER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(CommonStatus.TOKEN_ERROR.getCode(), CommonStatus.TOKEN_ERROR.getMessage());
        }
        return orderService.cancel(orderId, request.getHeader("Authorization"));
    }

    @GetMapping("/current")
    public ResponseResult current(HttpServletRequest request){
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.PASSENGER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(0, "请先登录乘客账号");
        }
        return orderService.current(token.getPhone());
    }

    @PostMapping("/pay")
    public ResponseResult pay(@RequestBody OrderRequest orderRequest, HttpServletRequest request){
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.PASSENGER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(0, "请先登录乘客账号");
        }
        return orderService.pay(orderRequest, request.getHeader("Authorization"));
    }

}

package com.flytaxi.apidriver.controller;


import com.flytaxi.apidriver.service.ApiDriverOrderInfoService;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.dto.TokenResult;
import com.flytaxi.internalCommon.request.OrderRequest;
import com.flytaxi.internalCommon.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.function.Function;

/**
 * This class is used to connect to the order system and change its status
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    ApiDriverOrderInfoService apiDriverOrderInfoService;

    @GetMapping("/list")
    public ResponseResult list(HttpServletRequest request) {
        return withDriver(request, apiDriverOrderInfoService::driverOrders);
    }

    @PostMapping("/accept")
    public ResponseResult accept(@RequestParam Long orderId, HttpServletRequest request) {
        return withDriver(request, phone -> apiDriverOrderInfoService.acceptOrder(orderId, phone));
    }

    private ResponseResult withDriver(HttpServletRequest request, Function<String, ResponseResult> action) {
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.DRIVER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(0, "请先登录司机账号");
        }
        return action.apply(token.getPhone());
    }

    private ResponseResult advance(OrderRequest orderRequest, HttpServletRequest request,
                                   Function<OrderRequest, ResponseResult> action) {
        return withDriver(request, phone -> apiDriverOrderInfoService.executeForDriver(
                orderRequest.getOrderId(), phone, order -> action.apply(orderRequest)));
    }

    /**
     * This function is to change the order status to "to-pick-up-passenger"
     * @param orderRequest
     * @return
     */
    @PostMapping("/to-pick-up-passenger")
    public ResponseResult changeStatus(@RequestBody OrderRequest orderRequest, HttpServletRequest request){
        return advance(orderRequest, request, apiDriverOrderInfoService::toPickUpPassenger);
    }

    /**
     * This function is to change the order status to "arrived-departure"
     * @param orderRequest
     * @return
     */
    @PostMapping("/arrived-departure")
    public ResponseResult arrivedDeparture(@RequestBody OrderRequest orderRequest, HttpServletRequest request){
        return advance(orderRequest, request, apiDriverOrderInfoService::arrivedDeparture);
    }

    /**
     * This function is to change the order status to "pick-up-passenger"
     * @param orderRequest
     * @return
     */
    @PostMapping("/pick-up-passenger")
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest, HttpServletRequest request){
        return advance(orderRequest, request, apiDriverOrderInfoService::pickUpPassenger);
    }

    /**
     * This function is to change the order status to "passenger-getoff"
     * @param orderRequest
     * @return
     */
    @PostMapping("/passenger-getoff")
    public ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest, HttpServletRequest request){
        return advance(orderRequest, request, apiDriverOrderInfoService::passengerGetoff);
    }

    /**
     * This function is to change the order status to "cancelled"
     * @param orderId
     * @return
     */
    @PostMapping("/cancel")
    public ResponseResult cancel(@RequestParam Long orderId, HttpServletRequest request){
        return withDriver(request, phone -> apiDriverOrderInfoService.executeForDriver(
                orderId, phone, order -> apiDriverOrderInfoService.cancel(order.getId())));
    }


}

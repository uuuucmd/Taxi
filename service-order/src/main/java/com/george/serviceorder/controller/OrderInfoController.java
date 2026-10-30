package com.george.serviceorder.controller;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.internalCommon.util.JwtUtils;
import com.george.serviceorder.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * This class is used to provide an external interface and to handle any order-related operations
 * @author George Sun
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {

    @Autowired
    OrderInfoService orderInfoService;

    /**
     * This function is to create a new order
     * @param orderRequest
     * @return
     */
    @PostMapping("/add")
    public ResponseResult add(@RequestBody OrderRequest orderRequest , HttpServletRequest httpServletRequest){
        return orderInfoService.add(orderRequest);
    }



    /**
     * This function is to deal with the situation where the driver initiates to pick up the passenger
     * @param orderRequest
     * @return
     */
    @PostMapping("/to-pick-up-passenger")
    public ResponseResult changeStatus(@RequestBody OrderRequest orderRequest){

        return orderInfoService.toPickUpPassenger(orderRequest);
    }

    /**
     * This function is to deal with the situation where the driver arrives the departure
     * @param orderRequest
     * @return
     */
    @PostMapping("/arrived-departure")
    public ResponseResult arrivedDeparture(@RequestBody OrderRequest orderRequest){
        return orderInfoService.arrivedDeparture(orderRequest);
    }

    /**
     * This function is to deal with the situation where the driver picks up the passenger
     * @param orderRequest
     * @return
     */
    @PostMapping("/pick-up-passenger")
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        return orderInfoService.pickUpPassenger(orderRequest);
    }

    /**
     * This function is to deal with the situation where the passenger gets off the vehicle
     * @param orderRequest
     * @return
     */
    @PostMapping("/passenger-getoff")
    public ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest){
        return orderInfoService.passengerGetoff(orderRequest);
    }

    /**
     * This function is to push the payment when the order ends
     * @param orderRequest
     * @return
     */
    @PostMapping("/push-pay-info")
    public ResponseResult pushPayInfo(@RequestBody OrderRequest orderRequest){
        return orderInfoService.pushPayInfo(orderRequest);
    }

    /**
     * This function is to deal with the payment
     * @param orderRequest
     * @param authorization signed passenger token forwarded by api-passenger
     * @return
     */
    @PostMapping("/pay")
    public ResponseResult pay(@RequestBody OrderRequest orderRequest,
                              @RequestHeader(value = "Authorization", required = false) String authorization){
        TokenResult token = passengerToken(authorization);
        if (token == null) {
            return ResponseResult.fail(CommonStatus.TOKEN_ERROR.getCode(), CommonStatus.TOKEN_ERROR.getMessage());
        }
        if (orderRequest == null || orderRequest.getOrderId() == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        return orderInfoService.pay(orderRequest.getOrderId(), token.getPhone());
    }

    /**
     * This function is to cancel the order
     * @param orderId
     * @param identity
     * @param authorization signed passenger token for passenger cancellation
     * @return
     */
    @PostMapping("/cancel")
    public ResponseResult cancel(@RequestParam("orderId") Long orderId,
                                 @RequestParam("identity") String identity,
                                 @RequestHeader(value = "Authorization", required = false) String authorization){
        if (UserIdentity.PASSENGER.getIdentity().equals(identity)) {
            TokenResult token = passengerToken(authorization);
            if (token == null) {
                return ResponseResult.fail(CommonStatus.TOKEN_ERROR.getCode(), CommonStatus.TOKEN_ERROR.getMessage());
            }
            return orderInfoService.cancel(orderId, identity, token.getPhone());
        }
        return orderInfoService.cancel(orderId, identity);
    }

    private TokenResult passengerToken(String authorization) {
        TokenResult token = JwtUtils.checkToken(authorization);
        if (token == null || !UserIdentity.PASSENGER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return null;
        }
        return token;
    }

    /**
     * This function is to query the current order information,
     * according to the user identity and mobile phone number
     * @param phone
     * @param identity
     * @return
     */
    @GetMapping("/current")
    public ResponseResult current(String phone , String identity){
        return orderInfoService.current(phone , identity);
    }

    @GetMapping("/driver-orders")
    public ResponseResult<List<OrderInfo>> driverOrders(@RequestParam("driverPhone") String driverPhone) {
        return orderInfoService.driverOrders(driverPhone);
    }

    @PostMapping("/accept")
    public ResponseResult accept(@RequestParam Long orderId, @RequestParam String driverPhone) {
        return orderInfoService.acceptOrder(orderId, driverPhone);
    }

    @GetMapping("/driver-order")
    public ResponseResult<OrderInfo> driverOrder(@RequestParam("driverPhone") String driverPhone,
                                               @RequestParam("orderId") Long orderId) {
        return orderInfoService.driverOrder(driverPhone, orderId);
    }
}

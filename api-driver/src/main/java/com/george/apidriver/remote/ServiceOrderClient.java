package com.george.apidriver.remote;


import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// This class is used to connect to the order system
@FeignClient("service-order")
public interface ServiceOrderClient {

    @GetMapping("/order/driver-orders")
    ResponseResult<List<OrderInfo>> driverOrders(@RequestParam("driverPhone") String driverPhone);

    @PostMapping("/order/accept")
    ResponseResult acceptOrder(@RequestParam("orderId") Long orderId,
                               @RequestParam("driverPhone") String driverPhone);

    @GetMapping("/order/driver-order")
    ResponseResult<OrderInfo> driverOrder(@RequestParam("driverPhone") String driverPhone,
                                        @RequestParam("orderId") Long orderId);

    @RequestMapping(method = RequestMethod.POST, value = "/order/to-pick-up-passenger")
    ResponseResult toPickUpPassenger(@RequestBody OrderRequest orderRequest);

    @RequestMapping(method = RequestMethod.POST, value = "/order/arrived-departure")
    ResponseResult arrivedDeparture(@RequestBody OrderRequest orderRequest);

    @RequestMapping(method = RequestMethod.POST, value = "/order/pick-up-passenger")
    ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest);

    @RequestMapping(method = RequestMethod.POST,value ="/order/passenger-getoff")
    ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest);

    @RequestMapping(method = RequestMethod.POST, value = "/order/cancel")
    ResponseResult cancel(@RequestParam Long orderId, @RequestParam String identity);

    @PostMapping("/order/push-pay-info")
    ResponseResult pushPayInfo(@RequestBody OrderRequest orderRequest);

}

package com.flytaxi.apipassenger.remote;


import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.dto.OrderInfo;
import com.flytaxi.internalCommon.request.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * This class is used to connect the order system directly using openfeign, so that it can be
 * successfully invoked by the service provider
 */
@FeignClient("service-order")
public interface ServiceOrderClient {

    @RequestMapping(method = RequestMethod.POST, value = "/order/add")
    ResponseResult add(@RequestBody OrderRequest orderRequest);

    @RequestMapping(method = RequestMethod.POST, value = "/order/cancel")
    ResponseResult cancel(@RequestParam("orderId") Long orderId,
                          @RequestParam("identity") String identity,
                          @RequestHeader("Authorization") String authorization);

    @RequestMapping(method = RequestMethod.GET, value = "/order/current")
    ResponseResult<OrderInfo> current(@RequestParam String phone, @RequestParam String identity);

    @RequestMapping(method = RequestMethod.POST, value = "/order/pay")
    ResponseResult pay(@RequestBody OrderRequest orderRequest,
                       @RequestHeader("Authorization") String authorization);

}

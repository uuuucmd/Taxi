package com.flytaxi.apidriver.service;


import com.flytaxi.apidriver.remote.ServiceOrderClient;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.OrderInfo;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.OrderRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import java.util.function.Function;


/**
 * This class provides service support that allows the controller layer to change order status
 */
@Service
public class ApiDriverOrderInfoService {

    @Autowired
    ServiceOrderClient serviceOrderClient;

    public ResponseResult<List<OrderInfo>> driverOrders(String driverPhone) {
        return serviceOrderClient.driverOrders(driverPhone);
    }

    public ResponseResult<OrderInfo> orderDetail(Long orderId, String driverPhone) {
        if (orderId == null) {
            return ResponseResult.fail(0, "请先选择订单");
        }
        ResponseResult<OrderInfo> result = serviceOrderClient.driverOrder(driverPhone, orderId);
        if (result == null) {
            return ResponseResult.fail(0, "订单详情暂不可用，请稍后重试");
        }
        if (result.getCode() == 1 && result.getData() != null
                && !driverPhone.equals(result.getData().getDriverPhone())) {
            return ResponseResult.fail(0, "订单不属于当前司机");
        }
        return result;
    }

    public ResponseResult acceptOrder(Long orderId, String driverPhone) {
        if (orderId == null) return ResponseResult.fail(0, "请先选择订单");
        return serviceOrderClient.acceptOrder(orderId, driverPhone);
    }

    public ResponseResult executeForDriver(Long orderId, String driverPhone,
                                          Function<OrderInfo, ResponseResult> action) {
        if (orderId == null) {
            return ResponseResult.fail(0, "请先选择订单");
        }
        ResponseResult<OrderInfo> result = serviceOrderClient.driverOrder(driverPhone, orderId);
        if (result == null || result.getCode() != 1 || result.getData() == null) {
            return ResponseResult.fail(0, result == null ? "未获取到订单，请刷新后重试" : result.getMessage());
        }
        OrderInfo order = result.getData();
        if (!orderId.equals(order.getId()) || !driverPhone.equals(order.getDriverPhone())) {
            return ResponseResult.fail(0, "订单不属于当前司机");
        }
        return action.apply(order);
    }

    public ResponseResult toPickUpPassenger(OrderRequest orderRequest){
        return serviceOrderClient.toPickUpPassenger(orderRequest);
    }

    public ResponseResult arrivedDeparture(OrderRequest orderRequest){
        return serviceOrderClient.arrivedDeparture(orderRequest);
    }

    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        return  serviceOrderClient.pickUpPassenger(orderRequest);
    }

    public ResponseResult passengerGetoff(OrderRequest orderRequest){
        return serviceOrderClient.passengerGetoff(orderRequest);
    }

    public ResponseResult cancel(Long orderId){
        return  serviceOrderClient.cancel(orderId, UserIdentity.DRIVER.getIdentity());
    }

}

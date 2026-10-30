package com.george.apidriver.service;


import com.george.apidriver.remote.ServiceOrderClient;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
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

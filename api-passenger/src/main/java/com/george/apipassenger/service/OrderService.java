package com.george.apipassenger.service;


import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.PassengerUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.apipassenger.remote.ServiceOrderClient;
import com.george.apipassenger.remote.ServicePassengerUserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This class is used to invoke the remote connection order system and
 * provides support for the controller layer
 */
@Service
public class OrderService {

    @Autowired
    ServiceOrderClient serviceOrderClient;

    @Autowired
    ServicePassengerUserClient servicePassengerUserClient;

    public ResponseResult add(OrderRequest orderRequest, String passengerPhone){
        ResponseResult<PassengerUser> passengerResponse = servicePassengerUserClient.getUser(passengerPhone);
        if (passengerResponse == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        if (passengerResponse.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(passengerResponse.getCode(), passengerResponse.getMessage());
        }
        PassengerUser passenger = passengerResponse.getData();
        if (passenger == null || passenger.getId() == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        orderRequest.setPassengerId(passenger.getId());
        orderRequest.setPassengerPhone(passengerPhone);
        return serviceOrderClient.add(orderRequest);
    }

    public ResponseResult cancel(Long orderId, String authorization){
        return serviceOrderClient.cancel(orderId, UserIdentity.PASSENGER.getIdentity(), authorization);
    }

    public ResponseResult current(String passengerPhone){
        return serviceOrderClient.current(passengerPhone, UserIdentity.PASSENGER.getIdentity());
    }

    public ResponseResult pay(OrderRequest orderRequest, String authorization){
        return serviceOrderClient.pay(orderRequest, authorization);
    }


}

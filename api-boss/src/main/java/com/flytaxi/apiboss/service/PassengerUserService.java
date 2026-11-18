package com.flytaxi.apiboss.service;

import com.flytaxi.apiboss.remote.ServicePassengerUserClient;
import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.constant.TokenConstant;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.util.RedisPrefixUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PassengerUserService {

    @Autowired
    private ServicePassengerUserClient client;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public ResponseResult listPassengers(long page, long pageSize, String keyword, Integer state) {
        return client.listPassengers(page, pageSize, keyword, state);
    }

    public ResponseResult updatePassengerState(String passengerPhone, Integer state) {
        ResponseResult result = client.updatePassengerState(passengerPhone, state);
        if (result != null && result.getCode() == CommonStatus.SUCCESS.getCode() && Integer.valueOf(1).equals(state)) {
            String identity = UserIdentity.PASSENGER.getIdentity();
            String accessTokenKey = RedisPrefixUtils.generateTokenKey(
                    passengerPhone, identity, TokenConstant.ACCESS_TOKEN_TYPE);
            String refreshTokenKey = RedisPrefixUtils.generateTokenKey(
                    passengerPhone, identity, TokenConstant.REFRESH_TOKEN_TYPE);
            try {
                redisTemplate.delete(accessTokenKey);
                redisTemplate.delete(refreshTokenKey);
            } catch (Exception exception) {
                log.warn("Passenger account was disabled but its tokens could not be revoked", exception);
            }
        }
        return result;
    }
}

package com.flytaxi.apipassenger.service;

import com.alibaba.nacos.common.utils.StringUtils;
import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.constant.TokenConstant;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.VerificationCodeDTO;
import com.flytaxi.internalCommon.response.TokenResponse;
import com.flytaxi.internalCommon.util.JwtUtils;
import com.flytaxi.internalCommon.util.RedisPrefixUtils;
import com.flytaxi.internalCommon.util.VerificationCodeUtils;
import com.flytaxi.apipassenger.remote.ServicePassengerUserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.util.concurrent.TimeUnit;

/**
 * @Date: 2024-10-12-19:17
 * @Description: This class is used to generate or check the verification code that users entered
 */
@Service
public class VerificationCodeService {

    @Autowired
    private ServicePassengerUserClient userClient;


    // This variable is used to store String object in Redis
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${verification-code.dev-mode:false}")
    private boolean devMode;

    /**
     * This function is aimed to generate verification codes for passengers and
     * then store these codes in Redis
     * @param passengerPhone - The phone number of passengers
     * @return - a container of response messages
     */
    public ResponseResult generateCode (String passengerPhone) {
        // Obtain a verification code
        int codeNumber = VerificationCodeUtils.generate(6);

        String key = RedisPrefixUtils.generateKey(passengerPhone, UserIdentity.PASSENGER.getIdentity());

        // Store verification code in Redis
        redisTemplate.opsForValue().set(key, String.valueOf(codeNumber), 2, TimeUnit.MINUTES);

        if (devMode) {
            return ResponseResult.success(String.valueOf(codeNumber));
        }

        // Through the SMS service provider, send the verification code to the mobile phone.

        return ResponseResult.success("");
    }



    /**
     * This function is designed to check the correctness of verification codes sent from passenger's phone.
     * @param passengerPhone - The phone number of passengers
     * @param verificationCode - The verification codes sent by the passengers
     * @return - a container of response messages
     */
    public ResponseResult checkCode(String passengerPhone, String verificationCode) {
        // generate the key for checking
        String key = RedisPrefixUtils.generateKey(passengerPhone, UserIdentity.PASSENGER.getIdentity());

        // attempt to extract value from Redis
        String redisValue = redisTemplate.opsForValue().get(key);

        // according to the phone number. check the correctness of vc
        if (StringUtils.isBlank(redisValue) || !redisValue.trim().equals(verificationCode.trim())) {
            return ResponseResult.fail(CommonStatus.VERIFICATION_ERROR.getCode(), CommonStatus.VERIFICATION_ERROR.getMessage());
        }

        // once the verification code matches, check the existence of users using service function
        VerificationCodeDTO verificationCodeDTO = new VerificationCodeDTO();
        verificationCodeDTO.setPassengerPhone(passengerPhone);
        userClient.loginOrRegister(verificationCodeDTO);

        // create a token for the access of users
        String accessToken = JwtUtils.generateToken(passengerPhone, UserIdentity.PASSENGER.getIdentity(),
                TokenConstant.ACCESS_TOKEN_TYPE);
        // create a token to reassure the identity of users once the accessToken expires
        String refreshToken =JwtUtils.generateToken(passengerPhone, UserIdentity.PASSENGER.getIdentity(),
                TokenConstant.REFRESH_TOKEN_TYPE);

        // store token in Redis
        String accessTokenKey = RedisPrefixUtils.generateTokenKey(passengerPhone,
                UserIdentity.PASSENGER.getIdentity(), TokenConstant.ACCESS_TOKEN_TYPE);
        // store it for up to 30 days
        redisTemplate.opsForValue().set(accessTokenKey, accessToken, 30, TimeUnit.DAYS);

        String refreshTokenKey = RedisPrefixUtils.generateTokenKey(passengerPhone,
                UserIdentity.PASSENGER.getIdentity(), TokenConstant.REFRESH_TOKEN_TYPE);
        // store it for up to 32 days,
        // a little longer than access token to avoid the repeating enter of phone number and VC
        redisTemplate.opsForValue().set(refreshTokenKey, refreshToken, 32, TimeUnit.DAYS);


        // return the token to passenger
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshToken);
        return ResponseResult.success(tokenResponse);
    }
}

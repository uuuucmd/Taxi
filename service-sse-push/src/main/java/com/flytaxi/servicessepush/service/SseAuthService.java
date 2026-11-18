package com.flytaxi.servicessepush.service;

import com.flytaxi.internalCommon.constant.TokenConstant;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.PassengerUser;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.dto.TokenResult;
import com.flytaxi.internalCommon.response.DriverUserExistsResponse;
import com.flytaxi.internalCommon.util.JwtUtils;
import com.flytaxi.internalCommon.util.RedisPrefixUtils;
import com.flytaxi.servicessepush.remote.DriverUserClient;
import com.flytaxi.servicessepush.remote.PassengerUserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the authenticated user behind an SSE connection.
 *
 * EventSource cannot set an Authorization header, so the browser passes the access token as a
 * query parameter. The token is validated against JWT and the Redis token store, and the user id is
 * looked up from the user service instead of trusting a client supplied id.
 */
@Service
@RequiredArgsConstructor
public class SseAuthService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PassengerUserClient passengerUserClient;
    private final DriverUserClient driverUserClient;

    public AuthenticatedUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("missing token");
        }

        TokenResult tokenResult = JwtUtils.checkToken(token);
        if (tokenResult == null || tokenResult.getPhone() == null || tokenResult.getIdentity() == null) {
            throw unauthorized("invalid token");
        }

        String phone = tokenResult.getPhone();
        String identity = tokenResult.getIdentity();
        String tokenKey = RedisPrefixUtils.generateTokenKey(phone, identity, TokenConstant.ACCESS_TOKEN_TYPE);
        String storedToken = stringRedisTemplate.opsForValue().get(tokenKey);
        if (storedToken == null || !storedToken.trim().equals(token.trim())) {
            throw unauthorized("expired token");
        }

        Long userId = resolveUserId(phone, identity);
        if (userId == null) {
            throw unauthorized("unknown user");
        }
        return new AuthenticatedUser(userId, identity);
    }

    private Long resolveUserId(String phone, String identity) {
        try {
            if (UserIdentity.PASSENGER.getIdentity().equals(identity)) {
                ResponseResult<PassengerUser> result = passengerUserClient.getUser(phone);
                if (result != null && result.getCode() == 1 && result.getData() != null) {
                    return result.getData().getId();
                }
                return null;
            }
            if (UserIdentity.DRIVER.getIdentity().equals(identity)) {
                ResponseResult<DriverUserExistsResponse> result = driverUserClient.getUser(phone);
                if (result != null && result.getCode() == 1 && result.getData() != null) {
                    return result.getData().getDriverId();
                }
                return null;
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "user service unavailable", exception);
        }
        return null;
    }

    private ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }

    public record AuthenticatedUser(Long userId, String identity) {
    }
}

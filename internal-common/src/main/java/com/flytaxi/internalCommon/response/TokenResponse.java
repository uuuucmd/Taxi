package com.flytaxi.internalCommon.response;

import lombok.Data;

/**
 * @Date: 2024-10-16-22:42
 * @Description: This class contains two types of tokens
 */
@Data
public class TokenResponse {

    private String accessToken;

    private String refreshToken;
}

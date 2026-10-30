package com.flytaxi.internalCommon.dto;

import lombok.Data;

/**
 * @Date: 2024-10-18-20:01
 * @Description: This class contains the info that can be extracted from token
 */
@Data
public class TokenResult {
    private String phone;
    private String identity;
}

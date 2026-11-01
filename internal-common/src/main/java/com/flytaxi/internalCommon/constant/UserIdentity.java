package com.flytaxi.internalCommon.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Date: 2024-10-18-20:45
 * @Description: This enumeration is used to represent the identity of different users
 */
@AllArgsConstructor
public enum UserIdentity {
    PASSENGER("1"),
    DRIVER("2"),
    ADMIN("3");

    @Getter
    private String identity;
}

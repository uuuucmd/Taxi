package com.flytaxi.internalCommon.dto;

import lombok.Data;

/**
 * @Date: 2024-10-27-18:01
 * @Description: This class stores all the Chinese addresses
 */
@Data
public class DicDistrict {
    private String addressCode;
    private String addressName;
    private String parentAddressCode;
    private Integer level;
}

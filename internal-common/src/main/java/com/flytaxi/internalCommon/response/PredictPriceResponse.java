package com.flytaxi.internalCommon.response;

import lombok.Data;

/**
 * @Date: 2024-10-23-20:20
 * @Description: This class stores the price of a trip
 */
@Data
public class PredictPriceResponse {
    private double price;
    private String cityCode;
    private String vehicleType;
    private Integer fareVersion;
    private String fareType;

}

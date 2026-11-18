package com.flytaxi.internalCommon.response;

import lombok.Data;

/**
 * @Date: 2024-11-03-19:57
 * @Description: This class stores the information about the existence of a driver
 */
@Data
public class DriverUserExistsResponse {

    // the phone number of the driver
    private String driverPhone;

    // the id of the driver
    private Long driverId;

    // whether the driver exists in the database
    private int ifExists;
}

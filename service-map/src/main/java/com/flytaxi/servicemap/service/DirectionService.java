package com.flytaxi.servicemap.service;

import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.DirectionResponse;
import com.flytaxi.servicemap.remote.MapDirectionException;
import com.flytaxi.servicemap.remote.MapServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Date: 2024-10-24-18:13
 * @Description: com.flytaxi.servicemap.service
 */
@Service
@Slf4j
public class DirectionService {

    @Autowired
    MapServiceClient mapServiceClient;

    /**
     * This function is used to obtain the distance and journey duration,
     * according to the longitude and latitude of destination and origin.
     * @param depLatitude
     * @param depLongitude
     * @param destLatitude
     * @param destLongitude
     * @return
     */
    public ResponseResult driving(String depLatitude, String depLongitude,
                                  String destLatitude, String destLongitude) {
        // connect with map interface (lbs.amap.com)

        DirectionResponse directionResponse;
        try {
            directionResponse = mapServiceClient.direction(depLatitude, depLongitude,
                                                                            destLatitude, destLongitude);
        } catch (MapDirectionException e) {
            String causeType = e.getCause() == null
                    ? e.getClass().getSimpleName()
                    : e.getCause().getClass().getSimpleName();
            log.warn("Map direction request failed; causeType={}", causeType);
            return ResponseResult.fail(CommonStatus.MAP_DIRECTION_ERROR.getCode(),
                    CommonStatus.MAP_DIRECTION_ERROR.getMessage());
        }
        if (directionResponse == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }

        return ResponseResult.success(directionResponse);
    }
}

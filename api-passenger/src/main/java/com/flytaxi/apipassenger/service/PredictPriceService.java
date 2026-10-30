package com.flytaxi.apipassenger.service;

import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.PredictPriceDTO;
import com.flytaxi.internalCommon.response.PredictPriceResponse;
import com.flytaxi.apipassenger.remote.ServicePriceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Date: 2024-10-23-20:14
 * @Description: com.flytaxi.apipassenger.service
 */
@Service
@Slf4j
public class PredictPriceService {

    @Autowired
    ServicePriceClient client;

    /**
     * This function is used to predict the price of a journey according to the latitude and l
     * ongitude of origin and destination.
     * @param depLatitude - the latitude of origin
     * @param depLongitude - the longitude of origin
     * @param destLatitude - the latitude of destination
     * @param destLongitude - the longitude of destination
     * @return
     */
    public ResponseResult predictPrice(String depLatitude, String depLongitude,
                                       String destLatitude, String destLongitude,
                                       String cityCode, String vehicleType) {

        PredictPriceDTO priceDTO = new PredictPriceDTO();
        priceDTO.setDepLongitude(depLongitude);
        priceDTO.setDepLatitude(depLatitude);
        priceDTO.setDestLongitude(destLongitude);
        priceDTO.setDestLatitude(destLatitude);
        priceDTO.setCityCode(cityCode);
        priceDTO.setVehicleType(vehicleType);
        ResponseResult result = client.predictPrice(priceDTO);

        if (result == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        if (result.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }

        return result;
    }
}

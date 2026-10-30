package com.flytaxi.serviceprice.remote;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.PredictPriceDTO;
import com.flytaxi.internalCommon.response.DirectionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Date: 2024-10-25-16:31
 * @Description: The interface used to connect with map service
 */
@FeignClient("service-map")
public interface ServiceMapClient {

    @RequestMapping(method = RequestMethod.GET, value = "/direction/driving")
    ResponseResult<DirectionResponse> direction(@RequestBody PredictPriceDTO priceDTO);
}

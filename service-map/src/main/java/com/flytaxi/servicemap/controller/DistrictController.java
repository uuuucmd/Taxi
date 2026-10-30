package com.flytaxi.servicemap.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.servicemap.service.DictDistrictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2024-10-28-13:31
 * @Description: com.flytaxi.servicemap.controller
 */
@RestController
public class DistrictController {

    @Autowired
    private DictDistrictService service;

    @GetMapping("/dic-district")
    public ResponseResult initDicDistrict(String keywords) {
        return service.initDicDistrict(keywords);
    }
}

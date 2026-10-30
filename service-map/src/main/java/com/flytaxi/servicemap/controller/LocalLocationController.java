package com.flytaxi.servicemap.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.ApiDriverPointRequest;
import com.flytaxi.internalCommon.request.PredictPriceDTO;
import com.flytaxi.internalCommon.response.TerminalResponse;
import com.flytaxi.internalCommon.response.TrsearchResponse;
import com.flytaxi.servicemap.service.LocalLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/local-locations")
public class LocalLocationController {
    @Autowired
    private LocalLocationService locations;

    @PostMapping("/upload")
    public ResponseResult upload(@RequestBody ApiDriverPointRequest request) {
        return locations.upload(request);
    }

    @PostMapping("/around")
    public ResponseResult<List<TerminalResponse>> around(@RequestParam String center, @RequestParam Integer radius) {
        return locations.around(center, radius);
    }

    @PostMapping("/trip")
    public ResponseResult<TrsearchResponse> trip(@RequestBody PredictPriceDTO route) {
        return locations.trip(route);
    }
}

package com.george.servicemap.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.ApiDriverPointRequest;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrsearchResponse;
import com.george.servicemap.service.LocalLocationService;
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

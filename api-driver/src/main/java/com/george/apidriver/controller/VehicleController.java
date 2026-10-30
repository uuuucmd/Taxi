package com.george.apidriver.controller;

import com.george.apidriver.remote.ServiceDriverUserClient;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.util.JwtUtils;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/vehicle")
public class VehicleController {
    @Value("${taxi.local-simulation:true}")
    private boolean localSimulation = true;
    @Autowired
    private ServiceDriverUserClient driverClient;

    @GetMapping
    public ResponseResult get(HttpServletRequest request) {
        return owned(request, driverClient::getVehicle);
    }

    @PostMapping
    public ResponseResult register(@RequestBody Car car, HttpServletRequest request) {
        return owned(request, phone -> driverClient.registerVehicle(phone, car));
    }

    @PostMapping("/map")
    public ResponseResult initializeMap(HttpServletRequest request) {
        return owned(request, driverClient::initializeVehicleMap);
    }

    private ResponseResult owned(HttpServletRequest request, Function<String, ResponseResult<Car>> action) {
        TokenResult token = JwtUtils.checkToken(request.getHeader("Authorization"));
        if (token == null || !UserIdentity.DRIVER.getIdentity().equals(token.getIdentity())
                || token.getPhone() == null || token.getPhone().isBlank()) {
            return ResponseResult.fail(0, "请先登录司机账号");
        }
        try {
            ResponseResult<Car> result = action.apply(token.getPhone());
            if (result == null) return unavailable();
            if (result.getCode() != 1) return ResponseResult.fail(result.getCode(), result.getMessage());
            Car car = result.getData();
            if (car == null) return ResponseResult.success(null);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", car.getId() == null ? null : car.getId().toString());
            view.put("vehicleNo", car.getVehicleNo());
            view.put("address", car.getAddress());
            view.put("brand", car.getBrand());
            view.put("model", car.getModel());
            view.put("seats", car.getSeats());
            view.put("vehicleType", car.getVehicleType());
            view.put("state", car.getState());
            view.put("locationMode", localSimulation ? "local" : "amap");
            view.put("traceReady", localSimulation || (car.getTid() != null && !car.getTid().isBlank()
                    && car.getTrid() != null && !car.getTrid().isBlank()));
            return ResponseResult.success(view);
        } catch (FeignException exception) {
            return unavailable();
        }
    }

    private ResponseResult unavailable() {
        return ResponseResult.fail(1700, "车辆服务暂不可用，请稍后重试");
    }
}

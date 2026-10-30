package com.george.servicedriveruser.service;

import com.george.internalCommon.dto.Car;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrackResponse;
import com.george.servicedriveruser.mapper.CarMapper;
import com.george.servicedriveruser.remote.ServiceMapClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-22:34
 * @Description: This class provides service to add a car in database
 */
@Service
public class CarService {
    @Value("${taxi.local-simulation:true}")
    private boolean localSimulation = true;

    @Autowired
    private CarMapper mapper;

    @Autowired
    private ServiceMapClient serviceMapClient;

    public ResponseResult addCar(Car car) {
        if (car == null) {
            return ResponseResult.fail(0, "请填写车辆信息");
        }
        ResponseResult<String> cityResult = resolveCity(car.getAddress());
        if (cityResult.getCode() != CommonStatus.SUCCESS.getCode()) {
            return cityResult;
        }
        car.setAddress(cityResult.getData());
        if (car.getState() == null) {
            car.setState(0);
        } else if (car.getState() != 0 && car.getState() != 1) {
            return ResponseResult.fail(0, "车辆状态无效");
        }
        car.setGmtCreate(LocalDateTime.now());
        car.setGmtModified(LocalDateTime.now());
        // insert car into database
        mapper.insert(car);

        if (localSimulation) return ResponseResult.success("");

        // register this vehicle as a terminal in Amap before inserting it into database and get tid
        ResponseResult<TerminalResponse> responseResult = serviceMapClient.addTerminal(car.getVehicleNo(),
                car.getId()+"");
        String tid = responseResult.getData().getTid();
        car.setTid(tid);

        // create a trace and get the trid
        ResponseResult<TrackResponse> trackResponseResult = serviceMapClient.addTrack(tid);
        String trid = trackResponseResult.getData().getTrid();
        String trname = trackResponseResult.getData().getTrname();
        car.setTrid(trid);
        car.setTrname(trname);

        mapper.updateById(car);
        return ResponseResult.success("");
    }

    public ResponseResult<String> resolveCity(String address) {
        if (address == null || address.isBlank()) {
            return ResponseResult.fail(0, "请选择车辆所在城市");
        }
        String city = address.trim();
        if (!city.matches("\\d{6}")) {
            ResponseResult<Map<String, String>> result = serviceMapClient.cityCode(city);
            if (result == null || (result.getCode() == CommonStatus.SUCCESS.getCode()
                    && result.getData() == null)) {
                return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                        "车辆所在城市解析失败，请稍后重试");
            }
            if (result.getCode() != CommonStatus.SUCCESS.getCode()) {
                return ResponseResult.fail(result.getCode(), result.getMessage());
            }
            city = result.getData().get("cityCode");
        }
        boolean ordinaryCityCode = city != null && city.matches("\\d{4}00") && !city.endsWith("0000")
                && !city.matches("(?:11|12|31|50).*");
        boolean municipalityCode = city != null && city.matches("(?:11|12|31|50)0000");
        if (!ordinaryCityCode && !municipalityCode) {
            return ResponseResult.fail(0, "请输入有效的城市名称或城市码");
        }
        return ResponseResult.success(city);
    }


    public ResponseResult getCarById(Long id){
        if (id == null || id <= 0) {
            return ResponseResult.fail(0, "车辆不存在，请先绑定有效车辆");
        }
        Car car = mapper.selectById(id);
        if (car == null) {
            return ResponseResult.fail(0, "车辆不存在，请联系管理员检查车辆绑定");
        }
        return ResponseResult.success(car);

    }

}

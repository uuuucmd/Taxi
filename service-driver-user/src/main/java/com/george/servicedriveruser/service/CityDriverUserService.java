package com.george.servicedriveruser.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.DriverUserWorkStatus;
import com.george.internalCommon.response.TerminalResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.george.servicedriveruser.mapper.CarMapper;
import com.george.servicedriveruser.mapper.DriverCarBindingRelationshipMapper;
import com.george.servicedriveruser.mapper.DriverUserMapper;
import com.george.servicedriveruser.mapper.DriverUserWorkStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is to deal with drivers in a specific area
 */
@Service
public class CityDriverUserService {

    @Autowired
    DriverUserMapper driverUserMapper;

    @Autowired
    CarMapper carMapper;

    @Autowired
    DriverCarBindingRelationshipMapper bindingMapper;

    @Autowired
    DriverUserWorkStatusMapper workStatusMapper;

    /**
     * This function is to determine whether there is available driver in a specific area
     * @param cityCode
     * @return
     */
    public ResponseResult<Boolean> isAvailableDriver(String cityCode){
        int i = driverUserMapper.selectDriverUserCountByCityCode(cityCode);
        if (i > 0){
            return ResponseResult.success(true);
        }else{
            return ResponseResult.success(false);
        }
    }

    public ResponseResult<List<TerminalResponse>> availableCars(String cityCode) {
        if (cityCode == null || cityCode.isBlank()) {
            return ResponseResult.fail(0, "鍩庡競缂佃В鏃犳晥");
        }

        QueryWrapper<Car> carQuery = new QueryWrapper<>();
        carQuery.eq("address", cityCode).eq("state", 0).orderByAsc("id");
        List<Car> cars = carMapper.selectList(carQuery);
        List<TerminalResponse> candidates = new ArrayList<>();
        for (Car car : cars) {
            QueryWrapper<DriverCarBindingRelationship> bindingQuery = new QueryWrapper<>();
            bindingQuery.eq("car_id", car.getId()).eq("bind_state", 1);
            for (DriverCarBindingRelationship binding : bindingMapper.selectList(bindingQuery)) {
                DriverUser driver = driverUserMapper.selectById(binding.getDriverId());
                if (driver == null || !Integer.valueOf(1).equals(driver.getState())) continue;

                QueryWrapper<DriverUserWorkStatus> statusQuery = new QueryWrapper<>();
                statusQuery.eq("driver_id", binding.getDriverId()).eq("work_status", 1);
                if (workStatusMapper.selectCount(statusQuery) == 0) continue;

                TerminalResponse candidate = new TerminalResponse();
                candidate.setCarId(car.getId());
                candidates.add(candidate);
                break;
            }
        }
        return ResponseResult.success(candidates);
    }
}

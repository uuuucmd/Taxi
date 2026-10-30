package com.george.servicedriveruser.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.DriverCarConstant;
import com.george.internalCommon.dto.*;
import com.george.internalCommon.response.OrderDriverResponse;
import com.george.servicedriveruser.response.DriverUserPageResponse;
import com.george.servicedriveruser.mapper.CarMapper;
import com.george.servicedriveruser.mapper.DriverCarBindingRelationshipMapper;
import com.george.servicedriveruser.mapper.DriverUserMapper;
import com.george.servicedriveruser.mapper.DriverUserWorkStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: George Sun
 * @Date: 2024-10-30-21:11
 * @Description: This class provides service to deal with the driver users
 */
@Service
public class DriverUserService {
    @Autowired
    private DriverUserMapper driverUserMapper;

    @Autowired
    private DriverUserWorkStatusMapper driverUserWorkStatusMapper;

    @Autowired
    private CarMapper carMapper;

    public ResponseResult<DriverUserPageResponse> listDrivers(long page, long pageSize, String keyword, Integer state) {
        if (page < 1 || page > Integer.MAX_VALUE || pageSize < 1 || pageSize > 100
                || (state != null && state != 0 && state != 1)
                || (keyword != null && keyword.length() > 100)) {
            return ResponseResult.fail(0, "分页或筛选参数无效");
        }
        QueryWrapper<DriverUser> query = new QueryWrapper<>();
        query.select("id", "driver_name", "driver_phone", "address", "driver_gender", "state", "gmt_create");
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            query.and(criteria -> criteria.like("driver_name", term).or().like("driver_phone", term));
        }
        if (state != null) query.eq("state", state);
        query.orderByDesc("gmt_create", "id");
        Page<DriverUser> result = driverUserMapper.selectPage(new Page<>(page, pageSize), query);
        DriverUserPageResponse response = new DriverUserPageResponse();
        response.setPage(result.getCurrent());
        response.setPageSize(result.getSize());
        response.setTotal(result.getTotal());
        response.setRecords(result.getRecords().stream().map(driver -> {
            DriverUserPageResponse.DriverSummary summary = new DriverUserPageResponse.DriverSummary();
            summary.setId(driver.getId());
            summary.setDriverName(driver.getDriverName());
            summary.setDriverPhone(driver.getDriverPhone());
            summary.setAddress(driver.getAddress());
            summary.setDriverGender(driver.getDriverGender());
            summary.setState(driver.getState());
            summary.setGmtCreate(driver.getGmtCreate());
            return summary;
        }).collect(Collectors.toList()));
        return ResponseResult.success(response);
    }

    /**
     * This method is used to add a new driver to database
     * @param driverUser - the driver to be added
     * @return
     */
    public ResponseResult addDriverUser(DriverUser driverUser) {
        // add a new driver
        LocalDateTime now = LocalDateTime.now();
        driverUser.setGmtCreate(now);
        driverUser.setGmtModified(now);
        driverUserMapper.insert(driverUser);

        // initialize driver status table
        DriverUserWorkStatus driverUserWorkStatus = new DriverUserWorkStatus();
        driverUserWorkStatus.setDriverId(driverUser.getId());

        // at the beginning, after the driver just login
        driverUserWorkStatus.setWorkStatus(DriverCarConstant.DRIVER_WORK_STATUS_STOP);
        driverUserWorkStatus.setGmtModified(now);
        driverUserWorkStatus.setGmtCreate(now);
        driverUserWorkStatusMapper.insert(driverUserWorkStatus);

        return ResponseResult.success("");
    }

    /**
     * This method is used to add a new driver to database
     * @param driverUser - the driver to be added
     * @return
     */
    public ResponseResult updateDriverUser(DriverUser driverUser) {
        if (driverUser.getId() == null || driverUserMapper.selectById(driverUser.getId()) == null) {
            return ResponseResult.fail(0, "司机不存在，请刷新列表后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        driverUser.setGmtModified(now);
        driverUserMapper.updateById(driverUser);
        return ResponseResult.success("");
    }

    /**
     * This method is used to get the driver info by the given phone number
     * @param driverPhone - the given phone number
     * @return
     */
    public ResponseResult<DriverUser> getDriverUserByPhone(String driverPhone){
        Map<String,Object> map = new HashMap<>();
        map.put("driver_phone", driverPhone);

        // the target driver must be a valid one
        map.put("state", DriverCarConstant.DRIVER_STATE_VALID);
        List<DriverUser> driverUsers = driverUserMapper.selectByMap(map);

        // if the driver does not exist
        if (driverUsers.isEmpty()){
            return ResponseResult.fail(CommonStatus.DRIVER_NOT_EXISTS.getCode(),CommonStatus.DRIVER_NOT_EXISTS.getMessage());
        }
        DriverUser driverUser = driverUsers.get(0);
        return ResponseResult.success(driverUser);
    }

    @Autowired
    DriverCarBindingRelationshipMapper driverCarBindingRelationshipMapper;

    /**
     * Check available driver information based on vehicle information
     * @param carId - the id of the vehicle
     * @return
     */
    public ResponseResult<OrderDriverResponse> getAvailableDriver(Long carId){
        // Vehicle and driver binding relationship query
        QueryWrapper<DriverCarBindingRelationship> driverCarBindingRelationshipQueryWrapper = new QueryWrapper<>();
        driverCarBindingRelationshipQueryWrapper.eq("car_id",carId);
        driverCarBindingRelationshipQueryWrapper.eq("bind_state",DriverCarConstant.DRIVER_CAR_BIND);

        DriverCarBindingRelationship driverCarBindingRelationship = driverCarBindingRelationshipMapper.selectOne(driverCarBindingRelationshipQueryWrapper);
        if (driverCarBindingRelationship == null) {
            return ResponseResult.fail(CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getCode(),
                    CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getMessage());
        }
        Long driverId = driverCarBindingRelationship.getDriverId();

        // Query the driver's working status
        QueryWrapper<DriverUserWorkStatus> driverUserWorkStatusQueryWrapper = new QueryWrapper<>();
        driverUserWorkStatusQueryWrapper.eq("driver_id",driverId);
        driverUserWorkStatusQueryWrapper.eq("work_status",DriverCarConstant.DRIVER_WORK_STATUS_START);

        DriverUserWorkStatus driverUserWorkStatus = driverUserWorkStatusMapper.selectOne(driverUserWorkStatusQueryWrapper);
        if (null == driverUserWorkStatus){
            return ResponseResult.fail(CommonStatus.NO_AVAILABLE_DRIVER.getCode(),CommonStatus.NO_AVAILABLE_DRIVER.getMessage());

        }
        else {
            // Query driver information
            QueryWrapper<DriverUser> driverUserQueryWrapper = new QueryWrapper<>();
            driverUserQueryWrapper.eq("id",driverId);
            DriverUser driverUser = driverUserMapper.selectOne(driverUserQueryWrapper);
            // Query car information
            QueryWrapper<Car> carQueryWrapper = new QueryWrapper<>();
            carQueryWrapper.eq("id",carId);
            Car car = carMapper.selectOne(carQueryWrapper);

            if (driverUser == null || !Integer.valueOf(1).equals(driverUser.getState())
                    || car == null || !Integer.valueOf(0).equals(car.getState())) {
                return ResponseResult.fail(CommonStatus.NO_AVAILABLE_DRIVER.getCode(), CommonStatus.NO_AVAILABLE_DRIVER.getMessage());
            }

            // set driver and car information in an order
            OrderDriverResponse orderDriverResponse = new OrderDriverResponse();
            orderDriverResponse.setCarId(carId);
            orderDriverResponse.setDriverId(driverId);
            orderDriverResponse.setDriverPhone(driverUser.getDriverPhone());

            orderDriverResponse.setLicenseId(driverUser.getLicenseId());
            orderDriverResponse.setVehicleNo(car.getVehicleNo());
            orderDriverResponse.setVehicleType(car.getVehicleType());

            return ResponseResult.success(orderDriverResponse);
        }
    }
}

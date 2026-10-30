package com.george.apidriver.controller;

import com.george.apidriver.service.UserService;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.DriverUserWorkStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-18:24
 * @Description: This class is used to connect the driver's front-end and back-end systems
 */
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * This method is used to update driver identity information
     * @param driverUser
     * @return
     */
    @PutMapping("/user")
    public ResponseResult updateUser(@RequestBody DriverUser driverUser) {
        return userService.updateUser(driverUser);
    }

    /**
     * This method is used to change the work status of the driver
     * @param driverUserWorkStatus
     * @return
     */
    @PostMapping("/driver-user-work-status")
    public ResponseResult changeWorkStatus(@RequestBody DriverUserWorkStatus driverUserWorkStatus){

        return userService.changeWorkStatus(driverUserWorkStatus);
    }

    /**
     * Query the bonding relationship between a driver and a vehicle based on the driver token
     * @param request
     * @return
     */
    @GetMapping("/driver-car-binding-relationship")
    public ResponseResult getDriverCarBindingRelationship(HttpServletRequest request){

        String authorization = request.getHeader("Authorization");
        TokenResult tokenResult = JwtUtils.checkToken(authorization);
        if (tokenResult == null || !UserIdentity.DRIVER.getIdentity().equals(tokenResult.getIdentity())) {
            return ResponseResult.fail(0, "请先登录司机账号");
        }
        ResponseResult<DriverCarBindingRelationship> result = userService.getDriverCarBindingRelationship(tokenResult.getPhone());
        if (result == null) return ResponseResult.fail(1700, "车辆绑定服务暂不可用");
        if (result.getCode() != 1 || result.getData() == null) return result;
        DriverCarBindingRelationship binding = result.getData();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", binding.getId() == null ? null : binding.getId().toString());
        view.put("driverId", binding.getDriverId() == null ? null : binding.getDriverId().toString());
        view.put("carId", binding.getCarId() == null ? null : binding.getCarId().toString());
        view.put("bindState", binding.getBindState());
        view.put("bindingTime", binding.getBindingTime());
        return ResponseResult.success(view);

    }

    /**
     * Get the work status of a driver using the driver id
     * @param driverId
     * @return
     */
    @GetMapping("/work-status")
    public ResponseResult<DriverUserWorkStatus> getWorkStatus(Long driverId){
        return userService.getWorkStatus(driverId);
    }
}

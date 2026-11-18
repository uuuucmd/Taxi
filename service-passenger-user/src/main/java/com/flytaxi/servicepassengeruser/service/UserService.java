package com.flytaxi.servicepassengeruser.service;

import com.flytaxi.internalCommon.constant.CommonStatus;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flytaxi.internalCommon.dto.PassengerUser;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.servicepassengeruser.mapper.PassengerUserMapper;
import com.flytaxi.servicepassengeruser.response.PassengerUserPageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Date: 2024-10-17-16:59
 * @Description: This class is to check or get the user
 */
@Service
public class UserService {

    // mapper to connect with MySQL Database
    @Autowired
    private PassengerUserMapper passengerUserMapper;

    /**
     * This function is used to check the existence of user after assuring the correctness of verification code.
     * If the user does not exist in database, then create a new account for that user.
     * @param passengerPhone - the phone number of user
     * @return
     */
    public ResponseResult checkOrRegister(String passengerPhone) {
        // use MybatisPlus to connect with MySQL
        Map<String, Object> map = new HashMap<>();
        map.put("passenger_phone", passengerPhone);
        List<PassengerUser> passengerUsers = passengerUserMapper.selectByMap(map);

        // check whether current user exists in database
        if (passengerUsers.isEmpty()) {
            PassengerUser user = new PassengerUser();
            user.setPassengerName("New Passenger");
            // 0 means female; 1 means male
            user.setPassengerGender((byte) 0);
            user.setPassengerPhone(passengerPhone);
            // 0 means valid user; 1 for invalid
            user.setState((byte) 0);
            LocalDateTime now = LocalDateTime.now();
            user.setGmtCreate(now);
            user.setGmtModified(now);
            passengerUserMapper.insert(user);
        } else if (passengerUsers.get(0).getState() != 0) {
            return ResponseResult.fail(CommonStatus.USER_DISABLED.getCode(), CommonStatus.USER_DISABLED.getMessage());
        }

        return ResponseResult.success();
    }

    public ResponseResult updatePassengerState(String passengerPhone, Integer state) {
        if (passengerPhone == null || passengerPhone.isBlank() || passengerPhone.length() > 32
                || state == null || (state != 0 && state != 1)) {
            return ResponseResult.fail(0, "乘客手机号或状态无效");
        }

        UpdateWrapper<PassengerUser> update = new UpdateWrapper<>();
        update.eq("passenger_phone", passengerPhone)
                .set("state", state)
                .set("gmt_modified", LocalDateTime.now());
        if (passengerUserMapper.update(null, update) == 0) {
            return ResponseResult.fail(CommonStatus.USER_NOT_EXISTS.getCode(), CommonStatus.USER_NOT_EXISTS.getMessage());
        }
        return ResponseResult.success("");
    }

    public ResponseResult<PassengerUserPageResponse> listPassengers(
            long page, long pageSize, String keyword, Integer state) {
        if (page < 1 || page > Integer.MAX_VALUE || pageSize < 1 || pageSize > 100
                || (state != null && state != 0 && state != 1)
                || (keyword != null && keyword.length() > 100)) {
            return ResponseResult.fail(0, "分页或筛选参数无效");
        }

        QueryWrapper<PassengerUser> query = new QueryWrapper<>();
        query.select("id", "passenger_name", "passenger_phone", "passenger_gender", "state", "gmt_create");
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            query.and(criteria -> criteria.like("passenger_name", term).or().like("passenger_phone", term));
        }
        if (state != null) query.eq("state", state);
        query.orderByDesc("gmt_create", "id");

        Page<PassengerUser> result = passengerUserMapper.selectPage(new Page<>(page, pageSize), query);
        PassengerUserPageResponse response = new PassengerUserPageResponse();
        response.setPage(result.getCurrent());
        response.setPageSize(result.getSize());
        response.setTotal(result.getTotal());
        response.setRecords(result.getRecords().stream().map(passenger -> {
            PassengerUserPageResponse.PassengerSummary summary = new PassengerUserPageResponse.PassengerSummary();
            summary.setId(passenger.getId());
            summary.setPassengerName(passenger.getPassengerName());
            summary.setPassengerPhone(passenger.getPassengerPhone());
            summary.setPassengerGender((int) passenger.getPassengerGender());
            summary.setState((int) passenger.getState());
            summary.setGmtCreate(passenger.getGmtCreate());
            return summary;
        }).collect(Collectors.toList()));
        return ResponseResult.success(response);
    }

    /**
     * This method is used to return the passenger user by phone-number check
     * @param phoneNum - the phone number of specific user
     * @return
     */
    public ResponseResult getUserByPhone(String phoneNum) {
        // use MybatisPlus to connect with MySQL
        Map<String, Object> map = new HashMap<>();
        map.put("passenger_phone", phoneNum);
        List<PassengerUser> passengerUsers = passengerUserMapper.selectByMap(map);

        if (passengerUsers.isEmpty()) {
            return ResponseResult.fail(CommonStatus.USER_NOT_EXISTS.getCode(), CommonStatus.USER_NOT_EXISTS.getMessage());
        }
        PassengerUser user = passengerUsers.get(0);
        return ResponseResult.success(user);
    }
}

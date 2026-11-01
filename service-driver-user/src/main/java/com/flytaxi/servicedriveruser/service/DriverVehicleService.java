package com.flytaxi.servicedriveruser.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.dto.Car;
import com.flytaxi.internalCommon.dto.DriverCarBindingRelationship;
import com.flytaxi.internalCommon.dto.DriverUser;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.TerminalResponse;
import com.flytaxi.internalCommon.response.TrackResponse;
import com.flytaxi.servicedriveruser.mapper.CarMapper;
import com.flytaxi.servicedriveruser.mapper.DriverCarBindingRelationshipMapper;
import com.flytaxi.servicedriveruser.mapper.DriverUserMapper;
import com.flytaxi.servicedriveruser.remote.ServiceMapClient;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class DriverVehicleService {
    @Value("${taxi.local-simulation:true}")
    private boolean localSimulation = true;
    @Autowired
    private DriverUserMapper driverMapper;
    @Autowired
    private CarMapper carMapper;
    @Autowired
    private DriverCarBindingRelationshipMapper bindingMapper;
    @Autowired
    private CarService carService;
    @Autowired
    private ServiceMapClient mapClient;

    public ResponseResult<Car> getVehicle(String phone) {
        DriverUser driver = driver(phone, false);
        if (driver == null) return ResponseResult.fail(0, "司机账号不存在，请重新登录");
        DriverCarBindingRelationship binding = binding(driver.getId());
        if (binding == null) return ResponseResult.success(null);
        Car car = carMapper.selectById(binding.getCarId());
        return car == null ? ResponseResult.fail(0, "绑定车辆不存在，请联系管理员") : ResponseResult.success(car);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Car> register(String phone, Car input) {
        DriverUser driver = driver(phone, true);
        if (driver == null || !Integer.valueOf(1).equals(driver.getState())) {
            return ResponseResult.fail(0, "司机账号不存在或已停用，无法登记车辆");
        }
        if (binding(driver.getId()) != null) {
            return ResponseResult.fail(0, "当前账号已绑定车辆，更换车辆请联系管理员");
        }
        if (input == null || !text(input.getVehicleNo(), 32) || !text(input.getBrand(), 64)
                || !text(input.getModel(), 64) || !text(input.getOwnerName(), 64)) {
            return ResponseResult.fail(0, "请完整填写车牌号、品牌、车型和车主姓名");
        }
        if (input.getSeats() == null || input.getSeats() < 1 || input.getSeats() > 9
                || !Set.of("1", "2", "3").contains(input.getVehicleType() == null ? "" : input.getVehicleType())
                || !Set.of("1", "2", "3", "4", "5", "9").contains(input.getPlateColor() == null ? "1" : input.getPlateColor())
                || !Set.of("1", "2").contains(input.getVehicleColor() == null ? "1" : input.getVehicleColor())) {
            return ResponseResult.fail(0, "车辆类型、座位数或颜色无效");
        }
        String plate = normalizePlate(input.getVehicleNo());
        if (carMapper.selectCount(new QueryWrapper<Car>().eq("vehicle_no", plate)) > 0) {
            return ResponseResult.fail(0, "该车牌已登记，请联系管理员确认归属，不能重复登记或绑定他人车辆");
        }
        ResponseResult<String> city;
        try {
            city = carService.resolveCity(input.getAddress());
        } catch (FeignException exception) {
            return ResponseResult.fail(1700, "车辆所在城市解析服务暂不可用，请稍后重试");
        }
        if (city.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(city.getCode(), city.getMessage());
        }
        Car car = new Car();
        car.setAddress(city.getData());
        car.setVehicleNo(plate);
        car.setBrand(input.getBrand().trim());
        car.setModel(input.getModel().trim());
        car.setOwnerName(input.getOwnerName().trim());
        car.setSeats(input.getSeats());
        car.setVehicleType(input.getVehicleType());
        car.setPlateColor(input.getPlateColor() == null ? "1" : input.getPlateColor());
        car.setVehicleColor(input.getVehicleColor() == null ? "1" : input.getVehicleColor());
        car.setState(0);
        LocalDateTime now = LocalDateTime.now();
        car.setGmtCreate(now);
        car.setGmtModified(now);
        try {
            if (carMapper.insert(car) != 1 || car.getId() == null) {
                throw new IllegalStateException("车辆登记失败");
            }
        } catch (DuplicateKeyException exception) {
            return ResponseResult.fail(0, "该车牌已登记，请联系管理员确认归属");
        }
        DriverCarBindingRelationship relationship = new DriverCarBindingRelationship();
        relationship.setDriverId(driver.getId());
        relationship.setCarId(car.getId());
        relationship.setBindState(1);
        relationship.setBindingTime(now);
        if (bindingMapper.insert(relationship) != 1) {
            throw new IllegalStateException("车辆绑定失败");
        }
        return ResponseResult.success(car);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Car> lockVehicle(String phone, String vehicleNo) {
        DriverUser driver = driver(phone, true);
        if (driver == null || !Integer.valueOf(1).equals(driver.getState())) {
            return ResponseResult.fail(0, "司机账号不存在或已停用，无法锁定车辆");
        }
        if (binding(driver.getId()) != null) {
            return ResponseResult.fail(CommonStatus.DRIVER_BIND_EXISTS.getCode(),
                    "当前账号已锁定车辆，更换车辆请联系管理员");
        }
        if (!text(vehicleNo, 32)) {
            return ResponseResult.fail(0, "请输入要锁定的车牌号");
        }

        String plate = normalizePlate(vehicleNo);
        Car car = carMapper.selectOne(new QueryWrapper<Car>().eq("vehicle_no", plate).last("FOR UPDATE"));
        if (car == null) {
            return ResponseResult.fail(0, "未找到该车辆，请确认车牌号或联系管理员登记");
        }
        if (!Integer.valueOf(0).equals(car.getState())) {
            return ResponseResult.fail(0, "该车辆已停用，请联系管理员处理");
        }
        DriverCarBindingRelationship occupied = bindingMapper.selectOne(
                new QueryWrapper<DriverCarBindingRelationship>()
                        .eq("car_id", car.getId())
                        .eq("bind_state", 1));
        if (occupied != null) {
            return ResponseResult.fail(CommonStatus.CAR_BIND_EXISTS.getCode(),
                    "该车辆已被其他司机锁定，请联系管理员处理");
        }

        DriverCarBindingRelationship relationship = new DriverCarBindingRelationship();
        relationship.setDriverId(driver.getId());
        relationship.setCarId(car.getId());
        relationship.setBindState(1);
        relationship.setBindingTime(LocalDateTime.now());
        if (bindingMapper.insert(relationship) != 1) {
            throw new IllegalStateException("车辆锁定失败");
        }
        return ResponseResult.success(car);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Car> initializeMap(String phone) {
        DriverUser driver = driver(phone, true);
        if (driver == null || !Integer.valueOf(1).equals(driver.getState())) {
            return ResponseResult.fail(0, "司机账号不存在或已停用");
        }
        DriverCarBindingRelationship binding = binding(driver.getId());
        if (binding == null) return ResponseResult.fail(1500, "请先登记并绑定车辆");
        Car car = carMapper.selectOne(new QueryWrapper<Car>().eq("id", binding.getCarId()).last("FOR UPDATE"));
        if (car == null || !Integer.valueOf(0).equals(car.getState())) {
            return ResponseResult.fail(0, "绑定车辆不存在或已停用，请联系管理员");
        }
        try {
            if (localSimulation) return ResponseResult.success(car);
            return configureMap(car);
        } catch (FeignException exception) {
            return ResponseResult.fail(1700, "地图初始化服务暂不可用，请稍后重试");
        }
    }

    private ResponseResult<Car> configureMap(Car car) {
        if (car.getTid() != null && !car.getTid().isBlank() && car.getTrid() != null && !car.getTrid().isBlank()) {
            return ResponseResult.success(car);
        }
        if (car.getTid() == null || car.getTid().isBlank()) {
            ResponseResult<TerminalResponse> terminal = mapClient.addTerminal(car.getVehicleNo(), car.getId().toString());
            if (terminal == null) return unavailable();
            if (terminal.getCode() != CommonStatus.SUCCESS.getCode()) {
                return ResponseResult.fail(terminal.getCode(), terminal.getMessage());
            }
            if (terminal.getData() == null || terminal.getData().getTid() == null || terminal.getData().getTid().isBlank()) {
                return unavailable();
            }
            car.setTid(terminal.getData().getTid());
            car.setGmtModified(LocalDateTime.now());
            if (carMapper.updateById(car) != 1) throw new IllegalStateException("地图终端保存失败");
        }
        ResponseResult<TrackResponse> track = mapClient.addTrack(car.getTid());
        if (track == null) return unavailable();
        if (track.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(track.getCode(), track.getMessage());
        }
        if (track.getData() == null || track.getData().getTrid() == null || track.getData().getTrid().isBlank()) {
            return unavailable();
        }
        car.setTrid(track.getData().getTrid());
        car.setTrname(track.getData().getTrname());
        car.setGmtModified(LocalDateTime.now());
        if (carMapper.updateById(car) != 1) throw new IllegalStateException("地图轨迹保存失败");
        return ResponseResult.success(car);
    }

    private DriverUser driver(String phone, boolean lock) {
        if (phone == null || phone.isBlank()) return null;
        QueryWrapper<DriverUser> query = new QueryWrapper<DriverUser>().eq("driver_phone", phone);
        if (lock) query.last("FOR UPDATE");
        return driverMapper.selectOne(query);
    }

    private DriverCarBindingRelationship binding(Long driverId) {
        return bindingMapper.selectOne(new QueryWrapper<DriverCarBindingRelationship>()
                .eq("driver_id", driverId).eq("bind_state", 1));
    }

    private boolean text(String value, int limit) {
        return value != null && !value.isBlank() && value.trim().length() <= limit;
    }

    private String normalizePlate(String value) {
        return value.trim().replaceAll("\\s+", "").replace("\u00b7", "").toUpperCase(Locale.ROOT);
    }

    private ResponseResult<Car> unavailable() {
        return ResponseResult.fail(1700, "地图初始化返回无效数据，请稍后重试");
    }
}

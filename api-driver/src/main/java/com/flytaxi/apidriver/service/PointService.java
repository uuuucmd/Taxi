package com.flytaxi.apidriver.service;


import com.flytaxi.apidriver.remote.ServiceDriverUserClient;
import com.flytaxi.apidriver.remote.ServiceMapClient;
import com.flytaxi.internalCommon.dto.Car;
import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.dto.DriverCarBindingRelationship;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.ApiDriverPointRequest;
import com.flytaxi.internalCommon.request.PointRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import feign.FeignException;

/**
 * This class contains the methods to upload the tracks info
 */
@Service
public class PointService {

    @Value("${taxi.local-simulation:true}")
    private boolean localSimulation = true;

    // client used to connect with map service directly
    @Autowired
    private ServiceMapClient serviceMapClient;

    // client used to connect with a driver server
    @Autowired
    private ServiceDriverUserClient serviceDriverUserClient;

    /**
     * This method is used to upload the traces using terminal and track ids
     * @param apiDriverPointRequest - the class that contains the id of the terminal
     * @return
     */
    public ResponseResult upload(ApiDriverPointRequest request, String driverPhone){
        if (driverPhone == null || driverPhone.isBlank()) {
            return ResponseResult.fail(0, "请先登录司机账号");
        }
        if (request == null || request.getPoints() == null || request.getPoints().length == 0
                || request.getPoints().length > 100) {
            return ResponseResult.fail(0, "请提供有效的定位点，单次最多上传100个点");
        }
        try {
            ResponseResult<DriverCarBindingRelationship> bindingResult =
                    serviceDriverUserClient.getDriverCarRelationShip(driverPhone);
            if (bindingResult == null) {
                return unavailable();
            }
            if (bindingResult.getCode() != CommonStatus.SUCCESS.getCode()) {
                return ResponseResult.fail(bindingResult.getCode(), bindingResult.getMessage());
            }
            DriverCarBindingRelationship binding = bindingResult.getData();
            if (binding == null || binding.getCarId() == null || binding.getCarId() <= 0
                    || !Integer.valueOf(1).equals(binding.getBindState())) {
                return ResponseResult.fail(1500, "当前账号未绑定车辆，请联系管理员绑定车辆后再上传定位");
            }
            if (request.getCarId() != null && !binding.getCarId().equals(request.getCarId())) {
                return ResponseResult.fail(0, "只能上传当前账号绑定车辆的定位");
            }
            ResponseResult<Car> carResult = serviceDriverUserClient.getCarById(binding.getCarId());
            if (carResult == null || (carResult.getCode() == CommonStatus.SUCCESS.getCode()
                    && carResult.getData() == null)) {
                return unavailable();
            }
            if (carResult.getCode() != CommonStatus.SUCCESS.getCode()) {
                return ResponseResult.fail(carResult.getCode(), carResult.getMessage());
            }
            Car car = carResult.getData();
            if (!Integer.valueOf(0).equals(car.getState())) {
                return ResponseResult.fail(0, "车辆已停用，请联系管理员");
            }
            if (localSimulation) {
                ApiDriverPointRequest local = new ApiDriverPointRequest();
                local.setCarId(binding.getCarId());
                local.setPoints(request.getPoints());
                ResponseResult result = serviceMapClient.uploadLocal(local);
                return result == null ? unavailable() : result;
            }
            if (car.getTid() == null || car.getTid().isBlank()
                    || car.getTrid() == null || car.getTrid().isBlank()) {
                return ResponseResult.fail(0, "车辆尚未完成地图终端和轨迹配置，请联系管理员");
            }
            PointRequest pointRequest = new PointRequest();
            pointRequest.setTid(car.getTid());
            pointRequest.setTrid(car.getTrid());
            pointRequest.setPoints(request.getPoints());
            ResponseResult result = serviceMapClient.upload(pointRequest);
            return result == null ? unavailable() : result;
        } catch (FeignException exception) {
            return unavailable();
        }
    }

    private ResponseResult unavailable() {
        return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                "定位相关服务暂不可用，请稍后重试");
    }
}

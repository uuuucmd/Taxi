package com.flytaxi.servicemap.remote;

import com.flytaxi.internalCommon.constant.AmapConfigConstant;
import com.flytaxi.internalCommon.dto.PointDTO;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.PointRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Service
public class PointClient {
    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.sid}")
    private String amapSid;

    @Autowired
    private RestTemplate restTemplate;

    public ResponseResult upload(PointRequest request) {
        if (request == null || request.getTid() == null || request.getTid().isBlank()
                || request.getTrid() == null || request.getTrid().isBlank()) {
            return ResponseResult.fail(0, "车辆地图终端或轨迹未配置，请联系管理员");
        }
        if (request.getPoints() == null || request.getPoints().length == 0 || request.getPoints().length > 100) {
            return ResponseResult.fail(0, "请提供有效的定位点，单次最多上传100个点");
        }
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode points = mapper.createArrayNode();
        for (PointDTO point : request.getPoints()) {
            if (point == null || point.getLocation() == null) {
                return ResponseResult.fail(0, "请输入有效的车辆坐标");
            }
            String location = point.getLocation().trim();
            if (!location.matches("-?\\d{1,3}(?:\\.\\d{1,6})?,-?\\d{1,2}(?:\\.\\d{1,6})?")) {
                return ResponseResult.fail(0, "车辆坐标格式应为经度,纬度，最多保留6位小数");
            }
            String[] coordinates = location.split(",");
            if (new BigDecimal(coordinates[0]).abs().compareTo(BigDecimal.valueOf(180)) > 0
                    || new BigDecimal(coordinates[1]).abs().compareTo(BigDecimal.valueOf(90)) > 0) {
                return ResponseResult.fail(0, "车辆坐标超出有效范围");
            }
            long timestamp;
            try {
                String time = point.getLocatetime();
                if (time == null || time.isBlank()) {
                    return ResponseResult.fail(0, "请提供有效的定位时间");
                }
                timestamp = time.matches("\\d{13}") ? Long.parseLong(time) : Instant.parse(time).toEpochMilli();
                if (timestamp <= 0) {
                    return ResponseResult.fail(0, "定位时间无效");
                }
            } catch (DateTimeParseException | ArithmeticException exception) {
                return ResponseResult.fail(0, "定位时间应为毫秒时间戳或有效的ISO时间");
            }
            points.addObject().put("location", location).put("locatetime", timestamp);
        }
        if (amapKey == null || amapKey.isBlank() || amapSid == null || amapSid.isBlank()) {
            return ResponseResult.fail(0, "地图轨迹服务未配置，请联系管理员配置AMAP_KEY和AMAP_SID");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", amapKey);
        form.add("sid", amapSid);
        form.add("tid", request.getTid());
        form.add("trid", request.getTrid());
        form.add("points", points.toString());
        try {
            String body = restTemplate.postForEntity(AmapConfigConstant.POINT_UPLOAD, form, String.class).getBody();
            if (body == null || body.isBlank()) {
                return ResponseResult.fail(0, "地图轨迹服务返回无效数据，请稍后重试");
            }
            JsonNode result = mapper.readTree(body);
            int code = result.path("errcode").asInt(-1);
            if (code != 10000) {
                return ResponseResult.fail(0, "地图轨迹服务拒绝定位上传（错误码：" + code + "），请检查轨迹配置或稍后重试");
            }
            return ResponseResult.success(result.get("data"));
        } catch (RestClientException exception) {
            return ResponseResult.fail(0, "地图轨迹服务暂不可用，请稍后重试");
        } catch (JsonProcessingException exception) {
            return ResponseResult.fail(0, "地图轨迹服务返回无效数据，请稍后重试");
        }
    }
}

package com.george.servicemap.remote;


import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrsearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is to deal with the terminal in a service, a terminal meaning a vehicle
 */
@Service
@Slf4j
public class TerminalClient {

    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.sid}")
    private String amapSid;

    // used to build connection with Amap server
    @Autowired
    private RestTemplate restTemplate;


    /**
     * This function is to add a terminal in a service according to its name and description(usually its carID)
     * @param name - the name of the terminal
     * @param desc - the id of the vehicle
     * @return
     */
    public ResponseResult<TerminalResponse> add(String name, String desc){
        if (amapKey == null || amapKey.isBlank() || amapSid == null || amapSid.isBlank()) {
            return ResponseResult.fail(0, "地图轨迹服务未配置，请联系管理员配置AMAP_KEY和AMAP_SID");
        }
        if (name == null || name.isBlank() || desc == null || desc.isBlank()) {
            return ResponseResult.fail(0, "车辆地图终端信息不完整");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", amapKey);
        form.add("sid", amapSid);
        form.add("name", name);
        form.add("desc", desc);
        try {
            String body = restTemplate.postForEntity(AmapConfigConstant.TERMINAL_ADD, form, String.class).getBody();
            if (body == null || body.isBlank()) return downstreamResponseError();
            JsonNode result = new ObjectMapper().readTree(body);
            int code = result.path("errcode").asInt(-1);
            if (code != 10000) {
                return ResponseResult.fail(0, "地图终端创建失败（错误码：" + code + "），请检查地图配置");
            }
            String tid = result.path("data").path("tid").asText("");
            if (!tid.matches("[1-9]\\d*")) return downstreamResponseError();
            TerminalResponse terminal = new TerminalResponse();
            terminal.setTid(tid);
            return ResponseResult.success(terminal);
        } catch (RestClientException | JsonProcessingException exception) {
            return ResponseResult.fail(1700, "地图终端服务暂不可用，请稍后重试");
        }
    }


    /**
     * This function is used to search the surrounding terminals about the center
     * @param center - the central terminal
     * @param radius - the description of the terminal (usually the range of the request)
     * @return
     */
    public ResponseResult<List<TerminalResponse>> aroundsearch(String center, Integer radius){
        //assemble the url for request
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.TERMINAL_AROUNDSEARCH);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("center="+center);
        url.append("&");
        url.append("radius="+radius);

        ResponseEntity<String> stringResponseEntity = restTemplate.postForEntity(url.toString(), null, String.class);

        // Parse terminal search results
        String body = stringResponseEntity.getBody();
        JSONObject result = new JSONObject(body);
        JSONObject data = result.getJSONObject("data");

        List<TerminalResponse> terminalResponseList = new ArrayList<>();

        JSONArray results = data.getJSONArray("results");

        // analyze all the surrounding terminals from the Amap server
        for (int i=0;i < results.length();i++){
            TerminalResponse terminalResponse = new TerminalResponse();

            JSONObject jsonObject = results.getJSONObject(i);
            String desc = jsonObject.getString("desc");
            Long carId = Long.parseLong(desc);
            String tid = String.valueOf(jsonObject.getInt("tid"));

            JSONObject location = jsonObject.getJSONObject("location");
            String longitude = String.valueOf(location.getBigDecimal("longitude"));
            String latitude = String.valueOf(location.getBigDecimal("latitude"));

            terminalResponse.setCarId(carId);
            terminalResponse.setTid(tid);
            terminalResponse.setLongitude(longitude);
            terminalResponse.setLatitude(latitude);

            terminalResponseList.add(terminalResponse);
        }



        return ResponseResult.success(terminalResponseList);
    }

    /**
     * This method is to find all the traces nearby and obtain how far and how long they need to get here
     * @param tid - the id of the terminal
     * @param starttime - the start time of the trace
     * @param endtime - the end time of the trace
     * @return
     */
    public ResponseResult<TrsearchResponse> trsearch(String tid, Long starttime , Long endtime){
        // assemble the requesting urls
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.TERMINAL_TRSEARCH);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("tid="+tid);
        url.append("&");
        url.append("starttime="+starttime);
        url.append("&");
        url.append("endtime="+endtime);

        log.debug("Requesting Amap trace search");
        ResponseEntity<String> forEntity = restTemplate.getForEntity(url.toString(), String.class);

        try {
            JSONObject result = new JSONObject(forEntity.getBody());
            JSONObject data = result.getJSONObject("data");
            int counts = data.getInt("counts");
            if (counts == 0){
                return ResponseResult.fail(CommonStatus.MAP_TRACK_EMPTY.getCode(),
                        CommonStatus.MAP_TRACK_EMPTY.getMessage());
            }
            if (counts < 0) {
                return downstreamResponseError();
            }
            JSONArray tracks = data.getJSONArray("tracks");
            if (tracks.length() == 0) {
                return ResponseResult.fail(CommonStatus.MAP_TRACK_EMPTY.getCode(),
                        CommonStatus.MAP_TRACK_EMPTY.getMessage());
            }
            long driveMile = 0L;
            long totalDurationMillis = 0L;
            for (int i=0;i<tracks.length();i++){
                JSONObject jsonObject = tracks.getJSONObject(i);

                long distance = jsonObject.getLong("distance");
                long time = jsonObject.getLong("time");
                if (distance < 0 || time < 0) {
                    return downstreamResponseError();
                }
                driveMile = Math.addExact(driveMile, distance);
                totalDurationMillis = Math.addExact(totalDurationMillis, time);
            }
            TrsearchResponse trsearchResponse = new TrsearchResponse();
            trsearchResponse.setDriveMile(driveMile);
            trsearchResponse.setDriveTime(TimeUnit.MILLISECONDS.toSeconds(totalDurationMillis));
            return ResponseResult.success(trsearchResponse);
        } catch (Exception e) {
            log.warn("Amap trace search response parsing failed; operation=trsearch, exceptionType={}",
                    e.getClass().getSimpleName());
            return downstreamResponseError();
        }

    }

    private <T> ResponseResult<T> downstreamResponseError() {
        return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
    }
}

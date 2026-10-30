package com.flytaxi.servicemap.remote;


import com.flytaxi.internalCommon.constant.AmapConfigConstant;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.response.TrackResponse;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * This class is to deal with all the traces
 */
@Service
@Slf4j
public class TrackClient {

    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.sid}")
    private String amapSid;

    //the one needed to connect with Amap
    @Autowired
    private RestTemplate restTemplate;


    /**
     * This function is used to add a new trace to the terminal
     * @param tid - the terminal id
     * @return
     */
    public ResponseResult<TrackResponse> add(String tid){
        if (amapKey == null || amapKey.isBlank() || amapSid == null || amapSid.isBlank()) {
            return ResponseResult.fail(0, "地图轨迹服务未配置，请联系管理员配置AMAP_KEY和AMAP_SID");
        }
        if (tid == null || tid.isBlank()) return ResponseResult.fail(0, "请先初始化车辆地图终端");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", amapKey);
        form.add("sid", amapSid);
        form.add("tid", tid);
        try {
            String body = restTemplate.postForEntity(AmapConfigConstant.TRACK_ADD, form, String.class).getBody();
            if (body == null || body.isBlank()) return invalidResponse();
            JsonNode result = new ObjectMapper().readTree(body);
            int code = result.path("errcode").asInt(-1);
            if (code != 10000) {
                return ResponseResult.fail(0, "地图轨迹创建失败（错误码：" + code + "），请检查地图配置");
            }
            String trid = result.path("data").path("trid").asText("");
            if (!trid.matches("[1-9]\\d*")) return invalidResponse();
            TrackResponse track = new TrackResponse();
            track.setTrid(trid);
            track.setTrname(result.path("data").path("trname").asText(""));
            return ResponseResult.success(track);
        } catch (RestClientException | JsonProcessingException exception) {
            return ResponseResult.fail(1700, "地图轨迹服务暂不可用，请稍后重试");
        }
    }

    private ResponseResult<TrackResponse> invalidResponse() {
        return ResponseResult.fail(1700, "地图轨迹服务返回无效数据，请稍后重试");
    }
}

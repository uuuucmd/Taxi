package com.george.servicemap.remote;

import com.george.internalCommon.dto.ResponseResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@Service
public class GeocodeClient {

    @Value("${amap.key}")
    private String mapKey;

    @Autowired
    private RestTemplate restTemplate;

    public ResponseResult<Map<String, String>> geocode(String address) {
        return resolve(address, false);
    }

    public ResponseResult<Map<String, String>> cityCode(String address) {
        ResponseResult<Map<String, String>> result = resolve(address, true);
        if (result.getCode() != 1) {
            return result;
        }
        return ResponseResult.success(Map.of("cityCode", result.getData().get("cityCode")));
    }

    private ResponseResult<Map<String, String>> resolve(String address, boolean allowCity) {
        if (address == null || address.isBlank() || address.length() > 200 || address.contains("|")) {
            return ResponseResult.fail(0, "请输入有效的详细地址");
        }
        if (mapKey == null || mapKey.isBlank()) {
            return ResponseResult.fail(0, "地址解析服务未配置，请联系管理员");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/geocode/geo")
                .queryParam("key", mapKey)
                .queryParam("address", "{address}")
                .queryParam("output", "JSON")
                .encode()
                .buildAndExpand(address.trim())
                .toUri();
        try {
            JSONObject payload = new JSONObject(restTemplate.getForEntity(uri, String.class).getBody());
            if (!"1".equals(payload.optString("status"))) {
                return ResponseResult.fail(0, "地址解析服务暂不可用，请稍后重试");
            }
            JSONArray geocodes = payload.optJSONArray("geocodes");
            if (geocodes == null || geocodes.length() == 0) {
                return ResponseResult.fail(0, "未找到该地址，请补充城市和详细地点");
            }
            if (geocodes.length() != 1) {
                return ResponseResult.fail(0, "该地址存在多个地点，请补充城市和详细地点");
            }
            JSONObject location = geocodes.getJSONObject(0);
            String level = location.optString("level");
            if ("国家".equals(level) || ("省".equals(level)
                    && !(allowCity && location.optString("adcode").matches("(?:11|12|31|50)0000")))
                    || (!allowCity && Set.of("市", "区县").contains(level))) {
                return ResponseResult.fail(0, "地址不够具体，请补充街道、门牌或地点名称");
            }
            String[] coordinates = location.getString("location").split(",");
            String districtCode = location.getString("adcode");
            if (coordinates.length != 2 || !districtCode.matches("\\d{6}")) {
                return ResponseResult.fail(0, "地址解析结果无效，请重新填写地址");
            }
            double longitude = Double.parseDouble(coordinates[0]);
            double latitude = Double.parseDouble(coordinates[1]);
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
                return ResponseResult.fail(0, "地址解析结果无效，请重新填写地址");
            }
            String cityCode = districtCode.matches("^(11|12|31|50).*")
                    ? districtCode.substring(0, 2) + "0000" : districtCode.substring(0, 4) + "00";
            return ResponseResult.success(Map.of(
                    "longitude", coordinates[0], "latitude", coordinates[1],
                    "districtCode", districtCode, "cityCode", cityCode,
                    "formattedAddress", location.getString("formatted_address")));
        } catch (Exception exception) {
            return ResponseResult.fail(0, "地址解析失败，请稍后重试");
        }
    }
}

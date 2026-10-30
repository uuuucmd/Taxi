package com.flytaxi.servicemap.remote;

import com.flytaxi.internalCommon.constant.AmapConfigConstant;
import com.flytaxi.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @Date: 2024-10-28-15:57
 * @Description: com.flytaxi.servicemap.remote
 */
@Service
public class MapDicDistrictClient {

    @Value("${amap.key}")
    private String mapKey;

    @Autowired
    private RestTemplate restTemplate;

    public String dicDistrict(String keywords) {

        // key=<user key>
        // create a url for visiting
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.DISTRICT_URL);
        url.append("keywords=" + keywords + "&");
        url.append("subdistrict=3" + "&");
        url.append("key=" + mapKey);

        // access Amap service
        ResponseEntity<String> entity = restTemplate.getForEntity(url.toString(), String.class);

        return entity.getBody();
    }
}

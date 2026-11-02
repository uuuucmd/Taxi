package com.flytaxi.serviceprice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.dto.PriceRule;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.serviceprice.mapper.PriceRuleMapper;
import com.flytaxi.serviceprice.cache.PriceRuleCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used to provide services related to pricing rules for controller
 */
@Service
public class PriceRuleService {

    @Autowired
    PriceRuleMapper priceRuleMapper;

    @Autowired
    PriceRuleCache priceRuleCache;

    /**
     * This method is used to add pricing rules
     * @param priceRule
     * @return
     */
    public ResponseResult add(PriceRule priceRule){

        // Splicing fareType
        String cityCode = priceRule.getCityCode();
        String vehicleType = priceRule.getVehicleType();
        String fareType = cityCode + "$" + vehicleType;
        priceRule.setFareType(fareType);

        // query for specific rule
        QueryWrapper<PriceRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("city_code",cityCode);
        queryWrapper.eq("vehicle_type",vehicleType);
        queryWrapper.orderByDesc("fare_version");

        List<PriceRule> priceRules = priceRuleMapper.selectList(queryWrapper);
        Integer fareVersion = 0;

        // Once the same pricing rule already exists, it cannot be added
        if (priceRules.size()>0){
            return ResponseResult.fail(CommonStatus.PRICE_RULE_EXISTS.getCode(),CommonStatus.PRICE_RULE_EXISTS.getMessage());
        }
        priceRule.setFareVersion(++fareVersion);


        priceRuleMapper.insert(priceRule);
        priceRuleCache.evict(fareType);
        return ResponseResult.success();
    }


    /**
     * This method is used to edit pricing rules
     * @param priceRule
     * @return
     */
    public ResponseResult edit(PriceRule priceRule){
        String cityCode = priceRule.getCityCode();
        String vehicleType = priceRule.getVehicleType();
        String fareType = cityCode + "$" + vehicleType;
        priceRule.setFareType(fareType);

        QueryWrapper<PriceRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("city_code",cityCode);
        queryWrapper.eq("vehicle_type",vehicleType);
        queryWrapper.orderByDesc("fare_version");

        List<PriceRule> priceRules = priceRuleMapper.selectList(queryWrapper);
        Integer fareVersion = 0;
        if (priceRules.size()>0){
            PriceRule lastPriceRule = priceRules.get(0);
            Double unitPricePerMile = lastPriceRule.getUnitPricePerMile();
            Double unitPricePerMinute = lastPriceRule.getUnitPricePerMinute();
            Double startFare = lastPriceRule.getStartFare();
            Integer startMile = lastPriceRule.getStartMile();

            if (unitPricePerMile.doubleValue() == priceRule.getUnitPricePerMile().doubleValue()
            && unitPricePerMinute.doubleValue() == priceRule.getUnitPricePerMinute().doubleValue()
            && startFare.doubleValue() == priceRule.getStartFare().doubleValue()
            && startMile.intValue() == priceRule.getStartMile().intValue()){
                return ResponseResult.fail(CommonStatus.PRICE_RULE_NOT_EDIT.getCode(),CommonStatus.PRICE_RULE_NOT_EDIT.getMessage());
            }


            fareVersion = lastPriceRule.getFareVersion();
        }
        priceRule.setFareVersion(++fareVersion);


        priceRuleMapper.insert(priceRule);
        priceRuleCache.evict(fareType);
        return ResponseResult.success();
    }


    /**
     * This method is used to get the rule with the latest version
     * @param fareType
     * @return
     */
    public ResponseResult<PriceRule> getLatestVersion(String fareType){
        if (fareType == null || fareType.isBlank()) {
            return priceRuleNotExists();
        }

        PriceRuleCache.Lookup cached = priceRuleCache.find(fareType);
        if (cached.isHit()) {
            return ResponseResult.success(cached.getPriceRule());
        }
        if (cached.isNotExists()) {
            return priceRuleNotExists();
        }//既没命中，也没有不存在的key，说明缓存击穿了，需要去数据库查找

        QueryWrapper<PriceRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("fare_type",fareType);

        queryWrapper.orderByDesc("fare_version");
        queryWrapper.last("LIMIT 1");

        List<PriceRule> priceRules = priceRuleMapper.selectList(queryWrapper);

        if (priceRules.size() > 0){
            PriceRule priceRule = priceRules.get(0);
            priceRuleCache.put(fareType, priceRule);
            return ResponseResult.success(priceRule);
        }else {
            priceRuleCache.putNotExists(fareType);
            return priceRuleNotExists();
        }


    }

    private ResponseResult<PriceRule> priceRuleNotExists() {
        return ResponseResult.fail(CommonStatus.PRICE_RULE_NOT_EXISTS.getCode(),
                CommonStatus.PRICE_RULE_NOT_EXISTS.getMessage());
    }


    /**
     * This method is used to determine whether the rule is the latest version
     */
    public ResponseResult<Boolean> isLatest(String fareType, int fareVersion){
        // Get the latest pricing rules first, and then check
        ResponseResult<PriceRule> newestVersionPriceRule = getLatestVersion(fareType);
        if (newestVersionPriceRule.getCode() == CommonStatus.PRICE_RULE_NOT_EXISTS.getCode()){
            return ResponseResult.fail(CommonStatus.PRICE_RULE_NOT_EXISTS.getCode(),
                    CommonStatus.PRICE_RULE_NOT_EXISTS.getMessage());
        }
        if (newestVersionPriceRule.getCode() != CommonStatus.SUCCESS.getCode()){
            return ResponseResult.fail(newestVersionPriceRule.getCode(),
                    newestVersionPriceRule.getMessage());
        }

        PriceRule priceRule = newestVersionPriceRule.getData();
        if (priceRule == null || priceRule.getFareVersion() == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        return ResponseResult.success(priceRule.getFareVersion().intValue() == fareVersion);


    }


    /**
     * This method is used to determine whether the pricing rules of the city and
     * the corresponding vehicle type exist
     * @param priceRule
     * @return
     */
    public ResponseResult<Boolean> ifExists(PriceRule priceRule){
        if (priceRule == null || priceRule.getCityCode() == null
                || priceRule.getCityCode().isBlank()
                || priceRule.getVehicleType() == null
                || priceRule.getVehicleType().isBlank()) {
            return ResponseResult.success(false);
        }

        String fareType = priceRule.getCityCode() + "$" + priceRule.getVehicleType();
        ResponseResult<PriceRule> latestRule = getLatestVersion(fareType);
        if (latestRule.getCode() == CommonStatus.PRICE_RULE_NOT_EXISTS.getCode()) {
            return ResponseResult.success(false);
        }
        if (latestRule.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(latestRule.getCode(), latestRule.getMessage());
        }
        return ResponseResult.success(latestRule.getData() != null);
    }
}

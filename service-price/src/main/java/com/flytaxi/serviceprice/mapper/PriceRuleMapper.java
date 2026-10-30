package com.flytaxi.serviceprice.mapper;

import com.flytaxi.internalCommon.dto.PriceRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

/**
 * This class is used to join the table of pricing rules
 * @since 2024-11-12
 */
@Repository
public interface PriceRuleMapper extends BaseMapper<PriceRule> {

}

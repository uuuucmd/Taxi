package com.flytaxi.serviceorder.job;

import com.flytaxi.internalCommon.trace.TraceIdContext;
import com.flytaxi.serviceorder.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class OrderFinalizationRetryJob {

    @Autowired
    private OrderInfoService orderInfoService;

    @Scheduled(fixedDelayString = "${order.finalization.retry-scan-delay-ms:30000}")
    public void retryDueFinalizations() {
        TraceIdContext.set(null);
        try {
            orderInfoService.retryDueFinalizations(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("Order finalization retry scan failed; exceptionType={}",
                    e.getClass().getSimpleName());
        } finally {
            TraceIdContext.clear();
        }
    }
}

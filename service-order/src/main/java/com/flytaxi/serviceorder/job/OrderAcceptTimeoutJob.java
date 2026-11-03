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
public class OrderAcceptTimeoutJob {

    @Autowired
    private OrderInfoService orderInfoService;

    @Scheduled(fixedDelayString = "${order.accept-timeout.scan-delay-ms:60000}")
    public void cancelExpiredUnacceptedOrders() {
        TraceIdContext.set(null);
        try {
            int cancelled = orderInfoService.cancelUnacceptedOrders(LocalDateTime.now());
            if (cancelled > 0) {
                log.info("Unaccepted order timeout scan completed; cancelled={}", cancelled);
            }
        } catch (RuntimeException exception) {
            log.warn("Unaccepted order timeout scan failed; exceptionType={}",
                    exception.getClass().getSimpleName());
        } finally {
            TraceIdContext.clear();
        }
    }
}

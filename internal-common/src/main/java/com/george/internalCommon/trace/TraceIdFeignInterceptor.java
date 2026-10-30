package com.george.internalCommon.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class TraceIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceIdContext.current();
        if (traceId != null) {
            template.header(TraceIdContext.HEADER_NAME, traceId);
        }
    }
}

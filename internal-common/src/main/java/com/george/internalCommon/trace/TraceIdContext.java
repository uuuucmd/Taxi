package com.george.internalCommon.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceIdContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private TraceIdContext() {
    }

    public static String resolve(String suppliedTraceId) {
        if (suppliedTraceId != null && VALID_TRACE_ID.matcher(suppliedTraceId).matches()) {
            return suppliedTraceId;
        }
        return UUID.randomUUID().toString();
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, resolve(traceId));
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}

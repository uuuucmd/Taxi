package com.flytaxi.servicedriveruser.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DriverUserPageResponse {
    private long page;
    private long pageSize;
    private long total;
    private List<DriverSummary> records;

    @Data
    public static class DriverSummary {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        private String driverName;
        private String driverPhone;
        private String address;
        private Integer driverGender;
        private Integer state;
        private LocalDateTime gmtCreate;
    }
}

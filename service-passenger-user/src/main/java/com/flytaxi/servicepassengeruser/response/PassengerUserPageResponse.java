package com.flytaxi.servicepassengeruser.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PassengerUserPageResponse {

    private long page;
    private long pageSize;
    private long total;
    private List<PassengerSummary> records;

    @Data
    public static class PassengerSummary {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        private String passengerPhone;
        private String passengerName;
        private Integer passengerGender;
        private Integer state;
        private LocalDateTime gmtCreate;
    }
}

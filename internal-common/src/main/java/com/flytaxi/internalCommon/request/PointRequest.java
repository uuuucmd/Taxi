package com.flytaxi.internalCommon.request;

import com.flytaxi.internalCommon.dto.PointDTO;
import lombok.Data;

/**
 * This class contains the formal information of a trace
 */
@Data
public class PointRequest {

    private String tid;

    private String trid;

    private PointDTO[] points;

}

package com.george.servicemap.service;

import com.george.internalCommon.dto.PointDTO;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.ApiDriverPointRequest;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.internalCommon.response.DirectionResponse;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrsearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LocalLocationService {
    static final String POSITIONS = "flytaxi:local:{locations}:geo";
    static final String UPDATED = "flytaxi:local:{locations}:updated";
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>(
            "local old = redis.call('ZSCORE', KEYS[2], ARGV[1]); "
            + "if old and tonumber(old) >= tonumber(ARGV[4]) then return 0 end; "
            + "redis.call('GEOADD', KEYS[1], ARGV[2], ARGV[3], ARGV[1]); "
            + "redis.call('ZADD', KEYS[2], ARGV[4], ARGV[1]); return 1", Long.class);

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DirectionService directions;
    @Value("${taxi.local-simulation:true}")
    private boolean localSimulation = true;
    @Value("${taxi.location-ttl-seconds:1800}")
    private long ttlSeconds = 1800;
    private Clock clock = Clock.systemUTC();

    public ResponseResult upload(ApiDriverPointRequest request) {
        if (!localSimulation) return ResponseResult.fail(0, "本地模拟定位未启用");
        if (request == null || request.getCarId() == null || request.getCarId() <= 0
                || request.getPoints() == null || request.getPoints().length == 0 || request.getPoints().length > 100) {
            return ResponseResult.fail(0, "请提供有效的车辆和定位点，单次最多100个点");
        }
        Point newest = null;
        long timestamp = 0;
        long now = clock.millis();
        try {
            for (PointDTO item : request.getPoints()) {
                if (item == null) throw new IllegalArgumentException();
                Point coordinate = coordinate(item.getLocation());
                String time = item.getLocatetime();
                long epoch = time != null && time.matches("\\d{13}") ? Long.parseLong(time) : Instant.parse(time).toEpochMilli();
                if (epoch <= 0 || epoch > now + 60000 || epoch < now - ttlSeconds * 1000) {
                    return ResponseResult.fail(0, "定位时间已过期或超前，请使用当前时间重新上传");
                }
                if (epoch >= timestamp) {
                    newest = coordinate;
                    timestamp = epoch;
                }
            }
        } catch (RuntimeException exception) {
            return ResponseResult.fail(0, "请输入有效的经度,纬度和定位时间，纬度范围为-85.051128至85.051128");
        }
        try {
            Long saved = redis.execute(SAVE, List.of(POSITIONS, UPDATED), request.getCarId().toString(),
                    Double.toString(newest.getX()), Double.toString(newest.getY()), Long.toString(timestamp));
            if (saved == null) return unavailable();
            return ResponseResult.success(Map.of("mode", "local", "updated", saved == 1));
        } catch (DataAccessException exception) {
            return unavailable();
        }
    }

    public ResponseResult<List<TerminalResponse>> around(String center, Integer radius) {
        if (!localSimulation) return ResponseResult.fail(0, "本地模拟定位未启用");
        Point origin;
        try {
            origin = coordinate(center);
            if (radius == null || radius <= 0 || radius > 50000) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            return ResponseResult.fail(0, "搜索坐标或半径无效");
        }
        try {
            GeoResults<RedisGeoCommands.GeoLocation<String>> nearby = redis.opsForGeo().radius(POSITIONS,
                    new Circle(origin, new Distance(radius, RedisGeoCommands.DistanceUnit.METERS)),
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().sortAscending());
            List<TerminalResponse> candidates = new ArrayList<>();
            long now = clock.millis();
            if (nearby == null) return unavailable();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> match : nearby) {
                RedisGeoCommands.GeoLocation<String> location = match.getContent();
                Double updated = redis.opsForZSet().score(UPDATED, location.getName());
                if (updated == null || updated < now - ttlSeconds * 1000 || updated > now + 60000) continue;
                TerminalResponse vehicle = new TerminalResponse();
                vehicle.setCarId(Long.valueOf(location.getName()));
                vehicle.setLongitude(Double.toString(location.getPoint().getX()));
                vehicle.setLatitude(Double.toString(location.getPoint().getY()));
                candidates.add(vehicle);
            }
            return ResponseResult.success(candidates);
        } catch (DataAccessException exception) {
            return unavailable();
        }
    }

    public ResponseResult<TrsearchResponse> trip(PredictPriceDTO route) {
        if (!localSimulation) return ResponseResult.fail(0, "本地模拟定位未启用");
        try {
            if (route == null) throw new IllegalArgumentException();
            coordinate(route.getDepLongitude() + "," + route.getDepLatitude());
            coordinate(route.getDestLongitude() + "," + route.getDestLatitude());
        } catch (RuntimeException exception) {
            return ResponseResult.fail(0, "模拟行程的出发地或目的地坐标无效");
        }
        ResponseResult<DirectionResponse> result = directions.driving(route.getDepLatitude(), route.getDepLongitude(),
                route.getDestLatitude(), route.getDestLongitude());
        if (result == null) return unavailable();
        if (result.getCode() != 1) return ResponseResult.fail(result.getCode(), result.getMessage());
        DirectionResponse data = result.getData();
        if (data == null || data.getDistance() == null || data.getDuration() == null
                || data.getDistance() < 0 || data.getDuration() < 0) return unavailable();
        TrsearchResponse trip = new TrsearchResponse();
        trip.setDriveMile(data.getDistance().longValue());
        trip.setDriveTime(data.getDuration().longValue());
        return ResponseResult.success(trip);
    }

    private Point coordinate(String value) {
        if (value == null || !value.trim().matches("-?\\d{1,3}(?:\\.\\d{1,6})?,-?\\d{1,2}(?:\\.\\d{1,6})?")) {
            throw new IllegalArgumentException();
        }
        String[] parts = value.trim().split(",");
        double longitude = Double.parseDouble(parts[0]);
        double latitude = Double.parseDouble(parts[1]);
        if (Math.abs(longitude) > 180 || Math.abs(latitude) > 85.051128) throw new IllegalArgumentException();
        return new Point(longitude, latitude);
    }

    private <T> ResponseResult<T> unavailable() {
        return ResponseResult.fail(1700, "本地定位或路线服务暂不可用，请稍后重试");
    }
}

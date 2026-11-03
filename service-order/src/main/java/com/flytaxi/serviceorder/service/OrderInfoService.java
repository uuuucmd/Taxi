package com.flytaxi.serviceorder.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.flytaxi.internalCommon.constant.CommonStatus;
import com.flytaxi.internalCommon.constant.OrderConstant;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.dto.Car;
import com.flytaxi.internalCommon.dto.DriverCarBindingRelationship;
import com.flytaxi.internalCommon.dto.OrderInfo;
import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.OrderRequest;
import com.flytaxi.internalCommon.request.PredictPriceDTO;
import com.flytaxi.internalCommon.request.PriceRuleIsNewRequest;
import com.flytaxi.internalCommon.request.PushRequest;
import com.flytaxi.internalCommon.response.OrderDriverResponse;
import com.flytaxi.internalCommon.response.TerminalResponse;
import com.flytaxi.internalCommon.response.TrsearchResponse;
import com.flytaxi.internalCommon.util.RedisPrefixUtils;
import com.flytaxi.serviceorder.mapper.OrderInfoMapper;
import com.flytaxi.serviceorder.remote.FinalizationDriverUserClient;
import com.flytaxi.serviceorder.remote.FinalizationMapClient;
import com.flytaxi.serviceorder.remote.FinalizationPriceClient;
import com.flytaxi.serviceorder.remote.ServiceDriverUserClient;
import com.flytaxi.serviceorder.remote.ServiceMapClient;
import com.flytaxi.serviceorder.remote.ServicePriceClient;
import com.flytaxi.serviceorder.remote.ServiceSsePushClient;
import lombok.extern.slf4j.Slf4j;

import org.json.JSONObject;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class is to provide service for controller to deal with orders
 */
@Service
@Slf4j
public class OrderInfoService {

    private static final int FINALIZATION_RETRY_BATCH_SIZE = 50;
    private static final int UNACCEPTED_ORDER_CANCEL_BATCH_SIZE = 100;
    private static final long ORDER_CREATE_LOCK_WAIT_SECONDS = 2L;
    private static final String ORDER_CREATE_LOCK_PREFIX = "order:create:passenger:";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String PRESERVE_GMT_CREATE_SQL = "gmt_create = gmt_create";
    private static final String PRESERVE_GMT_MODIFIED_SQL = "gmt_modified = gmt_modified";
    private Clock clock = Clock.systemDefaultZone();

    @Value("${order.accept-timeout.minutes:30}")
    private long acceptTimeoutMinutes = 30L;

    @Value("${taxi.local-simulation:true}")
    private boolean localSimulation = true;

    @Value("${order.finalization.max-attempts:3}")
    private int maxFinalizationAttempts = 3;

    @Value("${order.finalization.base-retry-delay-seconds:30}")
    private long finalizationBaseRetryDelaySeconds = 30L;

    @Value("${order.finalization.max-retry-delay-seconds:900}")
    private long finalizationMaxRetryDelaySeconds = 900L;

    @Value("${order.finalization.processing-lease-seconds:120}")
    private long finalizationProcessingLeaseSeconds = 120L;

    @Value("${order.finalization.lease-safety-margin-ms:30000}")
    private long finalizationLeaseSafetyMarginMs = 30000L;

    @Value("${feign.client.config.finalizationDriverUserClient.connectTimeout:2000}")
    private int finalizationDriverConnectTimeoutMs = 2000;

    @Value("${feign.client.config.finalizationDriverUserClient.readTimeout:10000}")
    private int finalizationDriverReadTimeoutMs = 10000;

    @Value("${feign.client.config.finalizationMapClient.connectTimeout:2000}")
    private int finalizationMapConnectTimeoutMs = 2000;

    @Value("${feign.client.config.finalizationMapClient.readTimeout:30000}")
    private int finalizationMapReadTimeoutMs = 30000;

    @Value("${feign.client.config.finalizationPriceClient.connectTimeout:2000}")
    private int finalizationPriceConnectTimeoutMs = 2000;

    @Value("${feign.client.config.finalizationPriceClient.readTimeout:10000}")
    private int finalizationPriceReadTimeoutMs = 10000;

    // the mapper to interact with orderInfo database
    @Autowired
    OrderInfoMapper orderInfoMapper;

    // the mapper to interact with price database
    @Autowired
    ServicePriceClient servicePriceClient;

    // the mapper to interact with driver user database
    @Autowired
    ServiceDriverUserClient serviceDriverUserClient;

    // the mapper to interact with redis
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    // the mapper to interact with map service
    @Autowired
    ServiceMapClient serviceMapClient;

    @Autowired
    FinalizationPriceClient finalizationPriceClient;

    @Autowired
    FinalizationDriverUserClient finalizationDriverUserClient;

    @Autowired
    FinalizationMapClient finalizationMapClient;

    // the mapper to interact with redisson service
    @Autowired
    RedissonClient redissonClient;

    // the mapper to push order to front-end service
    @Autowired
    ServiceSsePushClient serviceSsePushClient;

    @PostConstruct
    public void validateFinalizationPolicyOnStartup() {
        validateFinalizationPolicy(
                maxFinalizationAttempts,
                finalizationBaseRetryDelaySeconds,
                finalizationMaxRetryDelaySeconds,
                finalizationProcessingLeaseSeconds,
                finalizationLeaseSafetyMarginMs,
                finalizationDriverConnectTimeoutMs,
                finalizationDriverReadTimeoutMs,
                finalizationMapConnectTimeoutMs,
                finalizationMapReadTimeoutMs,
                finalizationPriceConnectTimeoutMs,
                finalizationPriceReadTimeoutMs);
    }


    /**
     * This function is to create a new order
     * @param orderRequest order details with the authenticated passenger identity
     * @return
     */
    public ResponseResult add(OrderRequest orderRequest) {
        if (orderRequest == null || orderRequest.getPassengerId() == null
                || orderRequest.getPassengerId() <= 0) {
            return ResponseResult.fail(CommonStatus.USER_NOT_EXISTS.getCode(),
                    CommonStatus.USER_NOT_EXISTS.getMessage());
        }

        String idempotencyKey = orderRequest.getIdempotencyKey();
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            return ResponseResult.fail(CommonStatus.ORDER_IDEMPOTENCY_KEY_INVALID.getCode(),
                    CommonStatus.ORDER_IDEMPOTENCY_KEY_INVALID.getMessage());
        }
        String requestFingerprint = orderRequestFingerprint(orderRequest);
        ResponseResult existingRequest = resolveDuplicateOrder(
                orderInfoMapper.selectOne(new QueryWrapper<OrderInfo>()
                        .eq("passenger_id", orderRequest.getPassengerId())
                        .eq("idempotency_key", idempotencyKey)),
                requestFingerprint);
        if (existingRequest != null) {
            return existingRequest;
        }

        String fareType = orderRequest.getFareType();
        String cityCode = cityCodeFromFareType(fareType);
        if (cityCode == null || fareType.substring(fareType.indexOf('$') + 1).isBlank()) {
            return ResponseResult.fail(CommonStatus.PRICE_RULE_NOT_EXISTS.getCode(),
                    CommonStatus.PRICE_RULE_NOT_EXISTS.getMessage());
        }
        if (orderRequest.getFareVersion() == null) {
            return ResponseResult.fail(CommonStatus.PRICE_RULE_CHANGED.getCode(),
                    CommonStatus.PRICE_RULE_CHANGED.getMessage());
        }

        // Check whether the version of the pricing rule is the latest
        PriceRuleIsNewRequest priceRuleIsNewRequest = new PriceRuleIsNewRequest();
        priceRuleIsNewRequest.setFareType(fareType);
        priceRuleIsNewRequest.setFareVersion(orderRequest.getFareVersion());
        ResponseResult<Boolean> latestVersion = servicePriceClient.isLatest(priceRuleIsNewRequest);
        if (latestVersion != null
                && latestVersion.getCode() == CommonStatus.PRICE_RULE_NOT_EXISTS.getCode()) {
            return ResponseResult.fail(CommonStatus.SERVICE_NOT_PROVIDED.getCode(),
                    CommonStatus.SERVICE_NOT_PROVIDED.getMessage());
        }
        ResponseResult latestVersionFailure = validateRequiredBooleanResponse(latestVersion);
        if (latestVersionFailure != null) {
            return latestVersionFailure;
        }
        if (!latestVersion.getData()) {
            return ResponseResult.fail(CommonStatus.PRICE_RULE_CHANGED.getCode(),
                    CommonStatus.PRICE_RULE_CHANGED.getMessage());
        }

        // Determine whether the placed device is a blacklist device
        if (isBlackDevice(orderRequest)) {
            return ResponseResult.fail(CommonStatus.DEVICE_IS_BLACK.getCode(), CommonStatus.DEVICE_IS_BLACK.getMessage());
        }


        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderRequest,orderInfo);
        orderInfo.setOrderStatus(OrderConstant.ORDER_START);
        orderInfo.setIdempotencyKey(idempotencyKey);
        orderInfo.setRequestFingerprint(requestFingerprint);
        LocalDateTime now = LocalDateTime.now();
        orderInfo.setGmtCreate(now);
        orderInfo.setGmtModified(now);

        RLock orderLock;
        try {
            orderLock = redissonClient.getLock(ORDER_CREATE_LOCK_PREFIX + orderRequest.getPassengerId());
        } catch (RuntimeException exception) {
            log.warn("Passenger order lock unavailable; passengerId={}", orderRequest.getPassengerId());
            return orderCreateUnavailable();
        }

        boolean lockAcquired = false;
        try {
            lockAcquired = orderLock.tryLock(ORDER_CREATE_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!lockAcquired) {
                return ResponseResult.fail(CommonStatus.ORDER_CREATE_BUSY.getCode(),
                        CommonStatus.ORDER_CREATE_BUSY.getMessage());
            }

            OrderInfo existingOrder = orderInfoMapper.selectOne(new QueryWrapper<OrderInfo>()
                    .eq("passenger_id", orderRequest.getPassengerId())
                    .eq("idempotency_key", idempotencyKey));
            ResponseResult duplicateResult = resolveDuplicateOrder(existingOrder, requestFingerprint);
            if (duplicateResult != null) {
                return duplicateResult;
            }

            if (hasPassengerUnpaidOrder(orderRequest.getPassengerId())) {
                return ResponseResult.fail(CommonStatus.ORDER_UNPAID.getCode(),
                        CommonStatus.ORDER_UNPAID.getMessage());
            }
            if (isPassengerOrderGoingon(orderRequest.getPassengerId()) > 0) {
                return ResponseResult.fail(CommonStatus.ORDER_GOING_ON.getCode(),
                        CommonStatus.ORDER_GOING_ON.getMessage());
            }

            if (orderInfoMapper.insert(orderInfo) != 1) {
                return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                        CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
            }
            log.info("Order created; orderId={}, status={}, result=success",
                    orderInfo.getId(), OrderConstant.ORDER_START);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseResult.fail(CommonStatus.ORDER_CREATE_BUSY.getCode(),
                    CommonStatus.ORDER_CREATE_BUSY.getMessage());
        } catch (DuplicateKeyException exception) {
            OrderInfo existingOrder = orderInfoMapper.selectOne(new QueryWrapper<OrderInfo>()
                    .eq("passenger_id", orderRequest.getPassengerId())
                    .eq("idempotency_key", idempotencyKey));
            ResponseResult duplicateResult = resolveDuplicateOrder(existingOrder, requestFingerprint);
            if (duplicateResult != null) {
                return duplicateResult;
            }
            return orderCreateUnavailable();
        } catch (RuntimeException exception) {
            log.warn("Passenger order creation failed while holding lock; passengerId={}",
                    orderRequest.getPassengerId());
            return orderCreateUnavailable();
        } finally {
            if (lockAcquired) {
                try {
                    if (orderLock.isHeldByCurrentThread()) {
                        orderLock.unlock();
                    }
                } catch (RuntimeException exception) {
                    log.warn("Could not release passenger order lock; passengerId={}",
                            orderRequest.getPassengerId());
                }
            }
        }

        return orderCreated(orderInfo);
    }

    private ResponseResult resolveDuplicateOrder(OrderInfo existingOrder, String requestFingerprint) {
        if (existingOrder == null) {
            return null;
        }
        if (!requestFingerprint.equals(existingOrder.getRequestFingerprint())) {
            return ResponseResult.fail(CommonStatus.ORDER_IDEMPOTENCY_CONFLICT.getCode(),
                    CommonStatus.ORDER_IDEMPOTENCY_CONFLICT.getMessage());
        }
        if (isOrderStatus(existingOrder.getOrderStatus(), OrderConstant.ORDER_INVALID)) {
            return ResponseResult.fail(CommonStatus.DISPATCH_FAILED.getCode(),
                    CommonStatus.DISPATCH_FAILED.getMessage());
        }
        log.info("Duplicate order request resolved; orderId={}, result=idempotent", existingOrder.getId());
        return orderCreated(existingOrder);
    }

    private ResponseResult orderCreated(OrderInfo orderInfo) {
        return ResponseResult.success(orderInfo.getId() == null ? null : orderInfo.getId().toString());
    }

    private String orderRequestFingerprint(OrderRequest request) {
        List<Object> fields = Arrays.asList(
                request.getPassengerId(), request.getPassengerPhone(), request.getAddress(),
                request.getDepartTime(), request.getOrderTime(), request.getDeparture(),
                request.getDepLongitude(), request.getDepLatitude(), request.getDestination(),
                request.getDestLongitude(), request.getDestLatitude(), request.getEncrypt(),
                request.getFareType(), request.getFareVersion(), request.getVehicleType(),
                request.getDeviceCode());
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            if (field == null) {
                canonical.append("-1:");
            } else {
                String value = field.toString();
                canonical.append(value.length()).append(':').append(value);
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }


    /**
     * This function is aimed to deal with the real-time order operation
     * @param orderInfo the order needed to be dispatched
     * @return - 1, if successful
     */
    public int dispatchRealTimeOrder(OrderInfo orderInfo){
        int result = 0;

        // the location of the passenger
        String depLatitude = orderInfo.getDepLatitude();
        String depLongitude = orderInfo.getDepLongitude();
        String center = depLongitude+","+depLatitude;

        List<Integer> radiusList = new ArrayList<>();

        // the range to search available drivers from the passenger
        radiusList.add(1000);
        radiusList.add(3000);
        radiusList.add(5000);

        // returning list
        ResponseResult<List<TerminalResponse>> listResponseResult = null;
        radius:
        for (int i=0;i<radiusList.size();i++){
            Integer radius = radiusList.get(i);
            listResponseResult = localSimulation ? serviceMapClient.localAroundSearch(center, radius)
                    : serviceMapClient.terminalAroundSearch(center, radius);

            if (listResponseResult == null) {
                log.warn("Dispatch terminal search skipped; radius={}, reason=null_response", radius);
                continue;
            }
            if (listResponseResult.getCode() != CommonStatus.SUCCESS.getCode()) {
                log.warn("Dispatch terminal search skipped; radius={}, responseCode={}",
                        radius, listResponseResult.getCode());
                continue;
            }

            // analyze the terminal
            List<TerminalResponse> data = listResponseResult.getData();
            if (data == null) {
                log.warn("Dispatch terminal search skipped; radius={}, reason=null_data", radius);
                continue;
            }
            if (localSimulation && data.isEmpty()) {
                String orderCityCode = cityCodeFromFareType(orderInfo.getFareType());
                if (orderCityCode == null || orderCityCode.isBlank()) orderCityCode = orderInfo.getAddress();
                ResponseResult<List<TerminalResponse>> localCandidates =
                        serviceDriverUserClient.getAvailableCars(orderCityCode);
                if (localCandidates != null && localCandidates.getCode() == CommonStatus.SUCCESS.getCode()
                        && localCandidates.getData() != null) {
                    data = localCandidates.getData();
                    log.debug("Local dispatch fallback loaded; cityCode={}, candidateCount={}",
                            orderCityCode, data.size());
                }
            }
            log.debug("Dispatch terminal search succeeded; radius={}, candidateCount={}", radius, data.size());

            for (int j=0;j<data.size();j++){
                TerminalResponse terminalResponse = data.get(j);
                Long carId = terminalResponse.getCarId();

                if (localSimulation) {
                    ResponseResult<Car> candidate = serviceDriverUserClient.getCarById(carId);
                    String orderCityCode = cityCodeFromFareType(orderInfo.getFareType());
                    if (orderCityCode == null || orderCityCode.isBlank()) orderCityCode = orderInfo.getAddress();
                    if (candidate == null || candidate.getCode() != 1 || candidate.getData() == null
                            || !Integer.valueOf(0).equals(candidate.getData().getState())
                            || orderCityCode == null
                            || !orderCityCode.equals(candidate.getData().getAddress())) continue;
                }

                String longitude = terminalResponse.getLongitude();
                String latitude = terminalResponse.getLatitude();
                if (longitude == null || longitude.isBlank()) longitude = depLongitude;
                if (latitude == null || latitude.isBlank()) latitude = depLatitude;

                // Check if there are any extra drivers available
                ResponseResult<OrderDriverResponse> availableDriver = serviceDriverUserClient.getAvailableDriver(carId);
                if (availableDriver == null) {
                    log.debug("Dispatch driver candidate skipped; carId={}, reason=null_response", carId);
                    continue;
                }
                if (availableDriver.getCode() != CommonStatus.SUCCESS.getCode()) {
                    log.debug("Dispatch driver candidate skipped; carId={}, responseCode={}",
                            carId, availableDriver.getCode());
                    continue;
                }

                // extract information from that driver
                OrderDriverResponse orderDriverResponse = availableDriver.getData();
                if (orderDriverResponse == null) {
                    log.debug("Dispatch driver candidate skipped; carId={}, reason=null_data", carId);
                    continue;
                }
                else {

                    Long driverId = orderDriverResponse.getDriverId();
                    String driverPhone = orderDriverResponse.getDriverPhone();
                    String licenseId = orderDriverResponse.getLicenseId();
                    String vehicleNo = orderDriverResponse.getVehicleNo();
                    String vehicleTypeFromCar = orderDriverResponse.getVehicleType();

                    // Does the model of the vehicle match?
                    String vehicleType = orderInfo.getVehicleType();
                    if (!vehicleType.trim().equals(vehicleTypeFromCar.trim())) {
                        continue ;
                    }


                    // This lock is to prevent multiple threads from ordering the same driver
                    String lockKey = "driver:assignment:" + driverId;
                    RLock lock = redissonClient.getLock(lockKey);
                    lock.lock();
                    try {

                        // Determine if the driver has an order in progress
                        if (isDriverOrderGoingon(driverId)){
                            continue ;
                        }


                        // Set information about the order and the driver's vehicle
                        orderInfo.setDriverId(driverId);
                        orderInfo.setDriverPhone(driverPhone);
                        orderInfo.setCarId(carId);
                        orderInfo.setReceiveOrderCarLongitude(longitude);
                        orderInfo.setReceiveOrderCarLatitude(latitude);
                        orderInfo.setReceiveOrderTime(LocalDateTime.now());
                        orderInfo.setLicenseId(licenseId);
                        orderInfo.setVehicleNo(vehicleNo);
                        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);

                        int assigned = orderInfoMapper.updateById(orderInfo);
                        if (assigned == 1) {
                            log.info("Order status changed; action=dispatch_assign, orderId={}, fromStatus={}, toStatus={}, result=success",
                                    orderInfo.getId(), OrderConstant.ORDER_START,
                                    OrderConstant.DRIVER_RECEIVE_ORDER);
                        } else {
                            log.warn("Order dispatch assignment did not update order; orderId={}, expectedStatus={}, targetStatus={}, result=failed",
                                    orderInfo.getId(), OrderConstant.ORDER_START,
                                    OrderConstant.DRIVER_RECEIVE_ORDER);
                        }

                        // Notify the driver
                        JSONObject driverContent = new JSONObject();
                        driverContent.put("orderId",orderInfo.getId());
                        driverContent.put("passengerId",orderInfo.getPassengerId());
                        driverContent.put("passengerPhone",orderInfo.getPassengerPhone());
                        driverContent.put("departure",orderInfo.getDeparture());
                        driverContent.put("depLongitude",orderInfo.getDepLongitude());
                        driverContent.put("depLatitude",orderInfo.getDepLatitude());
                        driverContent.put("destination",orderInfo.getDestination());
                        driverContent.put("destLongitude",orderInfo.getDestLongitude());
                        driverContent.put("destLatitude",orderInfo.getDestLatitude());
                        PushRequest pushRequest = new PushRequest();
                        pushRequest.setUserId(driverId);
                        pushRequest.setIdentity(UserIdentity.DRIVER.getIdentity());
                        pushRequest.setContent(driverContent.toString());
                        serviceSsePushClient.push(pushRequest);

                        // Notify the passenger
                        JSONObject passengerContent = new  JSONObject();
                        passengerContent.put("orderId",orderInfo.getId());
                        passengerContent.put("driverId",orderInfo.getDriverId());
                        passengerContent.put("driverPhone",orderInfo.getDriverPhone());
                        passengerContent.put("vehicleNo",orderInfo.getVehicleNo());

                        // Get vehicle information, calling vehicle service
                        ResponseResult<Car> carById = serviceDriverUserClient.getCarById(carId);
                        Car carRemote = carById.getData();

                        passengerContent.put("brand", carRemote.getBrand());
                        passengerContent.put("model",carRemote.getModel());
                        passengerContent.put("vehicleColor",carRemote.getVehicleColor());

                        passengerContent.put("receiveOrderCarLongitude",orderInfo.getReceiveOrderCarLongitude());
                        passengerContent.put("receiveOrderCarLatitude",orderInfo.getReceiveOrderCarLatitude());

                        PushRequest pushRequest1 = new PushRequest();
                        pushRequest1.setUserId(orderInfo.getPassengerId());
                        pushRequest1.setIdentity(UserIdentity.PASSENGER.getIdentity());
                        pushRequest1.setContent(passengerContent.toString());

                        serviceSsePushClient.push(pushRequest1);
                        result = 1;

                        // Exit, no more driver search, if the order is sent successfully
                        break radius;
                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                }

            }

        }

        return  result;
    }

    private String cityCodeFromFareType(String fareType) {
        if (fareType == null) return null;
        int separator = fareType.indexOf('$');
        if (separator <= 0) return null;
        return fareType.substring(0, separator);
    }


    private ResponseResult validateRequiredBooleanResponse(ResponseResult<Boolean> response) {
        if (response == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        if (response.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(response.getCode(), response.getMessage());
        }
        if (response.getData() == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        return null;
    }


    /**
     * This method is used to determine whether a passenger is on a blacklist
     * @param orderRequest
     * @return
     */
    private boolean isBlackDevice(OrderRequest orderRequest) {
        if (orderRequest == null || orderRequest.getDeviceCode() == null
                || orderRequest.getDeviceCode().isBlank()) {
            return false;
        }
        String deviceCode = orderRequest.getDeviceCode().trim();
        // generate key for black list search
        String deviceCodeKey = RedisPrefixUtils.blackDeviceCodePrefix + deviceCode;
        Boolean aBoolean = stringRedisTemplate.hasKey(deviceCodeKey);
        if (aBoolean){
            String str = stringRedisTemplate.opsForValue().get(deviceCodeKey);
            int i = Integer.parseInt(str);
            // Once the same passenger orders more than twice within an hour, put him on the blacklist
            if (i >= 2){
                return true;
            }else {
                stringRedisTemplate.opsForValue().increment(deviceCodeKey);
            }

        }else {
            stringRedisTemplate.opsForValue().setIfAbsent(deviceCodeKey,"1",1L, TimeUnit.HOURS);
        }
        return false;
    }


    /**
     * This method is used to determine whether the current passenger has an order in progress
     * @param passengerId
     * @return
     */
    private int isPassengerOrderGoingon(Long passengerId){
        // No order is allowed if there is an order in progress
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("passenger_id",passengerId);
        queryWrapper.and(wrapper->wrapper.eq("order_status",OrderConstant.ORDER_START)
                .or().eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstant.PASSENGER_GETOFF)
                .or().eq("order_status",OrderConstant.TO_START_PAY)
                .or().eq("order_status",OrderConstant.FINALIZATION_PENDING)
                .or().eq("order_status",OrderConstant.FINALIZATION_FAILED)
        );


        Integer validOrderNumber = orderInfoMapper.selectCount(queryWrapper);

        return validOrderNumber;

    }

    private boolean hasPassengerUnpaidOrder(Long passengerId) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("passenger_id", passengerId)
                .in("order_status", OrderConstant.PASSENGER_GETOFF, OrderConstant.TO_START_PAY);
        return orderInfoMapper.selectCount(queryWrapper) > 0;
    }

    private ResponseResult orderCreateUnavailable() {
        return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
    }


    /**
     * This method is used to determine whether the current driver has an order in progress
     * @param driverId
     * @return
     */
    private boolean isDriverOrderGoingon(Long driverId){
        // No order is allowed if there is an order in progress
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driver_id",driverId);
        queryWrapper.and(wrapper->wrapper
                .eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)

        );


        Integer validOrderNumber = orderInfoMapper.selectCount(queryWrapper);

        return validOrderNumber > 0;

    }


    /**
     * This method is used to handle the state when the driver goes to pick up the passenger
     * @param orderRequest
     * @return
     */
    public ResponseResult toPickUpPassenger(OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();
        String toPickUpPassengerLongitude = orderRequest.getToPickUpPassengerLongitude();
        String toPickUpPassengerLatitude = orderRequest.getToPickUpPassengerLatitude();
        String toPickUpPassengerAddress = orderRequest.getToPickUpPassengerAddress();
        return transitionLegacyOrderState(
                "driver_to_pick_up",
                orderId,
                OrderConstant.DRIVER_RECEIVE_ORDER,
                OrderConstant.DRIVER_TO_PICK_UP_PASSENGER,
                updateWrapper -> updateWrapper
                        .set("to_pick_up_passenger_address", toPickUpPassengerAddress)
                        .set("to_pick_up_passenger_latitude", toPickUpPassengerLatitude)
                        .set("to_pick_up_passenger_longitude", toPickUpPassengerLongitude)
                        .set("to_pick_up_passenger_time", LocalDateTime.now(clock)));

    }


    /**
     * This method is used to deal with the status of the driver when he arrives at the passenger pick-up point
     * @param orderRequest
     * @return
     */
    public ResponseResult arrivedDeparture(OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();
        return transitionLegacyOrderState(
                "driver_arrived_departure",
                orderId,
                OrderConstant.DRIVER_TO_PICK_UP_PASSENGER,
                OrderConstant.DRIVER_ARRIVED_DEPARTURE,
                updateWrapper -> updateWrapper
                        .set("driver_arrived_departure_time", LocalDateTime.now(clock)));
    }


    /**
     * This method is used to deal with the status of the driver when he picks up the passenger
     * @param orderRequest
     * @return
     */
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();
        return transitionLegacyOrderState(
                "passenger_picked_up",
                orderId,
                OrderConstant.DRIVER_ARRIVED_DEPARTURE,
                OrderConstant.PICK_UP_PASSENGER,
                updateWrapper -> updateWrapper
                        .set("pick_up_passenger_longitude", orderRequest.getPickUpPassengerLongitude())
                        .set("pick_up_passenger_latitude", orderRequest.getPickUpPassengerLatitude())
                        .set("pick_up_passenger_time", LocalDateTime.now(clock)));
    }

    /**
     * This method is used to deal with the status of the passenger when the trip terminates
     * after the passenger disembarks and reaches the destination
     * @param orderRequest
     * @return
     */
    public ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();

        OrderInfo orderInfo = selectOrderById(orderId);
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationIdempotentSuccess(orderStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }
        if (!isFinalizationEligible(orderStatus)) {
            return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING) && !isFinalizationDue(orderInfo, now)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }

        return claimAndFinalize(orderInfo, orderRequest, now);
    }

    public ResponseResult retryFinalization(Long orderId) {
        OrderInfo orderInfo = selectOrderById(orderId);
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationIdempotentSuccess(orderStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }
        if (!isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)) {
            return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
        }
        if (!isFinalizationDue(orderInfo, now)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }

        return claimAndFinalize(orderInfo, null, now);
    }

    public ResponseResult scheduleFailedFinalizationRecovery(Long orderId) {
        OrderInfo orderInfo = selectOrderById(orderId);
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }

        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationIdempotentSuccess(orderStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }
        if (!isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Integer currentAttempts = orderInfo.getFinalizationAttempts();
        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                .eq("order_status", OrderConstant.FINALIZATION_FAILED)
                .eq("finalization_attempts", currentAttempts);
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_PENDING)
                .set("finalization_attempts", 0)
                .set("finalization_next_retry_at", now)
                .set("finalization_last_error", safeFinalizationError(ResponseResult.fail(
                        CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(),
                        CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage())))
                .set("gmt_modified", now);
        preserveOrderCreationTime(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return handleFailedFinalizationRecoveryCasMiss(orderId);
        }
        return ResponseResult.fail(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(),
                CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage());
    }

    public int retryDueFinalizations(LocalDateTime now) {
        return retryDueFinalizations(now, FINALIZATION_RETRY_BATCH_SIZE);
    }

    public int retryDueFinalizations(LocalDateTime now, int batchSize) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .and(wrapper -> wrapper.isNull("finalization_next_retry_at")
                        .or().le("finalization_next_retry_at", now))
                .orderByAsc("finalization_next_retry_at")
                .last("LIMIT " + Math.max(1, batchSize));

        List<OrderInfo> dueOrders = orderInfoMapper.selectList(queryWrapper);
        if (dueOrders == null) {
            return 0;
        }

        int processed = 0;
        for (OrderInfo dueOrder : dueOrders) {
            Long dueOrderId = dueOrder.getId();
            try {
                retryFinalization(dueOrderId);
                processed++;
            } catch (RuntimeException e) {
                log.warn("Order finalization retry failed unexpectedly; orderId={}, exceptionType={}",
                        dueOrderId, e.getClass().getSimpleName());
            }
        }
        return processed;
    }

    public int cancelUnacceptedOrders(LocalDateTime now) {
        return cancelUnacceptedOrders(now, UNACCEPTED_ORDER_CANCEL_BATCH_SIZE);
    }

    public int cancelUnacceptedOrders(LocalDateTime now, int batchSize) {
        if (now == null) {
            return 0;
        }
        LocalDateTime acceptDeadline = now.minusMinutes(Math.max(1L, acceptTimeoutMinutes));
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_status", OrderConstant.ORDER_START)
                .isNull("driver_phone")
                .le("gmt_create", acceptDeadline)
                .orderByAsc("gmt_create", "id")
                .last("LIMIT " + Math.max(1, batchSize));

        List<OrderInfo> expiredOrders = orderInfoMapper.selectList(queryWrapper);
        if (expiredOrders == null) {
            return 0;
        }

        int cancelled = 0;
        for (OrderInfo expiredOrder : expiredOrders) {
            if (expiredOrder == null || expiredOrder.getId() == null) {
                continue;
            }
            try {
                UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", expiredOrder.getId())
                        .eq("order_status", OrderConstant.ORDER_START)
                        .isNull("driver_phone")
                        .le("gmt_create", acceptDeadline)
                        .set("order_status", OrderConstant.ORDER_CANCEL)
                        .set("cancel_time", now)
                        .set("cancel_operator", Integer.parseInt(UserIdentity.ADMIN.getIdentity()))
                        .set("cancel_type_code", OrderConstant.CANCEL_PLATFORM_BEFORE)
                        .set("gmt_modified", now);
                preserveOrderCreationTime(updateWrapper);

                if (orderInfoMapper.update(null, updateWrapper) == 1) {
                    cancelled++;
                    log.info("Order status changed; action=accept_timeout, orderId={}, fromStatus={}, toStatus={}, result=success",
                            expiredOrder.getId(), OrderConstant.ORDER_START, OrderConstant.ORDER_CANCEL);
                }
            } catch (RuntimeException exception) {
                log.warn("Unaccepted order timeout cancellation failed; orderId={}, exceptionType={}",
                        expiredOrder.getId(), exception.getClass().getSimpleName());
            }
        }
        return cancelled;
    }

    private ResponseResult claimAndFinalize(OrderInfo orderInfo, OrderRequest orderRequest, LocalDateTime now) {
        Integer currentStatus = orderInfo.getOrderStatus();
        int currentAttempts = currentFinalizationAttempts(orderInfo);
        if (isOrderStatus(currentStatus, OrderConstant.FINALIZATION_PENDING)
                && currentAttempts >= maxFinalizationAttempts
                && isFinalizationDue(orderInfo, now)) {
            return moveExpiredFinalizationToFailed(orderInfo, currentAttempts, now);
        }
        if (currentAttempts >= maxFinalizationAttempts) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }

        int attempt = currentAttempts + 1;
        LocalDateTime processingLeaseUntil = now.plusSeconds(finalizationProcessingLeaseSeconds);
        Long traceEndEpochMs = orderInfo.getFinalizationTraceEndEpochMs();
        LocalDateTime passengerGetoffTime = orderInfo.getPassengerGetoffTime();
        String passengerGetoffLongitude = orderInfo.getPassengerGetoffLongitude();
        String passengerGetoffLatitude = orderInfo.getPassengerGetoffLatitude();

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderInfo.getId())
                .eq("order_status", currentStatus)
                .eq("finalization_attempts", currentAttempts);
        if (isOrderStatus(currentStatus, OrderConstant.FINALIZATION_PENDING)) {
            updateWrapper.and(wrapper -> wrapper.isNull("finalization_next_retry_at")
                    .or().le("finalization_next_retry_at", now));
        }
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_PENDING)
                .set("finalization_attempts", attempt)
                .set("finalization_next_retry_at", processingLeaseUntil)
                .set("finalization_last_error", null)
                .set("gmt_modified", now);

        if (isOrderStatus(currentStatus, OrderConstant.PICK_UP_PASSENGER)) {
            Instant traceEnd = Instant.now(clock);
            traceEndEpochMs = traceEnd.toEpochMilli();
            passengerGetoffTime = LocalDateTime.ofInstant(traceEnd, ZoneId.systemDefault());
            passengerGetoffLongitude = orderRequest.getPassengerGetoffLongitude();
            passengerGetoffLatitude = orderRequest.getPassengerGetoffLatitude();
            updateWrapper.set("passenger_getoff_time", passengerGetoffTime)
                    .set("passenger_getoff_longitude", passengerGetoffLongitude)
                    .set("passenger_getoff_latitude", passengerGetoffLatitude)
                    .set("finalization_trace_end_epoch_ms", traceEndEpochMs);
        }
        preserveOrderCreationTime(updateWrapper);

        int claimed = orderInfoMapper.update(null, updateWrapper);
        if (claimed == 0) {
            log.warn("Order finalization claim rejected; orderId={}, fromStatus={}, toStatus={}, result=conflict",
                    orderInfo.getId(), currentStatus, OrderConstant.FINALIZATION_PENDING);
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }
        log.info("Order status changed; action=finalization_claim, orderId={}, fromStatus={}, toStatus={}, attempt={}, result=success",
                orderInfo.getId(), currentStatus, OrderConstant.FINALIZATION_PENDING, attempt);

        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        orderInfo.setFinalizationAttempts(attempt);
        orderInfo.setFinalizationNextRetryAt(processingLeaseUntil);
        orderInfo.setFinalizationLastError(null);
        orderInfo.setGmtModified(now);
        orderInfo.setPassengerGetoffTime(passengerGetoffTime);
        orderInfo.setPassengerGetoffLongitude(passengerGetoffLongitude);
        orderInfo.setPassengerGetoffLatitude(passengerGetoffLatitude);
        orderInfo.setFinalizationTraceEndEpochMs(traceEndEpochMs);

        return finalizeClaimedOrder(orderInfo, attempt);
    }

    private ResponseResult finalizeClaimedOrder(OrderInfo orderInfo, int attempt) {
        ResponseResult<Car> carById = null;
        if (orderInfo.getCarId() != null) {
            try {
                carById = finalizationDriverUserClient.getCarById(orderInfo.getCarId());
            } catch (RuntimeException e) {
                logFinalizationDependencyException(orderInfo.getId(), attempt, "getCarById", e);
                return handleFinalizationFailure(orderInfo, attempt, downstreamResponseError());
            }
        }
        ResponseResult carFailure = validateCarLookup(carById);
        if (carFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, carFailure);
        }

        Car car = carById.getData();
        ResponseResult tracePrerequisiteFailure = validateTracePrerequisites(orderInfo, car);
        if (tracePrerequisiteFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, tracePrerequisiteFailure);
        }

        Long starttime = orderInfo.getPickUpPassengerTime()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        Long endtime = orderInfo.getFinalizationTraceEndEpochMs();

        ResponseResult<TrsearchResponse> trsearch;
        try {
            if (localSimulation) {
                PredictPriceDTO route = new PredictPriceDTO();
                route.setDepLongitude(orderInfo.getDepLongitude());
                route.setDepLatitude(orderInfo.getDepLatitude());
                route.setDestLongitude(orderInfo.getDestLongitude());
                route.setDestLatitude(orderInfo.getDestLatitude());
                trsearch = finalizationMapClient.simulatedTrip(route);
            } else {
                trsearch = finalizationMapClient.trsearch(car.getTid(), starttime, endtime);
            }
        } catch (RuntimeException e) {
            logFinalizationDependencyException(orderInfo.getId(), attempt, "trsearch", e);
            return handleFinalizationFailure(orderInfo, attempt, downstreamResponseError());
        }
        ResponseResult trackFailure = validateTrackLookup(trsearch);
        if (trackFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, trackFailure);
        }
        TrsearchResponse data = trsearch.getData();
        Long driveMile = data.getDriveMile();
        Long driveDurationSeconds = data.getDriveTime();
        int pricingDistance = Math.toIntExact(driveMile);
        int pricingDuration = Math.toIntExact(driveDurationSeconds);

        // get the actual price
        String address = orderInfo.getAddress();
        String vehicleType = orderInfo.getVehicleType();
        ResponseResult<Double> doubleResponseResult;
        try {
            doubleResponseResult = finalizationPriceClient.calculatePrice(pricingDistance, pricingDuration, address, vehicleType);
        } catch (RuntimeException e) {
            logFinalizationDependencyException(orderInfo.getId(), attempt, "calculatePrice", e);
            return handleFinalizationFailure(orderInfo, attempt, downstreamResponseError());
        }
        ResponseResult priceFailure = validatePriceLookup(doubleResponseResult);
        if (priceFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, priceFailure);
        }
        Double price = doubleResponseResult.getData();
        LocalDateTime completionTime = LocalDateTime.now(clock);

        UpdateWrapper<OrderInfo> updateWrapper = finalizationTerminalUpdateWrapper(orderInfo, attempt);
        updateWrapper.set("order_status", OrderConstant.PASSENGER_GETOFF)
                .set("drive_mile", driveMile)
                .set("drive_time", driveDurationSeconds)
                .set("price", price)
                .set("finalization_next_retry_at", null)
                .set("finalization_last_error", null)
                .set("gmt_modified", completionTime);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return handleFinalizationTerminalCasMiss(orderInfo.getId());
        }
        log.info("Order status changed; action=finalization_complete, orderId={}, fromStatus={}, toStatus={}, attempt={}, result=success",
                orderInfo.getId(), OrderConstant.FINALIZATION_PENDING,
                OrderConstant.PASSENGER_GETOFF, attempt);
        return ResponseResult.success();
    }

    private ResponseResult handleFinalizationFailure(OrderInfo orderInfo, int attempt, ResponseResult failure) {
        LocalDateTime failureTime = LocalDateTime.now(clock);
        ResponseResult canonicalFailure = canonicalFinalizationFailure(failure);
        String safeError = safeFinalizationError(canonicalFailure);

        if (attempt >= maxFinalizationAttempts) {
            UpdateWrapper<OrderInfo> updateWrapper = finalizationTerminalUpdateWrapper(orderInfo, attempt);
            updateWrapper.set("order_status", OrderConstant.FINALIZATION_FAILED)
                    .set("finalization_next_retry_at", null)
                    .set("finalization_last_error", safeError)
                    .set("gmt_modified", failureTime);
            int updated = orderInfoMapper.update(null, updateWrapper);
            if (updated == 0) {
                return handleFinalizationTerminalCasMiss(orderInfo.getId());
            }
            log.warn("Order finalization reached retry limit; orderId={}, attempt={}, responseCode={}",
                    orderInfo.getId(), attempt, canonicalFailure.getCode());
            log.warn("Order status changed; action=finalization_failed, orderId={}, fromStatus={}, toStatus={}, attempt={}, result=failed",
                    orderInfo.getId(), OrderConstant.FINALIZATION_PENDING,
                    OrderConstant.FINALIZATION_FAILED, attempt);
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }

        LocalDateTime nextRetryAt = failureTime.plusSeconds(finalizationBackoffSeconds(attempt));
        UpdateWrapper<OrderInfo> updateWrapper = finalizationTerminalUpdateWrapper(orderInfo, attempt);
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_PENDING)
                .set("finalization_next_retry_at", nextRetryAt)
                .set("finalization_last_error", safeError)
                .set("gmt_modified", failureTime);
        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return handleFinalizationTerminalCasMiss(orderInfo.getId());
        }
        log.warn("Order finalization scheduled for retry; orderId={}, attempt={}, responseCode={}, nextRetryAt={}",
                orderInfo.getId(), attempt, canonicalFailure.getCode(), nextRetryAt);
        log.info("Order status changed; action=finalization_retry, orderId={}, fromStatus={}, toStatus={}, attempt={}, result=retry_scheduled",
                orderInfo.getId(), OrderConstant.FINALIZATION_PENDING,
                OrderConstant.FINALIZATION_PENDING, attempt);
        return ResponseResult.fail(canonicalFailure.getCode(), canonicalFailure.getMessage());
    }

    private ResponseResult moveExpiredFinalizationToFailed(OrderInfo orderInfo, int currentAttempts, LocalDateTime now) {
        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderInfo.getId())
                .eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .eq("finalization_attempts", currentAttempts)
                .and(wrapper -> wrapper.isNull("finalization_next_retry_at")
                        .or().le("finalization_next_retry_at", now));
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_FAILED)
                .set("finalization_next_retry_at", null)
                .set("finalization_last_error", safeFinalizationError(ResponseResult.fail(
                        CommonStatus.FINALIZATION_FAILED.getCode(),
                        CommonStatus.FINALIZATION_FAILED.getMessage())))
                .set("gmt_modified", now);
        preserveOrderCreationTime(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }
        return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                CommonStatus.FINALIZATION_FAILED.getMessage());
    }

    private UpdateWrapper<OrderInfo> finalizationTerminalUpdateWrapper(OrderInfo orderInfo, int attempt) {
        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderInfo.getId())
                .eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .eq("finalization_attempts", attempt);
        preserveOrderCreationTime(updateWrapper);
        return updateWrapper;
    }

    private ResponseResult handleFinalizationTerminalCasMiss(Long orderId) {
        OrderInfo latest = selectOrderById(orderId);
        if (latest != null) {
            Integer latestStatus = latest.getOrderStatus();
            if (isFinalizationIdempotentSuccess(latestStatus)) {
                return ResponseResult.success();
            }
            if (isOrderStatus(latestStatus, OrderConstant.FINALIZATION_FAILED)) {
                return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                        CommonStatus.FINALIZATION_FAILED.getMessage());
            }
        }
        return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
    }

    private ResponseResult handleFailedFinalizationRecoveryCasMiss(Long orderId) {
        OrderInfo latest = selectOrderById(orderId);
        if (latest == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        Integer latestStatus = latest.getOrderStatus();
        if (isFinalizationIdempotentSuccess(latestStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(latestStatus, OrderConstant.FINALIZATION_PENDING)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage());
        }
        if (isOrderStatus(latestStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }
        return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
    }

    private void logFinalizationDependencyException(Long orderId, int attempt, String dependencyName, RuntimeException e) {
        log.warn("Order finalization dependency failed; orderId={}, attempt={}, dependency={}, exceptionType={}",
                orderId, attempt, dependencyName, e.getClass().getSimpleName());
    }

    private OrderInfo selectOrderById(Long orderId) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",orderId);
        return orderInfoMapper.selectOne(queryWrapper);
    }

    private boolean isFinalizationIdempotentSuccess(Integer orderStatus) {
        return isOrderStatus(orderStatus, OrderConstant.PASSENGER_GETOFF)
                || isOrderStatus(orderStatus, OrderConstant.TO_START_PAY)
                || isOrderStatus(orderStatus, OrderConstant.SUCCESS_PAY);
    }

    private boolean isFinalizationEligible(Integer orderStatus) {
        return isOrderStatus(orderStatus, OrderConstant.PICK_UP_PASSENGER)
                || isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING);
    }

    private boolean isOrderStatus(Integer actualStatus, int expectedStatus) {
        return actualStatus != null && actualStatus == expectedStatus;
    }

    private boolean isFinalizationOwnedStatus(Integer orderStatus) {
        return isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)
                || isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED);
    }

    private boolean isFinalizationDue(OrderInfo orderInfo, LocalDateTime now) {
        LocalDateTime nextRetryAt = orderInfo.getFinalizationNextRetryAt();
        return nextRetryAt == null || !nextRetryAt.isAfter(now);
    }

    private int currentFinalizationAttempts(OrderInfo orderInfo) {
        Integer finalizationAttempts = orderInfo.getFinalizationAttempts();
        if (finalizationAttempts == null) {
            return 0;
        }
        return finalizationAttempts;
    }

    private long finalizationBackoffSeconds(int attempt) {
        long delay = finalizationBaseRetryDelaySeconds;
        if (attempt <= 1) {
            return Math.min(delay, finalizationMaxRetryDelaySeconds);
        }
        for (int i = 1; i < attempt; i++) {
            if (delay >= finalizationMaxRetryDelaySeconds
                    || delay > finalizationMaxRetryDelaySeconds / 2) {
                return finalizationMaxRetryDelaySeconds;
            }
            delay = delay * 2;
        }
        return Math.min(delay, finalizationMaxRetryDelaySeconds);
    }

    void validateFinalizationPolicy(
            int maxAttempts,
            long baseRetryDelaySeconds,
            long maxRetryDelaySeconds,
            long processingLeaseSeconds,
            long leaseSafetyMarginMs,
            int driverConnectTimeoutMs,
            int driverReadTimeoutMs,
            int mapConnectTimeoutMs,
            int mapReadTimeoutMs,
            int priceConnectTimeoutMs,
            int priceReadTimeoutMs) {

        if (maxAttempts < 1 || maxAttempts > 100) {
            throw invalidFinalizationPolicy();
        }
        if (baseRetryDelaySeconds <= 0
                || maxRetryDelaySeconds <= 0
                || processingLeaseSeconds <= 0
                || leaseSafetyMarginMs <= 0) {
            throw invalidFinalizationPolicy();
        }
        if (baseRetryDelaySeconds > maxRetryDelaySeconds) {
            throw invalidFinalizationPolicy();
        }
        if (driverConnectTimeoutMs <= 0
                || driverReadTimeoutMs <= 0
                || mapConnectTimeoutMs <= 0
                || mapReadTimeoutMs <= 0
                || priceConnectTimeoutMs <= 0
                || priceReadTimeoutMs <= 0) {
            throw invalidFinalizationPolicy();
        }

        long processingLeaseMillis = secondsToMillis(processingLeaseSeconds);
        long remoteBudgetMillis = checkedAdd(
                checkedAdd(
                        checkedAdd(driverConnectTimeoutMs, driverReadTimeoutMs),
                        checkedAdd(mapConnectTimeoutMs, mapReadTimeoutMs)),
                checkedAdd(priceConnectTimeoutMs, priceReadTimeoutMs));
        long requiredLeaseMillis = checkedAdd(remoteBudgetMillis, leaseSafetyMarginMs);
        if (processingLeaseMillis <= requiredLeaseMillis) {
            throw invalidFinalizationPolicy();
        }
    }

    private long secondsToMillis(long seconds) {
        try {
            return Math.multiplyExact(seconds, TimeUnit.SECONDS.toMillis(1));
        } catch (ArithmeticException e) {
            throw invalidFinalizationPolicy();
        }
    }

    private long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw invalidFinalizationPolicy();
        }
    }

    private IllegalStateException invalidFinalizationPolicy() {
        return new IllegalStateException("Invalid order finalization policy configuration");
    }

    private ResponseResult validateCarLookup(ResponseResult<Car> carById) {
        if (carById == null) {
            return downstreamResponseError();
        }
        if (carById.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(carById.getCode(), carById.getMessage());
        }
        Car car = carById.getData();
        if (car == null || (!localSimulation && (car.getTid() == null || car.getTid().trim().isEmpty()))) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult validateTracePrerequisites(OrderInfo orderInfo, Car car) {
        if (car == null || (!localSimulation && (car.getTid() == null || car.getTid().trim().isEmpty()))) {
            return downstreamResponseError();
        }
        if (orderInfo.getPickUpPassengerTime() == null || orderInfo.getFinalizationTraceEndEpochMs() == null) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult validateTrackLookup(ResponseResult<TrsearchResponse> trsearch) {
        if (trsearch == null) {
            return downstreamResponseError();
        }
        if (trsearch.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(trsearch.getCode(), trsearch.getMessage());
        }
        TrsearchResponse data = trsearch.getData();
        if (data == null) {
            return downstreamResponseError();
        }
        if (data.getDriveMile() == null || data.getDriveTime() == null) {
            return downstreamResponseError();
        }
        Long driveMile = data.getDriveMile();
        Long driveTime = data.getDriveTime();
        if (driveMile < 0 || driveTime < 0) {
            return downstreamResponseError();
        }
        if (driveMile > Integer.MAX_VALUE || driveTime > Integer.MAX_VALUE) {
            return downstreamResponseError();
        }
        if (driveMile == 0L && driveTime == 0L) {
            return ResponseResult.fail(CommonStatus.MAP_TRACK_EMPTY.getCode(),
                    CommonStatus.MAP_TRACK_EMPTY.getMessage());
        }
        return null;
    }

    private ResponseResult validatePriceLookup(ResponseResult<Double> priceResponse) {
        if (priceResponse == null) {
            return downstreamResponseError();
        }
        if (priceResponse.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(priceResponse.getCode(), priceResponse.getMessage());
        }
        Double price = priceResponse.getData();
        if (price == null || price.isNaN() || price.isInfinite() || price < 0) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult downstreamResponseError() {
        return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
    }

    private ResponseResult canonicalFinalizationFailure(ResponseResult failure) {
        if (failure == null) {
            return downstreamResponseError();
        }
        CommonStatus knownStatus = findCommonStatus(failure.getCode());
        if (knownStatus == null) {
            return downstreamResponseError();
        }
        return ResponseResult.fail(knownStatus.getCode(), knownStatus.getMessage());
    }

    private String safeFinalizationError(ResponseResult failure) {
        int code = failure.getCode();
        String message = CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage();
        CommonStatus knownStatus = findCommonStatus(code);
        if (knownStatus != null) {
            message = knownStatus.getMessage();
        }
        String error = code + ":" + message;
        if (error.length() > 255) {
            return error.substring(0, 255);
        }
        return error;
    }

    private CommonStatus findCommonStatus(int code) {
        for (CommonStatus status : CommonStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }

    private ResponseResult transitionLegacyOrderState(
            String action,
            Long orderId,
            int expectedStatus,
            int targetStatus,
            Consumer<UpdateWrapper<OrderInfo>> updateCustomizer) {

        OrderInfo currentOrder = orderInfoMapper.selectById(orderId);
        ResponseResult preflight = resolveLegacyTransitionRead(currentOrder, expectedStatus, targetStatus);
        if (preflight != null) {
            if (preflight.getCode() == CommonStatus.SUCCESS.getCode()) {
                log.info("Order status transition already applied; action={}, orderId={}, status={}, result=idempotent",
                        action, orderId, targetStatus);
                return preflight;
            }
            log.warn("Order status transition rejected; action={}, orderId={}, fromStatus={}, toStatus={}, resultCode={}",
                    action, orderId, currentOrder == null ? null : currentOrder.getOrderStatus(),
                    targetStatus, preflight.getCode());
            return preflight;
        }

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                .eq("order_status", expectedStatus)
                .set("order_status", targetStatus);
        updateCustomizer.accept(updateWrapper);
        preserveLegacyOrderAuditTimes(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 1) {
            log.info("Order status changed; action={}, orderId={}, fromStatus={}, toStatus={}, result=success",
                    action, orderId, expectedStatus, targetStatus);
            return ResponseResult.success();
        }
        OrderInfo latestOrder = orderInfoMapper.selectById(orderId);
        ResponseResult rereadResult = resolveLegacyTransitionRead(latestOrder, expectedStatus, targetStatus);
        if (rereadResult != null) {
            log.warn("Order status transition lost race; action={}, orderId={}, expectedStatus={}, targetStatus={}, actualStatus={}, resultCode={}",
                    action, orderId, expectedStatus, targetStatus,
                    latestOrder == null ? null : latestOrder.getOrderStatus(), rereadResult.getCode());
            return rereadResult;
        }
        log.warn("Order status transition failed; action={}, orderId={}, expectedStatus={}, targetStatus={}, actualStatus={}",
                action, orderId, expectedStatus, targetStatus,
                latestOrder == null ? null : latestOrder.getOrderStatus());
        return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getMessage());
    }

    private ResponseResult resolveLegacyTransitionRead(OrderInfo orderInfo, int expectedStatus, int targetStatus) {
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        Integer currentStatus = orderInfo.getOrderStatus();
        if (isOrderStatus(currentStatus, targetStatus)) {
            return ResponseResult.success();
        }
        if (isFinalizationOwnedStatus(currentStatus)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }
        if (!isOrderStatus(currentStatus, expectedStatus)) {
            return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getMessage());
        }
        return null;
    }

    private void preserveOrderCreationTime(UpdateWrapper<OrderInfo> updateWrapper) {
        updateWrapper.setSql(PRESERVE_GMT_CREATE_SQL);
    }

    private void preserveLegacyOrderAuditTimes(UpdateWrapper<OrderInfo> updateWrapper) {
        updateWrapper.setSql(PRESERVE_GMT_CREATE_SQL);
        updateWrapper.setSql(PRESERVE_GMT_MODIFIED_SQL);
    }

    /**
     * This method is used to process the status of a passenger when they pay for an order
     * @param orderId
     * @param passengerPhone authenticated passenger phone from the signed token
     * @return
     */
    public ResponseResult pay(Long orderId, String passengerPhone){
        if (orderId == null || passengerPhone == null || passengerPhone.isBlank()) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (!isPassengerOrder(orderInfo, passengerPhone)) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        int currentStatus = orderInfo.getOrderStatus();
        if (isOrderStatus(currentStatus, OrderConstant.SUCCESS_PAY)) {
            return ResponseResult.success();
        }
        if (isFinalizationOwnedStatus(currentStatus)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }
        if (!isOrderStatus(currentStatus, OrderConstant.PASSENGER_GETOFF)
                && !isOrderStatus(currentStatus, OrderConstant.TO_START_PAY)) {
            return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getMessage());
        }

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                .eq("passenger_phone", passengerPhone)
                .eq("order_status", currentStatus)
                .set("order_status", OrderConstant.SUCCESS_PAY);
        preserveLegacyOrderAuditTimes(updateWrapper);

        if (orderInfoMapper.update(null, updateWrapper) == 1) {
            log.info("Order status changed; action=payment, orderId={}, fromStatus={}, toStatus={}, result=success",
                    orderId, currentStatus, OrderConstant.SUCCESS_PAY);
            return ResponseResult.success();
        }

        OrderInfo latestOrder = orderInfoMapper.selectById(orderId);
        if (!isPassengerOrder(latestOrder, passengerPhone)) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        if (isOrderStatus(latestOrder.getOrderStatus(), OrderConstant.SUCCESS_PAY)) {
            return ResponseResult.success();
        }
        if (isFinalizationOwnedStatus(latestOrder.getOrderStatus())) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }
        return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getMessage());
    }

    /**
     * This method is used to handle the status of an order when it is canceled
     * @param orderId
     * @param identity  Identity: 1: passenger, 2: driver
     * @return
     */
    public ResponseResult cancel(Long orderId, String identity){
        return cancel(orderId, identity, null);
    }

    public ResponseResult cancel(Long orderId, String identity, String passengerPhone){
        if (orderId == null || identity == null || identity.isBlank()) {
            return ResponseResult.fail(CommonStatus.ORDER_CANCEL_ERROR.getCode(),
                    CommonStatus.ORDER_CANCEL_ERROR.getMessage());
        }
        identity = identity.trim();
        boolean passengerCancellation = UserIdentity.PASSENGER.getIdentity().equals(identity);
        if (!passengerCancellation && !UserIdentity.DRIVER.getIdentity().equals(identity)) {
            return ResponseResult.fail(CommonStatus.ORDER_CANCEL_ERROR.getCode(),
                    CommonStatus.ORDER_CANCEL_ERROR.getMessage());
        }
        if (passengerCancellation && (passengerPhone == null || passengerPhone.isBlank())) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        // Query the current status of the order
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (passengerCancellation && !isPassengerOrder(orderInfo, passengerPhone)) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationOwnedStatus(orderStatus)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }

        LocalDateTime cancelTime = LocalDateTime.now(clock);
        Integer cancelOperator = null;
        Integer cancelTypeCode = null;

        // Normal cancellation
        int cancelType = 1;

        // Update the cancellation status of the order
        // If a passenger cancels
        if (passengerCancellation){
            switch (orderStatus){
                // At the start of the order
                case OrderConstant.ORDER_START:
                    cancelTypeCode = OrderConstant.CANCEL_PASSENGER_BEFORE;
                    break;
                // When the driver receives the order
                case OrderConstant.DRIVER_RECEIVE_ORDER:
                    LocalDateTime receiveOrderTime = orderInfo.getReceiveOrderTime();
                    long between = ChronoUnit.MINUTES.between(receiveOrderTime, cancelTime);
                    if (between > 1){
                        cancelTypeCode = OrderConstant.CANCEL_PASSENGER_ILLEGAL;
                    }else {
                        cancelTypeCode = OrderConstant.CANCEL_PASSENGER_BEFORE;
                    }
                    break;
                // When the driver goes to pick up the passenger
                case OrderConstant.DRIVER_TO_PICK_UP_PASSENGER:
                // When the driver arrives at the passenger starting point
                case OrderConstant.DRIVER_ARRIVED_DEPARTURE:
                    cancelTypeCode = OrderConstant.CANCEL_PASSENGER_ILLEGAL;
                    break;
                default:
                    cancelType = 0;
                    break;
            }
        }

        // If the driver cancels the order
        if (UserIdentity.DRIVER.getIdentity().equals(identity)){
            switch (orderStatus){
                // At the start of the order
                // When the driver picks up the passenger
                case OrderConstant.DRIVER_RECEIVE_ORDER:
                case OrderConstant.DRIVER_TO_PICK_UP_PASSENGER:
                case OrderConstant.DRIVER_ARRIVED_DEPARTURE:
                    LocalDateTime receiveOrderTime = orderInfo.getReceiveOrderTime();
                    long between = ChronoUnit.MINUTES.between(receiveOrderTime, cancelTime);
                    if (between > 1){
                        cancelTypeCode = OrderConstant.CANCEL_DRIVER_ILLEGAL;
                    }else {
                        cancelTypeCode = OrderConstant.CANCEL_DRIVER_BEFORE;
                    }
                    break;

                default:
                    cancelType = 0;
                    break;
            }
        }

        // 0 means cancel unsuccessfully
        if (cancelType == 0){
            return ResponseResult.fail(CommonStatus.ORDER_CANCEL_ERROR.getCode(), CommonStatus.ORDER_CANCEL_ERROR.getMessage());
        }

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                .eq("order_status", orderStatus)
                .set("cancel_type_code", cancelTypeCode)
                .set("cancel_time", cancelTime)
                .set("cancel_operator", Integer.parseInt(identity))
                .set("order_status", OrderConstant.ORDER_CANCEL);
        if (passengerCancellation) {
            updateWrapper.eq("passenger_phone", passengerPhone);
        }
        preserveLegacyOrderAuditTimes(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 1) {
            log.info("Order status changed; action=cancel, orderId={}, fromStatus={}, toStatus={}, actor={}, result=success",
                    orderId, orderStatus, OrderConstant.ORDER_CANCEL, identity);
            return ResponseResult.success();
        }

        OrderInfo latestOrder = orderInfoMapper.selectById(orderId);
        if (passengerCancellation && !isPassengerOrder(latestOrder, passengerPhone)) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        if (latestOrder == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        if (isFinalizationOwnedStatus(latestOrder.getOrderStatus())) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }
        return ResponseResult.fail(CommonStatus.ORDER_CANCEL_ERROR.getCode(),
                CommonStatus.ORDER_CANCEL_ERROR.getMessage());
    }

    private boolean isPassengerOrder(OrderInfo orderInfo, String passengerPhone) {
        return orderInfo != null && passengerPhone.equals(orderInfo.getPassengerPhone());
    }

    /**
     * This method is used to handle the situation when the passenger starts paying for the order
     * @param orderRequest
     * @return
     */
    public ResponseResult pushPayInfo(OrderRequest orderRequest) {

        Long orderId = orderRequest.getOrderId();
        return transitionLegacyOrderState(
                "push_pay_info",
                orderId,
                OrderConstant.PASSENGER_GETOFF,
                OrderConstant.TO_START_PAY,
                updateWrapper -> {
                });

    }


    public ResponseResult<List<OrderInfo>> driverOrders(String driverPhone) {
        if (driverPhone == null || driverPhone.isBlank()) {
            return ResponseResult.fail(0, "司机账号无效");
        }
        LocalDateTime pendingSince = LocalDateTime.now(clock)
                .minusMinutes(Math.max(1L, acceptTimeoutMinutes));
        String driverCityCode = null;
        try {
            ResponseResult<DriverCarBindingRelationship> bindingResult =
                    serviceDriverUserClient.getBindingByPhone(driverPhone);
            DriverCarBindingRelationship binding = bindingResult == null ? null : bindingResult.getData();
            if (bindingResult != null && bindingResult.getCode() == CommonStatus.SUCCESS.getCode()
                    && binding != null && binding.getCarId() != null) {
                ResponseResult<Car> carResult = serviceDriverUserClient.getCarById(binding.getCarId());
                Car car = carResult == null ? null : carResult.getData();
                if (carResult != null && carResult.getCode() == CommonStatus.SUCCESS.getCode()
                        && car != null && Integer.valueOf(0).equals(car.getState())
                        && car.getAddress() != null && !car.getAddress().isBlank()) {
                    driverCityCode = car.getAddress();
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Driver order context lookup failed; driverPhone={}, reason={}",
                    driverPhone, exception.getMessage());
        }

        final String cityCode = driverCityCode;
        QueryWrapper<OrderInfo> query = new QueryWrapper<>();
        query.and(wrapper -> {
                    wrapper.eq("driver_phone", driverPhone);
                    if (cityCode != null) {
                        wrapper.or(unassigned -> unassigned.isNull("driver_phone")
                                .eq("order_status", OrderConstant.ORDER_START)
                                .likeRight("fare_type", cityCode + "$")
                                .ge("gmt_create", pendingSince));
                    }
                })
                .orderByDesc("gmt_create", "id")
                .last("LIMIT 100");
        return ResponseResult.success(orderInfoMapper.selectList(query));
    }

    public ResponseResult acceptOrder(Long orderId, String driverPhone) {
        if (orderId == null || driverPhone == null || driverPhone.isBlank()) {
            return ResponseResult.fail(0, "请先选择订单");
        }
        String normalizedPhone = driverPhone.trim();
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(), CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        if (isOrderAssignedToDriver(order, normalizedPhone)) {
            return ResponseResult.success();
        }
        if (!Integer.valueOf(OrderConstant.ORDER_START).equals(order.getOrderStatus())
                || order.getDriverPhone() != null) {
            return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                    "订单已被其他司机接单");
        }
        LocalDateTime acceptDeadline = LocalDateTime.now(clock)
                .minusMinutes(Math.max(1L, acceptTimeoutMinutes));
        if (order.getGmtCreate() == null || order.getGmtCreate().isBefore(acceptDeadline)) {
            return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                    "订单已超过可接单时间");
        }

        ResponseResult<DriverCarBindingRelationship> bindingResult =
                serviceDriverUserClient.getBindingByPhone(normalizedPhone);
        DriverCarBindingRelationship binding = bindingResult == null ? null : bindingResult.getData();
        if (bindingResult == null || bindingResult.getCode() != CommonStatus.SUCCESS.getCode()
                || binding == null || binding.getCarId() == null) {
            return ResponseResult.fail(CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getCode(),
                    CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getMessage());
        }

        ResponseResult<OrderDriverResponse> driverResult =
                serviceDriverUserClient.getAvailableDriver(binding.getCarId());
        OrderDriverResponse driver = driverResult == null ? null : driverResult.getData();
        if (driverResult == null || driverResult.getCode() != CommonStatus.SUCCESS.getCode()
                || driver == null || !normalizedPhone.equals(driver.getDriverPhone())) {
            return ResponseResult.fail(CommonStatus.NO_AVAILABLE_DRIVER.getCode(),
                    "Driver vehicle does not match this order");
        }
        ResponseResult<Car> carResult = serviceDriverUserClient.getCarById(binding.getCarId());
        Car car = carResult == null ? null : carResult.getData();
        String orderCityCode = cityCodeFromFareType(order.getFareType());
        if (orderCityCode == null || orderCityCode.isBlank()) orderCityCode = order.getAddress();
        if (carResult == null || carResult.getCode() != CommonStatus.SUCCESS.getCode() || car == null
                || !orderCityCode.equals(car.getAddress())) {
            // 司机车辆必须与订单所在城市匹配。
            return ResponseResult.fail(CommonStatus.NO_AVAILABLE_DRIVER.getCode(),
                    "Driver vehicle does not match this order");
        }
        RLock lock = redissonClient.getLock("driver:assignment:" + driver.getDriverId());
        lock.lock();
        try {
            if (isDriverOrderGoingon(driver.getDriverId())) {
                return ResponseResult.fail(CommonStatus.NO_AVAILABLE_DRIVER.getCode(),
                        "当前司机已有进行中的订单，请先完成或取消当前订单");
            }
            UpdateWrapper<OrderInfo> update = new UpdateWrapper<>();
            update.eq("id", orderId)
                    .eq("order_status", OrderConstant.ORDER_START)
                    .isNull("driver_phone")
                    .ge("gmt_create", acceptDeadline)
                    .set("driver_id", driver.getDriverId())
                    .set("driver_phone", driver.getDriverPhone())
                    .set("car_id", driver.getCarId())
                    .set("receive_order_car_longitude", order.getDepLongitude())
                    .set("receive_order_car_latitude", order.getDepLatitude())
                    .set("receive_order_time", LocalDateTime.now(clock))
                    .set("license_id", driver.getLicenseId())
                    .set("vehicle_no", driver.getVehicleNo())
                    .set("order_status", OrderConstant.DRIVER_RECEIVE_ORDER)
                    .setSql(PRESERVE_GMT_MODIFIED_SQL);
            if (orderInfoMapper.update(null, update) == 1) {
                log.info("Order status changed; action=driver_accept, orderId={}, fromStatus={}, toStatus={}, result=success",
                        orderId, OrderConstant.ORDER_START, OrderConstant.DRIVER_RECEIVE_ORDER);
                return ResponseResult.success();
            }

            // Only reload the order when the conditional update loses the race.
            OrderInfo latestOrder = orderInfoMapper.selectById(orderId);
            if (latestOrder == null) {
                return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(), CommonStatus.ORDER_NOT_FOUND.getMessage());
            }
            if (isOrderAssignedToDriver(latestOrder, normalizedPhone)) {
                return ResponseResult.success();
            }
            return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                    "订单已被其他司机接单");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private boolean isOrderAssignedToDriver(OrderInfo order, String driverPhone) {
        if (order == null || driverPhone == null || !driverPhone.equals(order.getDriverPhone())) {
            return false;
        }
        Integer orderStatus = order.getOrderStatus();
        return isOrderStatus(orderStatus, OrderConstant.DRIVER_RECEIVE_ORDER)
                || isOrderStatus(orderStatus, OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                || isOrderStatus(orderStatus, OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                || isOrderStatus(orderStatus, OrderConstant.PICK_UP_PASSENGER)
                || isOrderStatus(orderStatus, OrderConstant.PASSENGER_GETOFF)
                || isOrderStatus(orderStatus, OrderConstant.TO_START_PAY)
                || isOrderStatus(orderStatus, OrderConstant.SUCCESS_PAY)
                || isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)
                || isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED);
    }

    public ResponseResult<OrderInfo> driverOrder(String driverPhone, Long orderId) {
        if (driverPhone == null || driverPhone.isBlank() || orderId == null) {
            return ResponseResult.fail(0, "司机账号或订单无效");
        }
        QueryWrapper<OrderInfo> query = new QueryWrapper<>();
        query.eq("driver_phone", driverPhone).eq("id", orderId);
        OrderInfo order = orderInfoMapper.selectOne(query);
        if (order == null) {
            return ResponseResult.fail(0, "订单不存在或不属于当前司机");
        }
        return ResponseResult.success(order);
    }

    /**
     * This method is used to get information about the order being made by the current user
     * @param phone
     * @param identity
     * @return
     */
    public ResponseResult<OrderInfo> current(String phone, String identity){
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();

        if (identity.equals(UserIdentity.DRIVER.getIdentity())){
            queryWrapper.eq("driver_phone",phone);

            queryWrapper.and(wrapper->wrapper
                    .eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                    .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                    .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                    .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)

            );
        }
        if (identity.equals(UserIdentity.PASSENGER.getIdentity())){
            queryWrapper.eq("passenger_phone",phone);
            queryWrapper.and(wrapper->wrapper.eq("order_status",OrderConstant.ORDER_START)
                    .or().eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                    .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                    .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                    .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)
                    .or().eq("order_status",OrderConstant.PASSENGER_GETOFF)
                    .or().eq("order_status",OrderConstant.TO_START_PAY)
                    .or().eq("order_status",OrderConstant.SUCCESS_PAY)
                    .or().eq("order_status",OrderConstant.FINALIZATION_PENDING)
                    .or().eq("order_status",OrderConstant.FINALIZATION_FAILED)
            );
        }

        queryWrapper.orderByDesc("gmt_create", "id").last("LIMIT 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        return ResponseResult.success(orderInfo);
    }
}

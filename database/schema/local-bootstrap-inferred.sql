-- Inferred local bootstrap schema for FlyTaxi.
--
-- This file is derived from the current Java DTOs, MyBatis-Plus mappers,
-- query/update wrappers, and the reviewed ORDER-04 migration. It is intended
-- for local development/demo databases, not as an audited production baseline.
-- Target: MySQL 8.0.x.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `service-passenger-user`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `service-passenger-user`;

CREATE TABLE IF NOT EXISTS passenger_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    passenger_phone VARCHAR(32) NOT NULL,
    passenger_name VARCHAR(64) NULL,
    passenger_gender TINYINT NOT NULL DEFAULT 0,
    state TINYINT NOT NULL DEFAULT 0,
    profile_photo VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_passenger_user_phone (passenger_phone),
    KEY idx_passenger_user_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE DATABASE IF NOT EXISTS `service-driver-user`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `service-driver-user`;

CREATE TABLE IF NOT EXISTS driver_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(32) NULL,
    driver_name VARCHAR(64) NULL,
    driver_phone VARCHAR(32) NOT NULL,
    driver_gender INT NULL,
    driver_birthday DATE NULL,
    driver_nation VARCHAR(64) NULL,
    driver_contact_address VARCHAR(255) NULL,
    license_id VARCHAR(64) NULL,
    get_driver_license_date DATE NULL,
    driver_license_on DATE NULL,
    driver_license_off DATE NULL,
    taxi_driver INT NULL,
    certificate_no VARCHAR(64) NULL,
    network_car_issue_organization VARCHAR(128) NULL,
    network_car_issue_date DATE NULL,
    get_network_car_proof_date DATE NULL,
    network_car_proof_on DATE NULL,
    network_car_proof_off DATE NULL,
    register_date DATE NULL,
    commercial_type INT NULL,
    contract_company VARCHAR(128) NULL,
    contract_on DATE NULL,
    contract_off DATE NULL,
    state INT NOT NULL DEFAULT 0,
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_driver_user_phone (driver_phone),
    KEY idx_driver_user_state (state),
    KEY idx_driver_user_address_state (address, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS car (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(32) NULL,
    vehicle_no VARCHAR(32) NOT NULL,
    plate_color VARCHAR(16) NULL,
    seats INT NULL,
    brand VARCHAR(64) NULL,
    model VARCHAR(64) NULL,
    vehicle_type VARCHAR(32) NULL,
    owner_name VARCHAR(64) NULL,
    vehicle_color VARCHAR(32) NULL,
    engine_id VARCHAR(64) NULL,
    vin VARCHAR(64) NULL,
    certify_date_a DATE NULL,
    fuel_type VARCHAR(16) NULL,
    engine_displace VARCHAR(32) NULL,
    trans_agency VARCHAR(128) NULL,
    trans_area VARCHAR(128) NULL,
    trans_date_start DATE NULL,
    trans_date_end DATE NULL,
    certify_date_b DATE NULL,
    fix_state VARCHAR(16) NULL,
    next_fix_date DATE NULL,
    check_state VARCHAR(16) NULL,
    fee_print_id VARCHAR(64) NULL,
    gps_brand VARCHAR(64) NULL,
    gps_model VARCHAR(64) NULL,
    gps_install_date DATE NULL,
    register_date DATE NULL,
    commercial_type INT NULL,
    fare_type VARCHAR(64) NULL,
    state INT NOT NULL DEFAULT 0,
    tid VARCHAR(128) NULL,
    trid VARCHAR(128) NULL,
    trname VARCHAR(128) NULL,
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_car_vehicle_no (vehicle_no),
    KEY idx_car_address_state (address, state),
    KEY idx_car_vehicle_type (vehicle_type),
    KEY idx_car_tid (tid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS driver_car_binding_relationship (
    id BIGINT NOT NULL AUTO_INCREMENT,
    driver_id BIGINT NOT NULL,
    car_id BIGINT NOT NULL,
    bind_state INT NOT NULL DEFAULT 1,
    binding_time DATETIME NULL,
    un_binding_time DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_driver_car_bind_driver_state (driver_id, bind_state),
    KEY idx_driver_car_bind_car_state (car_id, bind_state),
    KEY idx_driver_car_bind_pair_state (driver_id, car_id, bind_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS driver_user_work_status (
    id BIGINT NOT NULL AUTO_INCREMENT,
    driver_id BIGINT NOT NULL,
    work_status INT NOT NULL DEFAULT 0,
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_driver_user_work_status_driver (driver_id),
    KEY idx_driver_user_work_status_status (work_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP VIEW IF EXISTS v_city_driver_work_status;
CREATE VIEW v_city_driver_work_status AS
SELECT
    c.address AS city_code,
    d.id AS driver_id,
    c.id AS car_id,
    ws.work_status AS work_status,
    r.bind_state AS bind_state,
    d.state AS driver_state,
    c.state AS car_state
FROM driver_user d
JOIN driver_user_work_status ws ON ws.driver_id = d.id
JOIN driver_car_binding_relationship r ON r.driver_id = d.id
JOIN car c ON c.id = r.car_id
WHERE ws.work_status = 1
  AND r.bind_state = 1
  AND d.state = 0
  AND c.state = 0;


CREATE DATABASE IF NOT EXISTS `service-price`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `service-price`;

CREATE TABLE IF NOT EXISTS price_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    city_code VARCHAR(32) NOT NULL,
    vehicle_type VARCHAR(32) NOT NULL,
    start_fare DECIMAL(10, 2) NOT NULL,
    start_mile INT NOT NULL,
    unit_price_per_mile DECIMAL(10, 2) NOT NULL,
    unit_price_per_minute DECIMAL(10, 2) NOT NULL,
    fare_type VARCHAR(64) NOT NULL,
    fare_version INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_price_rule_fare_version (fare_type, fare_version),
    KEY idx_price_rule_city_vehicle_version (city_code, vehicle_type, fare_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO price_rule
    (city_code, vehicle_type, start_fare, start_mile, unit_price_per_mile,
     unit_price_per_minute, fare_type, fare_version)
SELECT '110000', '1', 13.00, 3, 2.30, 0.50, '110000$1', 1
WHERE NOT EXISTS (SELECT 1 FROM price_rule WHERE fare_type = '110000$1');

INSERT INTO price_rule
    (city_code, vehicle_type, start_fare, start_mile, unit_price_per_mile,
     unit_price_per_minute, fare_type, fare_version)
SELECT '110000', '2', 18.00, 3, 3.20, 0.70, '110000$2', 1
WHERE NOT EXISTS (SELECT 1 FROM price_rule WHERE fare_type = '110000$2');

INSERT INTO price_rule
    (city_code, vehicle_type, start_fare, start_mile, unit_price_per_mile,
     unit_price_per_minute, fare_type, fare_version)
SELECT '110000', '3', 26.00, 3, 4.50, 0.90, '110000$3', 1
WHERE NOT EXISTS (SELECT 1 FROM price_rule WHERE fare_type = '110000$3');


CREATE DATABASE IF NOT EXISTS `service-map`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `service-map`;

CREATE TABLE IF NOT EXISTS dic_district (
    address_code VARCHAR(32) NOT NULL,
    address_name VARCHAR(128) NOT NULL,
    parent_address_code VARCHAR(32) NOT NULL,
    level INT NOT NULL,
    PRIMARY KEY (address_code),
    KEY idx_dic_district_parent (parent_address_code),
    KEY idx_dic_district_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE DATABASE IF NOT EXISTS `service-order`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `service-order`;

CREATE TABLE IF NOT EXISTS order_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    passenger_id BIGINT NULL,
    idempotency_key VARCHAR(64) NULL,
    request_fingerprint CHAR(64) NULL,
    passenger_phone VARCHAR(32) NULL,
    driver_id BIGINT NULL,
    driver_phone VARCHAR(32) NULL,
    car_id BIGINT NULL,
    vehicle_type VARCHAR(32) NULL,
    address VARCHAR(32) NULL,
    order_time DATETIME NULL,
    depart_time DATETIME NULL,
    departure VARCHAR(255) NULL,
    dep_longitude VARCHAR(32) NULL,
    dep_latitude VARCHAR(32) NULL,
    destination VARCHAR(255) NULL,
    dest_longitude VARCHAR(32) NULL,
    dest_latitude VARCHAR(32) NULL,
    encrypt INT NULL,
    fare_type VARCHAR(64) NULL,
    fare_version INT NULL,
    receive_order_car_longitude VARCHAR(32) NULL,
    receive_order_car_latitude VARCHAR(32) NULL,
    receive_order_time DATETIME NULL,
    license_id VARCHAR(64) NULL,
    vehicle_no VARCHAR(32) NULL,
    to_pick_up_passenger_time DATETIME NULL,
    to_pick_up_passenger_longitude VARCHAR(32) NULL,
    to_pick_up_passenger_latitude VARCHAR(32) NULL,
    to_pick_up_passenger_address VARCHAR(255) NULL,
    driver_arrived_departure_time DATETIME NULL,
    pick_up_passenger_time DATETIME NULL,
    pick_up_passenger_longitude VARCHAR(32) NULL,
    pick_up_passenger_latitude VARCHAR(32) NULL,
    passenger_getoff_time DATETIME NULL,
    passenger_getoff_longitude VARCHAR(32) NULL,
    passenger_getoff_latitude VARCHAR(32) NULL,
    cancel_time DATETIME NULL,
    cancel_operator INT NULL,
    cancel_type_code INT NULL,
    drive_mile BIGINT NULL,
    drive_time BIGINT NULL,
    order_status INT NOT NULL DEFAULT 1,
    price DECIMAL(10, 2) NULL,
    finalization_attempts INT NOT NULL DEFAULT 0,
    finalization_next_retry_at DATETIME NULL,
    finalization_last_error VARCHAR(255) NULL,
    finalization_trace_end_epoch_ms BIGINT NULL,
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_passenger_status (passenger_id, order_status),
    UNIQUE KEY uk_order_passenger_idempotency (passenger_id, idempotency_key),
    KEY idx_order_driver_status (driver_id, order_status),
    KEY idx_order_passenger_phone_status (passenger_phone, order_status),
    KEY idx_order_driver_phone_status (driver_phone, order_status),
    KEY idx_order_status (order_status),
    KEY idx_order_finalization_due (order_status, finalization_next_retry_at, finalization_attempts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- Change ID: ORDER-05
-- Title: Add passenger order-create idempotency
-- Owner: service-order
-- Target database: service-order
-- Target MySQL version: MySQL 8.0.30; reconfirm before execution.
-- Paired verification: database/verification/ORDER-05__order-create-idempotency.sql
-- Paired rollback: database/rollback/ORDER-05__order-create-idempotency.sql
--
-- Run only after the read-only preflight assertions pass and the application
-- rollout is ready to require idempotency keys. This migration does not backfill
-- historical orders; their idempotency fields remain NULL.

ALTER TABLE order_info
    ADD COLUMN idempotency_key VARCHAR(64) NULL,
    ADD COLUMN request_fingerprint CHAR(64) NULL,
    ADD UNIQUE INDEX uk_order_passenger_idempotency (passenger_id, idempotency_key);

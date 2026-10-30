-- Change ID: ORDER-05
-- Title: Roll back passenger order-create idempotency
-- Owner: service-order
-- Forward migration: database/migrations/ORDER-05__order-create-idempotency.sql
-- Verification: database/verification/ORDER-05__order-create-idempotency.sql
-- Reversibility: PARTIAL; dropping these columns discards keys used to deduplicate
-- retries, so requests retried after rollback may create duplicate orders.
--
-- PRECONDITIONS
-- Stop order creation and deploy code that no longer reads or writes these fields.
-- Run the verification PRE-ROLLBACK ASSERTION and preserve a database backup.
-- Resolve or expire all outstanding client retries before proceeding.
--
-- ROLLBACK

ALTER TABLE order_info
    DROP INDEX uk_order_passenger_idempotency,
    DROP COLUMN request_fingerprint,
    DROP COLUMN idempotency_key;

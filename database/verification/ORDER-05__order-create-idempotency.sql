-- Change ID: ORDER-05
-- Title: Verify passenger order-create idempotency
-- Owner: service-order
-- Target database: service-order
-- Forward migration: database/migrations/ORDER-05__order-create-idempotency.sql
-- Rollback: database/rollback/ORDER-05__order-create-idempotency.sql
--
-- Read-only checks. Record the environment, operator, timestamp, and results.

-- PRE-FLIGHT ASSERTION
-- Expected: one row; stop if order_info is absent.
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'order_info';

-- PRE-FLIGHT ASSERTION
-- Expected: zero rows; stop if this change was partially applied already.
SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN ('idempotency_key', 'request_fingerprint');

-- PRE-FLIGHT ASSERTION
-- Expected: zero rows; stop if the unique index already exists.
SELECT index_name, column_name, seq_in_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND index_name = 'uk_order_passenger_idempotency'
ORDER BY seq_in_index;

-- PRE-FLIGHT INVENTORY
-- Review row volume and status distribution before the ALTER TABLE operation.
SELECT order_status, COUNT(*) AS row_count
FROM order_info
GROUP BY order_status
ORDER BY order_status;

-- POST-MIGRATION ASSERTION
-- Expected: exactly two rows, one for each new column.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN ('idempotency_key', 'request_fingerprint')
ORDER BY column_name;

-- POST-MIGRATION ASSERTION
-- Expected: exactly two index rows in this column order.
SELECT index_name, column_name, seq_in_index, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND index_name = 'uk_order_passenger_idempotency'
ORDER BY seq_in_index;

-- POST-MIGRATION INVENTORY
-- Expected: historical rows have NULL keys and are excluded from uniqueness checks.
SELECT COUNT(*) AS historical_rows_without_idempotency_key
FROM order_info
WHERE idempotency_key IS NULL AND request_fingerprint IS NULL;

-- PRE-ROLLBACK ASSERTION
-- Expected: zero after create traffic is stopped and pending retries are resolved.
-- A non-zero count means rollback will discard active retry-deduplication records.
SELECT COUNT(*) AS orders_with_idempotency_metadata
FROM order_info
WHERE idempotency_key IS NOT NULL OR request_fingerprint IS NOT NULL;

-- POST-ROLLBACK ASSERTION
-- Expected: zero rows; the new columns and index should be absent.
SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN ('idempotency_key', 'request_fingerprint');

SELECT index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND index_name = 'uk_order_passenger_idempotency';

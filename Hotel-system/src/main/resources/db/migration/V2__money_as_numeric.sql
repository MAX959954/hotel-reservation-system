-- Money was stored as double precision (binary floating point), which cannot represent
-- most decimal amounts exactly (0.1 + 0.2 != 0.3). numeric(12,2) stores exact cents;
-- the Java side maps these columns to BigDecimal. Existing values are rounded to cents.

ALTER TABLE rooms
    ALTER COLUMN price_per_night TYPE numeric(12, 2) USING round(price_per_night::numeric, 2);

ALTER TABLE bookings
    ALTER COLUMN total_price TYPE numeric(12, 2) USING round(total_price::numeric, 2);

ALTER TABLE payments
    ALTER COLUMN amount TYPE numeric(12, 2) USING round(amount::numeric, 2),
    ALTER COLUMN refunded_amount DROP DEFAULT,
    ALTER COLUMN refunded_amount TYPE numeric(12, 2) USING round(refunded_amount::numeric, 2),
    ALTER COLUMN refunded_amount SET DEFAULT 0;

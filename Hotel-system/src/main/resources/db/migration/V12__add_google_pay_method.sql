ALTER TABLE payments DROP CONSTRAINT payments_method_check;

ALTER TABLE payments ADD CONSTRAINT payments_method_check
    CHECK (method::text = ANY (ARRAY['CREDIT_CARD','DEBIT_CARD','BANK_TRANSFER','CASH','CRYPTO','GOOGLE_PAY']::text[]));

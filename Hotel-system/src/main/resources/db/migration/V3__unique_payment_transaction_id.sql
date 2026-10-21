-- transaction_id holds the Stripe PaymentIntent id and is how webhooks find the payment
-- (PaymentRepository.findByTransactionId returns a single row). Without a UNIQUE
-- constraint two rows could share an intent id, which made that lookup fail and let one
-- succeeded intent "confirm" a second, unpaid payment.
--
-- Offline payments (cash, bank transfer, crypto) never go through Stripe; any
-- transaction_id they carry was typed in by the client and means nothing, so it is
-- cleared before the constraint is added. NULLs never conflict with each other.

UPDATE payments
SET transaction_id = NULL
WHERE method NOT IN ('CREDIT_CARD', 'DEBIT_CARD', 'GOOGLE_PAY');

ALTER TABLE payments
    ADD CONSTRAINT payments_transaction_id_key UNIQUE (transaction_id);

import { i18n } from '@/i18n'

export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'BANK_TRANSFER' | 'CASH' | 'CRYPTO' | 'GOOGLE_PAY'

// Key names don't mirror the enum 1:1 (CASH -> cashOnArrival), so this can't be a
// generic `payment.${method}` lookup the way bookingStatusLabel/hotelStatusLabel are.
const PAYMENT_METHOD_KEYS: Record<PaymentMethod, string> = {
  CREDIT_CARD: 'creditCard',
  DEBIT_CARD: 'debitCard',
  GOOGLE_PAY: 'googlePay',
  BANK_TRANSFER: 'bankTransfer',
  CASH: 'cashOnArrival',
  CRYPTO: 'crypto',
}

export function paymentMethodLabel(method: PaymentMethod): string {
  return i18n.global.t(`payment.${PAYMENT_METHOD_KEYS[method]}`)
}

export type PaymentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | 'CHARGEBACK'
  | 'EXPIRED'

/**
 * There is no card-number/CVV/expiry field here on purpose — the server has none either.
 * This records *how* a booking was paid, not a real charge; card details entered in
 * PaymentModal never leave the browser as raw values.
 */
export interface PaymentRequest {
  bookingId: number
  method: PaymentMethod
  currency: string
  transactionId?: string
}

export interface PaymentResponse {
  id: number
  bookingId: number
  amount: number
  method: PaymentMethod
  currency: string
  transactionId: string | null
  status: PaymentStatus
  paidAt: string | null
  createdAt: string
}

/** Returned by POST /api/payments/intent — clientSecret is a one-time token for
 *  Stripe.js, not a payment credential; safe to hold in component state but not to log. */
export interface PaymentIntentResponse {
  paymentId: number
  clientSecret: string
}

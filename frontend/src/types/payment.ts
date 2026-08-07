export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'BANK_TRANSFER' | 'CASH' | 'CRYPTO'

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

// ─── src/app/core/models/auth.models.ts ─────────────────────────────

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  idCardNumber: string;
  dateOfBirth?: string;
  address?: string;
}

export interface LoginRequest {
  identifier: string;
  password: string;
  deviceInfo?: string;
}

export interface TotpVerifyRequest {
  tempToken: string;
  totpCode: string;
}

export interface AuthResponse {
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  tokenType?: string;
  user?: User;
  totpRequired?: boolean;
  tempToken?: string;
}

export interface User {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  idCardNumber?: string;
  status: UserStatus;
  totpEnabled: boolean;
  emailVerified: boolean;
  lastLoginAt?: string;
  lastLoginIp?: string;
  createdAt?: string;
  roles?: string[];
}

export type UserStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';

// ─── Account models ──────────────────────────────────────────────────

export interface Account {
  id: number;
  accountNumber: string;
  iban: string;
  accountType: AccountType;
  currency: string;
  balance: number;
  availableBalance: number;
  status: AccountStatus;
  dailyLimit: number;
  monthlyLimit: number;
  openedAt: string;
}

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT' | 'INVESTMENT';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

// ─── Transaction models ───────────────────────────────────────────────

export interface Transaction {
  id: number;
  transactionRef: string;
  type: 'DEBIT' | 'CREDIT';
  category: TransactionCategory;
  amount: number;
  currency: string;
  balanceAfter: number;
  label?: string;
  status: TransactionStatus;
  counterpartIban?: string;
  counterpartName?: string;
  valueDate: string;
  createdAt: string;
}

export type TransactionCategory =
  | 'TRANSFER' | 'STANDING_ORDER' | 'CREDIT_DISBURSEMENT'
  | 'CREDIT_REPAYMENT' | 'FEE' | 'INTEREST' | 'DEPOSIT' | 'WITHDRAWAL';

export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'REVERSED';

// ─── Transfer models ──────────────────────────────────────────────────

export interface TransferRequest {
  fromAccountId: number;
  toIban: string;
  toName: string;
  amount: number;
  label?: string;
  scheduledDate?: string;
  totpCode: string;
}

export interface Transfer {
  id: number;
  fromAccountNumber: string;
  toIban: string;
  toName: string;
  amount: number;
  currency: string;
  label?: string;
  status: TransferStatus;
  scheduledDate?: string;
  processedAt?: string;
  createdAt: string;
}

export type TransferStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

// ─── Credit models ────────────────────────────────────────────────────

export interface CreditSimulationRequest {
  amount: number;
  durationMonths: number;
  creditType: CreditType;
}

export interface CreditSimulationResponse {
  requestedAmount: number;
  durationMonths: number;
  annualRate: number;
  monthlyPayment: number;
  totalRepayment: number;
  totalInterest: number;
  creditType: CreditType;
  amortizationTable: AmortizationEntry[];
}

export interface AmortizationEntry {
  month: number;
  payment: number;
  principal: number;
  interest: number;
  remainingBalance: number;
}

export interface CreditApplication {
  id: number;
  amount: number;
  durationMonths: number;
  annualRate: number;
  monthlyPayment: number;
  totalCost: number;
  purpose?: string;
  creditType: CreditType;
  status: CreditStatus;
  rejectionReason?: string;
  createdAt: string;
}

export type CreditType = 'PERSONAL' | 'MORTGAGE' | 'AUTO' | 'BUSINESS' | 'STUDENT';
export type CreditStatus = 'PENDING' | 'REVIEWING' | 'APPROVED' | 'REJECTED' | 'DISBURSED' | 'CLOSED';

// ─── Pagination ───────────────────────────────────────────────────────

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

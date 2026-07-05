export interface ApiErrorDetail {
  traceId: string | null;
  fields?: { field: string; message: string }[];
}

/** Standard envelope for all `/api/**` responses */
export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message?: string | null;
  data?: T | null;
  error?: ApiErrorDetail | null;
  timestamp: string;
}

export interface AuthenticatedUserResponse {
  id: string;
  email: string;
  username: string;
  displayName: string;
  status: string;
  emailVerified: boolean;
}

export interface AuthTokenResponse {
  tokenType: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  issuedAt: string;
  sessionId: string;
  user: AuthenticatedUserResponse;
}

export interface RegisterRequest {
  email: string;
  username: string;
  password: string;
  displayName: string;
  campus?: string;
}

export interface RegisterResponse {
  id: string;
  email: string;
  username: string;
  displayName: string;
  status: string;
  emailVerified: boolean;
}

export interface LoginRequest {
  identifier: string;
  password: string;
}

export interface ForgotPasswordResponse {
  message: string;
}

export interface UserProfileResponse {
  id: string;
  email: string;
  username: string;
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  avatarUrl: string | null;
  status: string;
  roles: string[];
  permVersion: number;
  permissions: string[];
  superAdmin: boolean;
  emailVerified: boolean;
  createdAt: string;
}

export interface UpdateUserProfileRequest {
  displayName?: string;
  firstName?: string;
  lastName?: string;
  avatarUrl?: string;
}

export interface AdminOverviewResponse {
  totalUsers: number;
  activeUsers: number;
  pendingVerificationUsers: number;
  disabledUsers: number;
  superAdminUsers: number;
  adminUsers: number;
  subAdminUsers: number;
}

export interface AdminUserSummary {
  id: string;
  email: string;
  username: string;
  displayName: string;
  status: string;
  roles: string[];
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AdminUserPageResponse {
  items: AdminUserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PermissionItemResponse {
  slug: string;
  module: string;
  resource: string;
  action: string;
  description: string;
}

export interface PermissionCatalogResponse {
  modules: Record<string, PermissionItemResponse[]>;
}

export interface RoleSummaryResponse {
  id: string;
  slug: string;
  name: string;
  roleType: string;
  parentRoleId: string | null;
  system: boolean;
  editable: boolean;
  permissionCount: number;
}

export interface RoleDetailResponse {
  id: string;
  slug: string;
  name: string;
  roleType: string;
  parentRoleId: string | null;
  system: boolean;
  editable: boolean;
  permissions: string[];
}

export interface CreateRoleRequest {
  slug: string;
  name: string;
  parentRoleSlug?: string | null;
  permissions: string[];
}

export interface EffectivePermissions {
  userId: string;
  permVersion: number;
  roles: string[];
  permissions: string[];
  superAdmin: boolean;
}

export interface UserPermissionOverrideResponse {
  permissionSlug: string;
  effect: string;
  reason: string | null;
}

export interface MembershipPlanResponse {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  pricePoints: number;
  currency: string;
  billingInterval: string;
  roleSlug: string;
  durationDays: number;
  imageUrl: string | null;
}

export interface AdminMembershipPlanResponse extends MembershipPlanResponse {
  status: string;
}

export interface MembershipRoleOptionResponse {
  id: string;
  slug: string;
  name: string;
}

export interface MembershipStatusResponse {
  membershipId: string | null;
  planSlug: string | null;
  planName: string | null;
  roleSlug: string | null;
  expiresAt: string | null;
  active: boolean;
}

export interface AdminDepositTierResponse {
  id: string;
  label: string;
  amountVnd: number;
  points: number;
  bonusPercent: number;
  totalPoints: number;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderListItem {
  orderId: string;
  orderCode: string;
  amount: number;
  currency: string;
  status: string;
  pointsAwarded: number | null;
  tierLabel: string | null;
  userId: string;
  username: string | null;
  email: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface AdminOrderPageResponse {
  items: AdminOrderListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminOrderPaymentRecord {
  paymentId: string;
  status: string;
  amountCents: number;
  currency: string;
  paidAt: string | null;
  createdAt: string;
}

export interface AdminOrderDetailResponse {
  orderId: string;
  orderCode: string;
  amount: number;
  currency: string;
  status: string;
  pointsAwarded: number | null;
  tierLabel: string | null;
  checkoutUrl: string | null;
  expiredAt: string | null;
  createdAt: string;
  updatedAt: string;
  user: { userId: string; username: string | null; email: string | null; displayName: string | null };
  payments: AdminOrderPaymentRecord[];
}

export interface BroadcastConfigResponse {
  id: string;
  eventType: string;
  config: Record<string, unknown>;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentAnalyticsResponse {
  totalRevenue: number;
  totalTransactions: number;
  totalPointsIssued: number;
  statusBreakdown: { paid: number; pending: number; failed: number; expired: number };
  conversionRate: number;
  averageDepositAmount: number;
  revenueByTier: { tierLabel: string; count: number; totalRevenue: number }[];
  topUsers: { userId: string; username: string | null; totalDeposited: number; transactionCount: number }[];
}

export interface SessionResponse {
  id: string;
  ipAddress: string | null;
  userAgent: string | null;
  deviceLabel: string;
  issuedAt: string;
  lastUsedAt: string | null;
  current: boolean;
}

export interface SessionListResponse {
  sessions: SessionResponse[];
  maxDevices: number;
  deviceLimitSource: "GLOBAL" | "CUSTOM" | "UNLIMITED";
}

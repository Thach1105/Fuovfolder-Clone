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

/** @deprecated Use ApiEnvelope — kept as alias for error payloads */
export type ApiErrorResponse = ApiEnvelope<null>;

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
  verificationToken: string;
}

export interface LoginRequest {
  identifier: string;
  password: string;
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

export interface MockThread {
  id: number;
  tag: string;
  title: string;
  forum: string;
  replies: number;
  views: number;
  lastActivity: string;
  author: string;
  authorInitial: string;
}

export interface MockContributor {
  rank: number;
  username: string;
  score: number;
  initial: string;
}

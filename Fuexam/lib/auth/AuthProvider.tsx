"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import * as authApi from "@/lib/api/auth";
import * as usersApi from "@/lib/api/users";
import {
  AUTH_SESSION_EXPIRED_EVENT,
  AUTH_SESSION_REFRESHED_EVENT,
} from "@/lib/api/client";
import type { AuthTokenResponse, LoginRequest, RegisterRequest, UserProfileResponse } from "@/types/api";

interface AuthContextValue {
  user: UserProfileResponse | null;
  loading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const REFRESH_SKEW_MS = 60_000;
const FALLBACK_REFRESH_MS = 8 * 60_000;
const MIN_REFRESH_DELAY_MS = 10_000;

function nextRefreshDelay(expiresAt: string | null) {
  if (!expiresAt) return FALLBACK_REFRESH_MS;
  const expiresAtMs = new Date(expiresAt).getTime();
  if (!Number.isFinite(expiresAtMs)) return FALLBACK_REFRESH_MS;
  return Math.max(MIN_REFRESH_DELAY_MS, expiresAtMs - Date.now() - REFRESH_SKEW_MS);
}

function sessionFromEvent(event: Event): AuthTokenResponse | null {
  if (!(event instanceof CustomEvent)) return null;
  const detail = event.detail;
  if (!detail || typeof detail !== "object") return null;
  const accessTokenExpiresAt = (detail as Partial<AuthTokenResponse>).accessTokenExpiresAt;
  return typeof accessTokenExpiresAt === "string" ? (detail as AuthTokenResponse) : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [accessTokenExpiresAt, setAccessTokenExpiresAt] = useState<string | null>(null);
  const [refreshTokenExpiresAt, setRefreshTokenExpiresAt] = useState<string | null>(null);

  const updateSessionExpiry = useCallback((session: AuthTokenResponse | null) => {
    setAccessTokenExpiresAt(session?.accessTokenExpiresAt ?? null);
    setRefreshTokenExpiresAt(session?.refreshTokenExpiresAt ?? null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const profile = await usersApi.getCurrentUser();
      setUser(profile);
    } catch {
      setUser(null);
      updateSessionExpiry(null);
    }
  }, [updateSessionExpiry]);

  useEffect(() => {
    refreshUser().finally(() => setLoading(false));
  }, [refreshUser]);

  useEffect(() => {
    const onRefreshed = (event: Event) => {
      const session = sessionFromEvent(event);
      if (session) updateSessionExpiry(session);
    };
    const onExpired = () => {
      setUser(null);
      updateSessionExpiry(null);
    };
    window.addEventListener(AUTH_SESSION_REFRESHED_EVENT, onRefreshed);
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, onExpired);
    return () => {
      window.removeEventListener(AUTH_SESSION_REFRESHED_EVENT, onRefreshed);
      window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, onExpired);
    };
  }, [updateSessionExpiry]);

  useEffect(() => {
    if (!user) return;

    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const session = await authApi.refreshSession();
        if (cancelled) return;
        updateSessionExpiry(session);
        await refreshUser();
      } catch {
        if (cancelled) return;
        setUser(null);
        updateSessionExpiry(null);
      }
    }, nextRefreshDelay(accessTokenExpiresAt));

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [accessTokenExpiresAt, refreshUser, updateSessionExpiry, user]);

  useEffect(() => {
    if (!user || !refreshTokenExpiresAt) return;
    const refreshExpiresAtMs = new Date(refreshTokenExpiresAt).getTime();
    if (!Number.isFinite(refreshExpiresAtMs)) return;
    if (refreshExpiresAtMs <= Date.now()) {
      setUser(null);
      updateSessionExpiry(null);
    }
  }, [refreshTokenExpiresAt, updateSessionExpiry, user]);

  const login = useCallback(
    async (data: LoginRequest) => {
      const session = await authApi.login(data);
      updateSessionExpiry(session);
      await refreshUser();
    },
    [refreshUser, updateSessionExpiry],
  );

  const register = useCallback(async (data: RegisterRequest) => {
    await authApi.register(data);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      updateSessionExpiry(null);
    }
  }, [updateSessionExpiry]);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, refreshUser }),
    [user, loading, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}

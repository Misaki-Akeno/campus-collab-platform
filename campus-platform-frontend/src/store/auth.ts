"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { UserInfo } from "@/types/api";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserInfo | null;
  setAuth: (accessToken: string, refreshToken: string, user: UserInfo) => void;
  setAccessToken: (token: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setAuth: (accessToken, refreshToken, user) => {
        set({ accessToken, refreshToken, user });
        // 写 cookie 供 middleware 做路由守卫（非 HttpOnly，仅存角色信息）
        document.cookie = `campus-session=1; path=/; max-age=${7 * 24 * 3600}; SameSite=Lax`;
        document.cookie = `campus-role=${user.role}; path=/; max-age=${7 * 24 * 3600}; SameSite=Lax`;
      },
      setAccessToken: (token) => set({ accessToken: token }),
      clearAuth: () => {
        set({ accessToken: null, refreshToken: null, user: null });
        document.cookie = "campus-session=; path=/; max-age=0";
        document.cookie = "campus-role=; path=/; max-age=0";
      },
    }),
    {
      name: "campus-auth",
      // 只持久化 refreshToken 和 user；accessToken 在内存中，重新打开页面时会自动刷新
      partialize: (state) => ({
        refreshToken: state.refreshToken,
        user: state.user,
      }),
    }
  )
);

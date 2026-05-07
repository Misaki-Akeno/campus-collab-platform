import type {
  ApiResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserProfile,
  TokenRefreshRequest,
  TokenRefreshResponse,
} from "@/types/api";
import apiClient from "./client";

export const userApi = {
  login(data: LoginRequest) {
    return apiClient.post<ApiResponse<LoginResponse>>("/user/api/v1/login", data);
  },

  register(data: RegisterRequest) {
    return apiClient.post<ApiResponse<{ userId: string; username: string }>>(
      "/user/api/v1/register",
      data
    );
  },

  getProfile() {
    return apiClient.get<ApiResponse<UserProfile>>("/user/api/v1/me");
  },

  refreshToken(data: TokenRefreshRequest) {
    return apiClient.post<ApiResponse<TokenRefreshResponse>>(
      "/user/api/v1/token/refresh",
      data
    );
  },

  logout(accessToken: string) {
    return apiClient.post<ApiResponse<null>>("/user/api/v1/logout", { accessToken });
  },

  changePassword(oldPassword: string, newPassword: string) {
    return apiClient.put<ApiResponse<null>>("/user/api/v1/me/password", {
      oldPassword,
      newPassword,
    });
  },
};

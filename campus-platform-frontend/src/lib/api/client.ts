import axios, {
  type AxiosError,
  type InternalAxiosRequestConfig,
} from "axios";
import { useAuthStore } from "@/store/auth";

const GATEWAY = process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:9000";

const apiClient = axios.create({
  baseURL: GATEWAY,
  timeout: 10_000,
});

// ── Request 拦截：自动注入 Bearer Token ─────────────────────────────────────
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Response 拦截：401 自动刷新 ──────────────────────────────────────────────
let isRefreshing = false;
let pendingQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

function drainQueue(err: unknown, token: string | null) {
  pendingQueue.forEach((p) => (err ? p.reject(err) : p.resolve(token!)));
  pendingQueue = [];
}

function redirectToLogin() {
  if (typeof window !== "undefined") window.location.href = "/login";
}

apiClient.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const req = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status !== 401 || req._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) =>
        pendingQueue.push({ resolve, reject })
      ).then((token) => {
        req.headers.Authorization = `Bearer ${token}`;
        return apiClient(req);
      });
    }

    req._retry = true;
    isRefreshing = true;

    const { refreshToken, clearAuth, setAccessToken } = useAuthStore.getState();

    if (!refreshToken) {
      clearAuth();
      redirectToLogin();
      return Promise.reject(error);
    }

    try {
      const { data } = await axios.post(`${GATEWAY}/user/api/v1/token/refresh`, {
        refreshToken,
      });
      const newToken: string = data.data.accessToken;
      setAccessToken(newToken);
      drainQueue(null, newToken);
      req.headers.Authorization = `Bearer ${newToken}`;
      return apiClient(req);
    } catch (refreshErr) {
      drainQueue(refreshErr, null);
      clearAuth();
      redirectToLogin();
      return Promise.reject(refreshErr);
    } finally {
      isRefreshing = false;
    }
  }
);

export default apiClient;

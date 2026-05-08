import type { ApiResponse, PageResult, Activity, SeckillOrder } from "@/types/api";
import apiClient from "./client";

interface ActivityListParams {
  clubId?: string;
  status?: number;
  page?: number;
  pageSize?: number;
}

export const seckillApi = {
  listActivities(params?: ActivityListParams) {
    return apiClient.get<ApiResponse<PageResult<Activity>>>(
      "/seckill/api/v1/activities",
      { params }
    );
  },

  getActivity(activityId: string) {
    return apiClient.get<ApiResponse<Activity>>(
      `/seckill/api/v1/activities/${activityId}`
    );
  },

  book(activityId: string) {
    return apiClient.post<ApiResponse<{ orderId: string }>>(
      `/seckill/api/v1/activities/${activityId}/book`
    );
  },

  getOrder(orderId: string) {
    return apiClient.get<ApiResponse<SeckillOrder>>(
      `/seckill/api/v1/orders/${orderId}`
    );
  },
};

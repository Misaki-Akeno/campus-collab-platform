import type {
  ApiResponse,
  PageResult,
  Club,
  ClubListParams,
  ClubMember,
  ClubAnnouncement,
  CreateClubRequest,
} from "@/types/api";
import apiClient from "./client";

export const clubApi = {
  list(params?: ClubListParams) {
    return apiClient.get<ApiResponse<PageResult<Club>>>("/club/api/v1/clubs", {
      params: {
        page: params?.page ?? 1,
        pageSize: params?.pageSize ?? 12,
        keyword: params?.keyword,
        category: params?.category,
        status: params?.status,
      },
    });
  },

  get(clubId: string) {
    return apiClient.get<ApiResponse<Club>>(`/club/api/v1/clubs/${clubId}`);
  },

  create(data: CreateClubRequest) {
    return apiClient.post<ApiResponse<{ clubId: string }>>("/club/api/v1/clubs", data);
  },

  join(clubId: string) {
    return apiClient.post<ApiResponse<null>>(`/club/api/v1/clubs/${clubId}/join`);
  },

  approve(clubId: string, approved: boolean) {
    return apiClient.post<ApiResponse<null>>(
      `/club/api/v1/clubs/${clubId}/approve`,
      null,
      { params: { approved } }
    );
  },

  getMembers(clubId: string, params?: { page?: number; pageSize?: number }) {
    return apiClient.get<ApiResponse<PageResult<ClubMember>>>(
      `/club/api/v1/clubs/${clubId}/members`,
      { params }
    );
  },

  approveMember(clubId: string, memberId: string, approved: boolean) {
    return apiClient.post<ApiResponse<null>>(
      `/club/api/v1/clubs/${clubId}/members/${memberId}/approve`,
      null,
      { params: { approved } }
    );
  },

  removeMember(clubId: string, memberId: string) {
    return apiClient.delete<ApiResponse<null>>(
      `/club/api/v1/clubs/${clubId}/members/${memberId}`
    );
  },

  updateMemberRole(clubId: string, targetUserId: string, role: number) {
    return apiClient.put<ApiResponse<null>>(
      `/club/api/v1/clubs/${clubId}/members/${targetUserId}/role`,
      { role }
    );
  },

  getAnnouncements(clubId: string) {
    return apiClient.get<ApiResponse<ClubAnnouncement[]>>(
      `/club/api/v1/clubs/${clubId}/announcements`
    );
  },

  postAnnouncement(
    clubId: string,
    data: { title: string; content: string; isPinned?: boolean }
  ) {
    return apiClient.post<ApiResponse<{ announcementId: string }>>(
      `/club/api/v1/clubs/${clubId}/announcements`,
      data
    );
  },
};

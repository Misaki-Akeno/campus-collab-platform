// ── 通用响应包装 ─────────────────────────────────────────────────────────────

export interface ApiResponse<T> {
  code: number;
  msg?: string;
  data: T;
  traceId?: string;
}

export interface PageResult<T> {
  total: number;
  pages: number;
  current: number;
  records: T[];
}

// ── 运行时枚举常量（可当值用）────────────────────────────────────────────────

export const UserRole = { STUDENT: 0, CLUB_LEADER: 1, ADMIN: 2 } as const;
export const MemberRoleConst = { MEMBER: 0, VICE_LEADER: 1, LEADER: 2 } as const;
export const ClubStatusConst = { PENDING: 0, ACTIVE: 1, DISSOLVED: 2, REJECTED: 3 } as const;
export const ActivityStatusConst = {
  NOT_STARTED: 0,
  ONGOING: 1,
  ENDED: 2,
  CANCELLED: 3,
} as const;
export const OrderStatusConst = { PROCESSING: 0, SUCCESS: 1, FAILED: 2 } as const;

// ── 类型别名 ─────────────────────────────────────────────────────────────────

export type UserRoleValue = 0 | 1 | 2; // 0-学生 1-社长 2-管理员
export type MemberRole = 0 | 1 | 2; // 0-普通 1-副社长 2-社长
export type MemberStatus = 0 | 1 | 2; // 0-待审批 1-已通过 2-已拒绝
export type ClubStatus = 0 | 1 | 2 | 3; // 0-待审核 1-正常 2-已解散 3-已拒绝
export type ActivityStatus = 0 | 1 | 2 | 3; // 0-未开始 1-进行中 2-已结束 3-已取消
export type OrderStatus = 0 | 1 | 2; // 0-处理中 1-成功 2-失败/已取消

// ── 用户 ─────────────────────────────────────────────────────────────────────

export interface UserInfo {
  userId: string;
  username: string;
  nickname: string;
  email?: string;
  role: UserRoleValue;
  avatarUrl: string;
}

export interface UserProfile extends UserInfo {
  clubs: ClubMembership[];
}

export interface ClubMembership {
  clubId: string;
  clubName: string;
  memberRole: MemberRole;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface LoginResponse extends TokenPair {
  userInfo: UserInfo;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}

export interface TokenRefreshResponse {
  accessToken: string;
  expiresIn: number;
}

// ── 社团 ─────────────────────────────────────────────────────────────────────

export interface Club {
  id: string;
  name: string;
  description: string;
  logoUrl: string;
  leaderId: string;
  category: string;
  status: ClubStatus;
  memberCount: number;
  createTime: string;
  updateTime: string;
}

export interface ClubMember {
  id: string;
  userId: string;
  clubId: string;
  nickname: string;
  avatarUrl: string;
  memberRole: MemberRole;
  status: MemberStatus;
  joinTime: string;
}

export interface ClubAnnouncement {
  id: string;
  clubId: string;
  publisherId: string;
  publisherName: string;
  title: string;
  content: string;
  isPinned: boolean;
  createTime: string;
}

export interface CreateClubRequest {
  name: string;
  description: string;
  category: string;
  logoUrl?: string;
}

export interface ClubListParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  category?: string;
  status?: ClubStatus;
}

// ── 秒杀活动 ─────────────────────────────────────────────────────────────────

export interface Activity {
  id: string;
  clubId: string;
  title: string;
  description: string | null;
  coverUrl: string;
  location: string;
  activityTime: string;
  totalStock: number;
  availableStock: number;
  startTime: string;
  endTime: string;
  status: ActivityStatus;
  createTime: string;
  updateTime: string;
}

export interface SeckillOrder {
  id: string;
  userId: number;
  activityId: string;
  status: OrderStatus;
  cancelReason: string | null;
  createTime: string;
  updateTime: string;
}

// ── 文件 ─────────────────────────────────────────────────────────────────────

export type UploadInitResponse =
  | { type: "new"; uploadId: string; presignedUrls: string[] }
  | { type: "instant"; fileUrl: string }
  | { type: "resume"; uploadedParts: string[] };

export interface MergeRequest {
  fileMd5: string;
  fileName: string;
  fileSize: number;
  chunkCount: number;
  etags: string[];
}

export interface MergeResponse {
  fileUrl: string;
}

// ── IM ───────────────────────────────────────────────────────────────────────

export type ConversationType = 1 | 2; // 1-单聊 2-群聊
export type MessageType = 1 | 2 | 3 | 4 | 5; // 1-文本 2-图片 3-文件 4-系统 5-@消息

export interface Conversation {
  conversationId: string;
  type: ConversationType;
  name: string | null;
  avatarUrl: string | null;
  ownerId: string | null;
  maxMembers: number | null;
  createTime: string;
  updateTime: string;
}

export interface ImMessage {
  msgId: string;
  conversationId: string;
  senderId: number;
  msgType: MessageType;
  content: string;
  atUserIds: string | null;
  replyMsgId: string | null;
  isRecalled: 0 | 1;
  createTime: string;
  updateTime: string;
}

// WebSocket 消息
export type WsCmd =
  | "CHAT_MSG"
  | "ACK"
  | "PUSH_MSG"
  | "HEARTBEAT"
  | "RECALL"
  | "READ_REPORT"
  | "TYPING"
  | "KICK_OFF";

export interface WsMessage<T = unknown> {
  cmd: WsCmd;
  msgId?: string;
  refMsgId?: string;
  timestamp?: number;
  payload?: T;
}

export interface ChatMsgPayload {
  conversationId: string;
  type: MessageType;
  content: string;
  atUserIds: number[];
  replyMsgId: string | null;
}

export interface PushMsgPayload {
  msgId: string;
  conversationId: string;
  senderId: number;
  senderName: string;
  senderAvatar: string;
  type: MessageType;
  content: string;
  timestamp: number;
}

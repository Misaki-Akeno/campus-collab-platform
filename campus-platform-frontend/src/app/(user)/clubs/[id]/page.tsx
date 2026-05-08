"use client";

import { use, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Users, Bell, ArrowLeft, Settings, Loader2 } from "lucide-react";
import Link from "next/link";
import { format } from "date-fns";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { clubApi } from "@/lib/api/club";
import { useAuthStore } from "@/store/auth";
import { MemberRoleConst } from "@/types/api";

const MEMBER_ROLE_LABEL: Record<number, string> = {
  0: "成员",
  1: "副社长",
  2: "社长",
};

export default function ClubDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: clubId } = use(params);
  const [tab, setTab] = useState<"announcements" | "members">("announcements");
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();

  const { data: club, isLoading: loadingClub } = useQuery({
    queryKey: ["club", clubId],
    queryFn: async () => {
      const { data: res } = await clubApi.get(clubId);
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  // 始终拉取成员列表，用于判断当前用户的成员身份和角色
  const { data: membersData } = useQuery({
    queryKey: ["club-members", clubId],
    queryFn: async () => {
      const { data: res } = await clubApi.getMembers(clubId, { pageSize: 500 });
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  const { data: announcements, isLoading: loadingAnnouncements } = useQuery({
    queryKey: ["club-announcements", clubId],
    queryFn: async () => {
      const { data: res } = await clubApi.getAnnouncements(clubId);
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
    enabled: tab === "announcements",
  });

  const joinMutation = useMutation({
    mutationFn: () =>
      clubApi.join(clubId).then(({ data }) => {
        if (data.code !== 200) throw new Error(data.msg);
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["club-members", clubId] });
    },
  });

  if (loadingClub) {
    return (
      <div className="flex justify-center py-20">
        <Loader2 className="size-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!club) {
    return <div className="p-6 text-muted-foreground">社团不存在</div>;
  }

  const approvedMembers =
    membersData?.records.filter((m) => m.status === 1) ?? [];
  const myMembership = approvedMembers.find((m) => m.userId === user?.userId);
  const isMember = !!myMembership;
  const canManage =
    club.leaderId === user?.userId ||
    (myMembership && myMembership.memberRole >= MemberRoleConst.VICE_LEADER);

  const pinnedAnnouncements = (announcements ?? []).filter((a) => a.isPinned);
  const otherAnnouncements = (announcements ?? []).filter((a) => !a.isPinned);

  return (
    <div className="p-6 space-y-6 max-w-4xl mx-auto">
      {/* 返回 */}
      <Link
        href="/clubs"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        返回社团列表
      </Link>

      {/* 社团头部 */}
      <div className="flex items-start gap-4">
        <div className="size-16 rounded-xl bg-muted flex items-center justify-center text-2xl shrink-0 overflow-hidden">
          {club.logoUrl ? (
            <img
              src={club.logoUrl}
              alt={club.name}
              className="size-16 object-cover"
            />
          ) : (
            "🎓"
          )}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-2xl font-bold">{club.name}</h1>
            {club.category && (
              <Badge variant="secondary">{club.category}</Badge>
            )}
          </div>
          <p className="text-muted-foreground mt-1 text-sm line-clamp-2">
            {club.description || "暂无简介"}
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            {club.memberCount} 名成员
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          {canManage && (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/clubs/${clubId}/manage`}>
                <Settings className="size-4" />
                管理社团
              </Link>
            </Button>
          )}
          {!isMember && (
            <Button
              size="sm"
              disabled={joinMutation.isPending || joinMutation.isSuccess}
              onClick={() => joinMutation.mutate()}
            >
              {joinMutation.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : joinMutation.isSuccess ? (
                "申请已提交"
              ) : (
                "申请加入"
              )}
            </Button>
          )}
          {isMember && !canManage && (
            <Badge variant="secondary" className="self-center">
              已加入
            </Badge>
          )}
        </div>
      </div>

      <Separator />

      {/* Tab 切换 */}
      <div className="flex gap-1 border-b">
        {(
          [
            { key: "announcements", label: "公告", icon: Bell },
            { key: "members", label: "成员", icon: Users },
          ] as const
        ).map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={cn(
              "flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors",
              tab === key
                ? "border-primary text-primary"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <Icon className="size-4" />
            {label}
          </button>
        ))}
      </div>

      {/* 公告 Tab */}
      {tab === "announcements" && (
        <div className="space-y-3">
          {loadingAnnouncements && (
            <div className="flex justify-center py-10">
              <Loader2 className="size-6 animate-spin text-muted-foreground" />
            </div>
          )}
          {!loadingAnnouncements &&
            announcements &&
            announcements.length === 0 && (
              <div className="py-10 text-center text-muted-foreground text-sm">
                暂无公告
              </div>
            )}
          {[...pinnedAnnouncements, ...otherAnnouncements].map((a) => (
            <div key={a.id} className="rounded-lg border p-4 space-y-2">
              <div className="flex items-center gap-2">
                {a.isPinned && (
                  <Badge variant="destructive" className="text-xs">
                    置顶
                  </Badge>
                )}
                <h3 className="font-medium">{a.title}</h3>
              </div>
              <p className="text-sm text-muted-foreground whitespace-pre-wrap">
                {a.content}
              </p>
              <p className="text-xs text-muted-foreground">
                {a.publisherName} ·{" "}
                {format(new Date(a.createTime), "yyyy-MM-dd HH:mm")}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* 成员 Tab */}
      {tab === "members" && (
        <div className="space-y-1">
          {approvedMembers.length === 0 && (
            <div className="py-10 text-center text-muted-foreground text-sm">
              暂无成员
            </div>
          )}
          {approvedMembers.map((m) => (
            <div
              key={m.id}
              className="flex items-center gap-3 rounded-lg p-2 hover:bg-muted/50"
            >
              <Avatar className="size-9">
                <AvatarImage src={m.avatarUrl} alt={m.nickname} />
                <AvatarFallback>
                  {m.nickname?.charAt(0).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">{m.nickname}</p>
                <p className="text-xs text-muted-foreground">
                  加入于 {format(new Date(m.joinTime), "yyyy-MM-dd")}
                </p>
              </div>
              <Badge variant={m.memberRole >= 1 ? "default" : "secondary"}>
                {MEMBER_ROLE_LABEL[m.memberRole] ?? "成员"}
              </Badge>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

"use client";

import { use, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2, Check, X, Shield, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { clubApi } from "@/lib/api/club";
import { useAuthStore } from "@/store/auth";
import { MemberRoleConst } from "@/types/api";

const ROLE_LABELS: Record<number, string> = { 0: "普通成员", 1: "副社长", 2: "社长" };

export default function ClubManagePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: clubId } = use(params);
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();
  const [tab, setTab] = useState<"pending" | "members" | "announce">("pending");

  // 公告表单
  const [annTitle, setAnnTitle] = useState("");
  const [annContent, setAnnContent] = useState("");
  const [annPinned, setAnnPinned] = useState(false);
  const [annError, setAnnError] = useState("");

  const { data: club } = useQuery({
    queryKey: ["club", clubId],
    queryFn: async () => {
      const { data: res } = await clubApi.get(clubId);
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  const { data: membersData, isLoading: loadingMembers } = useQuery({
    queryKey: ["club-members", clubId],
    queryFn: async () => {
      const { data: res } = await clubApi.getMembers(clubId, { pageSize: 500 });
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  // 权限检查
  const myMembership = membersData?.records.find(
    (m) => m.userId === user?.userId && m.status === 1
  );
  const canManage =
    club?.leaderId === user?.userId ||
    (myMembership && myMembership.memberRole >= MemberRoleConst.VICE_LEADER);

  const pendingMembers =
    membersData?.records.filter((m) => m.status === 0) ?? [];
  const approvedMembers =
    membersData?.records.filter((m) => m.status === 1) ?? [];

  const approveMut = useMutation({
    mutationFn: ({
      memberId,
      approved,
    }: {
      memberId: string;
      approved: boolean;
    }) =>
      clubApi.approveMember(clubId, memberId, approved).then(({ data }) => {
        if (data.code !== 200) throw new Error(data.msg);
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["club-members", clubId] }),
  });

  const removeMut = useMutation({
    mutationFn: (memberId: string) =>
      clubApi.removeMember(clubId, memberId).then(({ data }) => {
        if (data.code !== 200) throw new Error(data.msg);
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["club-members", clubId] }),
  });

  const roleMut = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: number }) =>
      clubApi.updateMemberRole(clubId, userId, role).then(({ data }) => {
        if (data.code !== 200) throw new Error(data.msg);
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["club-members", clubId] }),
  });

  const announceMut = useMutation({
    mutationFn: () =>
      clubApi
        .postAnnouncement(clubId, {
          title: annTitle,
          content: annContent,
          isPinned: annPinned,
        })
        .then(({ data }) => {
          if (data.code !== 200) throw new Error(data.msg);
        }),
    onSuccess: () => {
      setAnnTitle("");
      setAnnContent("");
      setAnnPinned(false);
      qc.invalidateQueries({ queryKey: ["club-announcements", clubId] });
    },
    onError: (e: Error) => setAnnError(e.message),
  });

  if (!canManage && !loadingMembers) {
    return (
      <div className="p-6 space-y-4">
        <p className="text-muted-foreground">无权访问此页面</p>
        <Button variant="outline" size="sm" onClick={() => router.back()}>
          返回
        </Button>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6 max-w-3xl mx-auto">
      <Link
        href={`/clubs/${clubId}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        返回社团详情
      </Link>

      <div>
        <h1 className="text-2xl font-bold">社团管理</h1>
        {club && (
          <p className="text-muted-foreground text-sm mt-1">{club.name}</p>
        )}
      </div>

      <Separator />

      {/* Tab 切换 */}
      <div className="flex gap-1 border-b">
        {(
          [
            { key: "pending", label: `待审批 (${pendingMembers.length})` },
            { key: "members", label: `成员管理 (${approvedMembers.length})` },
            { key: "announce", label: "发布公告" },
          ] as const
        ).map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={cn(
              "px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors",
              tab === key
                ? "border-primary text-primary"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {/* 待审批 Tab */}
      {tab === "pending" && (
        <div className="space-y-2">
          {loadingMembers && (
            <div className="flex justify-center py-10">
              <Loader2 className="size-6 animate-spin text-muted-foreground" />
            </div>
          )}
          {!loadingMembers && pendingMembers.length === 0 && (
            <div className="py-10 text-center text-muted-foreground text-sm">
              暂无待审批申请
            </div>
          )}
          {pendingMembers.map((m) => (
            <div
              key={m.id}
              className="flex items-center gap-3 rounded-lg border p-3"
            >
              <Avatar className="size-9">
                <AvatarImage src={m.avatarUrl} alt={m.nickname} />
                <AvatarFallback>{m.nickname?.charAt(0)}</AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">{m.nickname}</p>
                <p className="text-xs text-muted-foreground">申请加入</p>
              </div>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  className="text-destructive hover:bg-destructive/10"
                  disabled={approveMut.isPending}
                  onClick={() =>
                    approveMut.mutate({ memberId: m.id, approved: false })
                  }
                >
                  <X className="size-4" />
                  拒绝
                </Button>
                <Button
                  size="sm"
                  disabled={approveMut.isPending}
                  onClick={() =>
                    approveMut.mutate({ memberId: m.id, approved: true })
                  }
                >
                  <Check className="size-4" />
                  通过
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 成员管理 Tab */}
      {tab === "members" && (
        <div className="space-y-2">
          {loadingMembers && (
            <div className="flex justify-center py-10">
              <Loader2 className="size-6 animate-spin text-muted-foreground" />
            </div>
          )}
          {approvedMembers.map((m) => {
            const isSelf = m.userId === user?.userId;
            const isLeader = m.memberRole === MemberRoleConst.LEADER;
            return (
              <div
                key={m.id}
                className="flex items-center gap-3 rounded-lg border p-3"
              >
                <Avatar className="size-9">
                  <AvatarImage src={m.avatarUrl} alt={m.nickname} />
                  <AvatarFallback>{m.nickname?.charAt(0)}</AvatarFallback>
                </Avatar>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium">{m.nickname}</p>
                </div>
                <Badge variant={m.memberRole >= 1 ? "default" : "secondary"}>
                  {ROLE_LABELS[m.memberRole]}
                </Badge>
                {!isSelf && !isLeader && (
                  <div className="flex gap-1">
                    {m.memberRole === 0 && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-xs px-2"
                        disabled={roleMut.isPending}
                        title="设为副社长"
                        onClick={() =>
                          roleMut.mutate({
                            userId: m.userId,
                            role: MemberRoleConst.VICE_LEADER,
                          })
                        }
                      >
                        <Shield className="size-3.5" />
                      </Button>
                    )}
                    {m.memberRole === 1 && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-xs px-2"
                        disabled={roleMut.isPending}
                        title="降为普通成员"
                        onClick={() =>
                          roleMut.mutate({
                            userId: m.userId,
                            role: MemberRoleConst.MEMBER,
                          })
                        }
                      >
                        <X className="size-3.5" />
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-destructive hover:bg-destructive/10 px-2"
                      disabled={removeMut.isPending}
                      title="移除成员"
                      onClick={() => removeMut.mutate(m.id)}
                    >
                      <Trash2 className="size-3.5" />
                    </Button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 发布公告 Tab */}
      {tab === "announce" && (
        <div className="space-y-4 max-w-lg">
          <div className="space-y-1.5">
            <Label htmlFor="ann-title">标题</Label>
            <Input
              id="ann-title"
              placeholder="公告标题"
              value={annTitle}
              onChange={(e) => setAnnTitle(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ann-content">正文</Label>
            <Textarea
              id="ann-content"
              rows={6}
              placeholder="公告内容..."
              value={annContent}
              onChange={(e) => setAnnContent(e.target.value)}
            />
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="ann-pinned"
              checked={annPinned}
              onChange={(e) => setAnnPinned(e.target.checked)}
              className="accent-primary"
            />
            <Label htmlFor="ann-pinned" className="cursor-pointer">
              置顶公告
            </Label>
          </div>
          {annError && (
            <p className="text-sm text-destructive">{annError}</p>
          )}
          <Button
            disabled={
              !annTitle.trim() || !annContent.trim() || announceMut.isPending
            }
            onClick={() => {
              setAnnError("");
              announceMut.mutate();
            }}
          >
            {announceMut.isPending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              "发布公告"
            )}
          </Button>
          {announceMut.isSuccess && (
            <p className="text-sm text-green-600">公告发布成功</p>
          )}
        </div>
      )}
    </div>
  );
}

"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Plus, Loader2 } from "lucide-react";
import Link from "next/link";

import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { ClubCard } from "@/components/clubs/ClubCard";
import { clubApi } from "@/lib/api/club";
import { useAuthStore } from "@/store/auth";
import { UserRole } from "@/types/api";

export default function ClubsPage() {
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const user = useAuthStore((s) => s.user);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["clubs", { keyword, page }],
    queryFn: async () => {
      const { data: res } = await clubApi.list({ keyword, page, pageSize: 12, status: 1 });
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  return (
    <div className="p-6 space-y-6">
      {/* 页头 */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">发现社团</h1>
          <p className="text-muted-foreground text-sm">
            共 {data?.total ?? 0} 个社团
          </p>
        </div>
        {(user?.role === UserRole.CLUB_LEADER || user?.role === UserRole.STUDENT) && (
          <Button asChild size="sm">
            <Link href="/clubs/create">
              <Plus className="size-4" />
              创建社团
            </Link>
          </Button>
        )}
      </div>

      {/* 搜索 */}
      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
        <Input
          className="pl-9"
          placeholder="搜索社团名称..."
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value);
            setPage(1);
          }}
        />
      </div>

      {/* 列表 */}
      {isLoading && (
        <div className="flex justify-center py-20">
          <Loader2 className="size-8 animate-spin text-muted-foreground" />
        </div>
      )}

      {isError && (
        <div className="py-20 text-center text-muted-foreground">
          加载失败，请稍后重试
        </div>
      )}

      {!isLoading && !isError && data && (
        <>
          {data.records.length === 0 ? (
            <div className="py-20 text-center text-muted-foreground">
              {keyword ? `没有找到"${keyword}"相关社团` : "暂无社团"}
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {data.records.map((club) => (
                <ClubCard key={club.id} club={club} />
              ))}
            </div>
          )}

          {/* 分页 */}
          {data.pages > 1 && (
            <div className="flex justify-center gap-2 pt-4">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 1}
                onClick={() => setPage((p) => p - 1)}
              >
                上一页
              </Button>
              <span className="flex items-center text-sm text-muted-foreground px-2">
                {page} / {data.pages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page === data.pages}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

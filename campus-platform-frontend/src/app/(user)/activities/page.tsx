"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, MapPin, Clock, Users } from "lucide-react";
import Link from "next/link";
import { format, formatDistanceToNow, isFuture, isPast } from "date-fns";
import { zhCN } from "date-fns/locale";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { seckillApi } from "@/lib/api/seckill";
import { ActivityStatusConst } from "@/types/api";
import type { Activity } from "@/types/api";

const STATUS_CONFIG: Record<
  number,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  [ActivityStatusConst.NOT_STARTED]: { label: "未开始", variant: "secondary" },
  [ActivityStatusConst.ONGOING]: { label: "报名中", variant: "default" },
  [ActivityStatusConst.ENDED]: { label: "已结束", variant: "outline" },
  [ActivityStatusConst.CANCELLED]: { label: "已取消", variant: "destructive" },
};

function ActivityCard({ activity }: { activity: Activity }) {
  const cfg = STATUS_CONFIG[activity.status] ?? STATUS_CONFIG[0];
  const seckillStart = new Date(activity.startTime);
  const seckillEnd = new Date(activity.endTime);
  const isOngoing = activity.status === ActivityStatusConst.ONGOING;
  const hasNotStarted = activity.status === ActivityStatusConst.NOT_STARTED;

  return (
    <Link href={`/activities/${activity.id}`} className="block group">
      <div className="rounded-xl border overflow-hidden hover:shadow-md transition-shadow">
        {/* 封面图 */}
        <div className="h-36 bg-muted flex items-center justify-center overflow-hidden">
          {activity.coverUrl ? (
            <img
              src={activity.coverUrl}
              alt={activity.title}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            />
          ) : (
            <span className="text-4xl">🎉</span>
          )}
        </div>
        <div className="p-4 space-y-2">
          <div className="flex items-start justify-between gap-2">
            <h3 className="font-semibold line-clamp-1 group-hover:text-primary transition-colors">
              {activity.title}
            </h3>
            <Badge variant={cfg.variant} className="shrink-0 text-xs">
              {cfg.label}
            </Badge>
          </div>

          {activity.location && (
            <p className="flex items-center gap-1 text-xs text-muted-foreground">
              <MapPin className="size-3" />
              {activity.location}
            </p>
          )}

          <p className="flex items-center gap-1 text-xs text-muted-foreground">
            <Clock className="size-3" />
            活动时间：{format(new Date(activity.activityTime), "MM-dd HH:mm")}
          </p>

          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span className="flex items-center gap-1">
              <Users className="size-3" />
              剩余 {activity.availableStock} / {activity.totalStock} 名额
            </span>
            {isOngoing && isFuture(seckillEnd) && (
              <span className="text-primary font-medium">
                报名截止 {formatDistanceToNow(seckillEnd, { locale: zhCN, addSuffix: true })}
              </span>
            )}
            {hasNotStarted && (
              <span className="text-amber-500 font-medium">
                {formatDistanceToNow(seckillStart, { locale: zhCN, addSuffix: true })}开始报名
              </span>
            )}
          </div>
        </div>
      </div>
    </Link>
  );
}

export default function ActivitiesPage() {
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(
    ActivityStatusConst.ONGOING
  );

  const { data, isLoading, isError } = useQuery({
    queryKey: ["activities", { page, status: statusFilter }],
    queryFn: async () => {
      const { data: res } = await seckillApi.listActivities({
        page,
        pageSize: 12,
        status: statusFilter,
      });
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  const filters: { label: string; value: number | undefined }[] = [
    { label: "全部", value: undefined },
    { label: "报名中", value: ActivityStatusConst.ONGOING },
    { label: "未开始", value: ActivityStatusConst.NOT_STARTED },
    { label: "已结束", value: ActivityStatusConst.ENDED },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* 页头 */}
      <div>
        <h1 className="text-2xl font-bold">活动广场</h1>
        <p className="text-muted-foreground text-sm">共 {data?.total ?? 0} 个活动</p>
      </div>

      {/* 状态筛选 */}
      <div className="flex gap-2 flex-wrap">
        {filters.map((f) => (
          <Button
            key={String(f.value)}
            size="sm"
            variant={statusFilter === f.value ? "default" : "outline"}
            onClick={() => {
              setStatusFilter(f.value);
              setPage(1);
            }}
          >
            {f.label}
          </Button>
        ))}
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
              暂无活动
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {data.records.map((activity) => (
                <ActivityCard key={activity.id} activity={activity} />
              ))}
            </div>
          )}

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

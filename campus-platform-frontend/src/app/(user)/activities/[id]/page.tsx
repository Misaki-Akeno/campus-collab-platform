"use client";

import { use, useEffect, useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import {
  ArrowLeft,
  Loader2,
  MapPin,
  Clock,
  Users,
  CalendarDays,
  CheckCircle2,
  XCircle,
  Timer,
} from "lucide-react";
import Link from "next/link";
import { format, formatDistanceToNow, isFuture, isPast } from "date-fns";
import { zhCN } from "date-fns/locale";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { seckillApi } from "@/lib/api/seckill";
import { ActivityStatusConst, OrderStatusConst } from "@/types/api";

// ── 倒计时 Hook ────────────────────────────────────────────────────────────────
function useCountdown(targetDate: string | null) {
  const [remaining, setRemaining] = useState<string | null>(null);

  useEffect(() => {
    if (!targetDate) return;
    const target = new Date(targetDate);

    function tick() {
      const now = Date.now();
      const diff = target.getTime() - now;
      if (diff <= 0) {
        setRemaining("00:00:00");
        return;
      }
      const h = Math.floor(diff / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1_000);
      setRemaining(
        `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
      );
    }

    tick();
    const id = setInterval(tick, 1_000);
    return () => clearInterval(id);
  }, [targetDate]);

  return remaining;
}

// ── 订单状态展示 ───────────────────────────────────────────────────────────────
function OrderStatusPanel({ orderId }: { orderId: string }) {
  const { data: order, isLoading } = useQuery({
    queryKey: ["order", orderId],
    queryFn: async () => {
      const { data: res } = await seckillApi.getOrder(orderId);
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
    // 处理中时每 2s 轮询一次，成功或失败后停止
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === OrderStatusConst.PROCESSING) return 2_000;
      return false;
    },
  });

  if (isLoading || !order) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" />
        查询订单结果中...
      </div>
    );
  }

  if (order.status === OrderStatusConst.PROCESSING) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
        <Timer className="size-4 shrink-0 animate-pulse" />
        <div>
          <p className="font-medium">排队中，请稍候…</p>
          <p className="text-xs text-amber-600">系统正在处理您的报名，请勿重复操作</p>
        </div>
      </div>
    );
  }

  if (order.status === OrderStatusConst.SUCCESS) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
        <CheckCircle2 className="size-4 shrink-0" />
        <div>
          <p className="font-medium">报名成功！</p>
          <p className="text-xs text-green-600">订单号：{orderId}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      <XCircle className="size-4 shrink-0" />
      <div>
        <p className="font-medium">报名失败</p>
        {order.cancelReason && (
          <p className="text-xs text-red-600">{order.cancelReason}</p>
        )}
      </div>
    </div>
  );
}

// ── 主页面 ─────────────────────────────────────────────────────────────────────
export default function ActivityDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: activityId } = use(params);
  const [orderId, setOrderId] = useState<string | null>(null);
  const [bookError, setBookError] = useState<string | null>(null);

  const { data: activity, isLoading } = useQuery({
    queryKey: ["activity", activityId],
    queryFn: async () => {
      const { data: res } = await seckillApi.getActivity(activityId);
      if (res.code !== 200) throw new Error(res.msg);
      return res.data;
    },
  });

  // 倒计时目标：未开始 → 报名开始时间；进行中 → 报名截止时间
  const countdownTarget =
    activity?.status === ActivityStatusConst.NOT_STARTED
      ? activity.startTime
      : activity?.status === ActivityStatusConst.ONGOING
        ? activity.endTime
        : null;
  const countdown = useCountdown(countdownTarget);

  const bookMut = useMutation({
    mutationFn: () =>
      seckillApi.book(activityId).then(({ data }) => {
        if (data.code !== 200) throw new Error(data.msg ?? "报名失败");
        return data.data.orderId;
      }),
    onSuccess: (oid) => {
      setOrderId(oid);
      setBookError(null);
    },
    onError: (e: Error) => setBookError(e.message),
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-20">
        <Loader2 className="size-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!activity) {
    return <div className="p-6 text-muted-foreground">活动不存在</div>;
  }

  const isOngoing = activity.status === ActivityStatusConst.ONGOING;
  const hasEnded =
    activity.status === ActivityStatusConst.ENDED ||
    activity.status === ActivityStatusConst.CANCELLED;
  const hasNotStarted = activity.status === ActivityStatusConst.NOT_STARTED;
  const isSoldOut = activity.availableStock === 0;

  const STATUS_LABEL: Record<number, string> = {
    [ActivityStatusConst.NOT_STARTED]: "未开始",
    [ActivityStatusConst.ONGOING]: "报名中",
    [ActivityStatusConst.ENDED]: "已结束",
    [ActivityStatusConst.CANCELLED]: "已取消",
  };

  return (
    <div className="p-6 space-y-6 max-w-2xl mx-auto">
      {/* 返回 */}
      <Link
        href="/activities"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        返回活动列表
      </Link>

      {/* 封面 */}
      {activity.coverUrl && (
        <div className="rounded-xl overflow-hidden h-56 bg-muted">
          <img
            src={activity.coverUrl}
            alt={activity.title}
            className="w-full h-full object-cover"
          />
        </div>
      )}

      {/* 标题 + 状态 */}
      <div className="flex items-start gap-3">
        <h1 className="flex-1 text-2xl font-bold">{activity.title}</h1>
        <Badge
          variant={
            isOngoing ? "default" : hasEnded ? "outline" : "secondary"
          }
          className="shrink-0"
        >
          {STATUS_LABEL[activity.status]}
        </Badge>
      </div>

      {/* 活动信息 */}
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 text-sm">
        <div className="flex items-center gap-2 text-muted-foreground">
          <CalendarDays className="size-4 shrink-0" />
          活动时间：{format(new Date(activity.activityTime), "yyyy-MM-dd HH:mm")}
        </div>
        {activity.location && (
          <div className="flex items-center gap-2 text-muted-foreground">
            <MapPin className="size-4 shrink-0" />
            {activity.location}
          </div>
        )}
        <div className="flex items-center gap-2 text-muted-foreground">
          <Users className="size-4 shrink-0" />
          剩余名额：{activity.availableStock} / {activity.totalStock}
        </div>
        <div className="flex items-center gap-2 text-muted-foreground">
          <Clock className="size-4 shrink-0" />
          报名截止：{format(new Date(activity.endTime), "MM-dd HH:mm")}
        </div>
      </div>

      {/* 倒计时 */}
      {countdown && !hasEnded && (
        <div className="rounded-lg bg-muted/60 px-4 py-3 flex items-center gap-3">
          <Timer className="size-5 text-primary" />
          <div>
            <p className="text-xs text-muted-foreground">
              {hasNotStarted ? "距报名开始" : "距报名截止"}
            </p>
            <p className="text-2xl font-mono font-bold tracking-widest text-primary">
              {countdown}
            </p>
          </div>
        </div>
      )}

      {/* 活动简介 */}
      {activity.description && (
        <>
          <Separator />
          <div className="space-y-2">
            <h2 className="font-semibold">活动简介</h2>
            <p className="text-sm text-muted-foreground whitespace-pre-wrap leading-relaxed">
              {activity.description}
            </p>
          </div>
        </>
      )}

      <Separator />

      {/* 报名区域 */}
      <div className="space-y-3">
        {/* 已有订单结果 */}
        {orderId && <OrderStatusPanel orderId={orderId} />}

        {/* 报名错误提示 */}
        {bookError && !orderId && (
          <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <XCircle className="size-4 shrink-0" />
            {bookError}
          </div>
        )}

        {/* 报名按钮 */}
        {!orderId && (
          <Button
            size="lg"
            className="w-full"
            disabled={
              !isOngoing ||
              isSoldOut ||
              bookMut.isPending
            }
            onClick={() => bookMut.mutate()}
          >
            {bookMut.isPending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : isSoldOut ? (
              "名额已满"
            ) : hasEnded ? (
              "报名已结束"
            ) : hasNotStarted ? (
              "报名尚未开始"
            ) : (
              "立即报名"
            )}
          </Button>
        )}

        {isOngoing && !isSoldOut && !orderId && (
          <p className="text-xs text-center text-muted-foreground">
            报名成功后将异步确认，请稍候片刻
          </p>
        )}
      </div>
    </div>
  );
}

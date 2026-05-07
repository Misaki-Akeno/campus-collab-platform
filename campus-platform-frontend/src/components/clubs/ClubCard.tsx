import Image from "next/image";
import Link from "next/link";
import { Users } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { Club, ClubStatus } from "@/types/api";

const STATUS_MAP: Record<ClubStatus, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  0: { label: "审核中", variant: "secondary" },
  1: { label: "正常", variant: "default" },
  2: { label: "已解散", variant: "destructive" },
  3: { label: "已拒绝", variant: "destructive" },
};

export function ClubCard({ club }: { club: Club }) {
  const status = STATUS_MAP[club.status] ?? STATUS_MAP[0];

  return (
    <Link href={`/clubs/${club.id}`}>
      <Card className="h-full hover:shadow-md transition-shadow cursor-pointer">
        <CardHeader className="pb-3">
          <div className="flex items-start gap-3">
            <div className="relative size-12 shrink-0 overflow-hidden rounded-lg bg-muted">
              {club.logoUrl ? (
                <Image
                  src={club.logoUrl}
                  alt={club.name}
                  fill
                  className="object-cover"
                />
              ) : (
                <div className="flex size-full items-center justify-center text-xl">
                  🎭
                </div>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <CardTitle className="text-base truncate">{club.name}</CardTitle>
                <Badge variant={status.variant} className="text-xs shrink-0">
                  {status.label}
                </Badge>
              </div>
              {club.category && (
                <p className="text-xs text-muted-foreground mt-0.5">{club.category}</p>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent className="pb-4">
          <p className="text-sm text-muted-foreground line-clamp-2 mb-3">
            {club.description || "暂无简介"}
          </p>
          <div className="flex items-center gap-1 text-xs text-muted-foreground">
            <Users className="size-3.5" />
            <span>{club.memberCount} 名成员</span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}

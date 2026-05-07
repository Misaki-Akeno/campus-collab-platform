"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Home,
  Users,
  Calendar,
  MessageSquare,
  Upload,
  Bot,
  User,
  LayoutDashboard,
  LogOut,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { useAuthStore } from "@/store/auth";
import { userApi } from "@/lib/api/user";
import { UserRole } from "@/types/api";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

const navItems = [
  { href: "/", label: "首页", icon: Home },
  { href: "/clubs", label: "社团", icon: Users },
  { href: "/activities", label: "活动", icon: Calendar },
  { href: "/im", label: "消息", icon: MessageSquare },
  { href: "/files", label: "文件", icon: Upload },
  { href: "/ai", label: "AI 助手", icon: Bot },
];

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, accessToken, clearAuth } = useAuthStore();

  async function handleLogout() {
    if (accessToken) {
      await userApi.logout(accessToken).catch(() => null);
    }
    clearAuth();
    router.push("/login");
  }

  return (
    <aside className="flex h-full w-56 flex-col border-r bg-sidebar">
      {/* Logo */}
      <div className="flex h-14 items-center px-4 font-bold text-sidebar-foreground">
        🎓 社团平台
      </div>
      <Separator />

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-2 py-3 space-y-1">
        {navItems.map(({ href, label, icon: Icon }) => {
          const active =
            href === "/" ? pathname === "/" : pathname.startsWith(href);
          return (
            <Link key={href} href={href}>
              <span
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent/60"
                )}
              >
                <Icon className="size-4 shrink-0" />
                {label}
              </span>
            </Link>
          );
        })}

        {/* Admin 入口（仅管理员可见） */}
        {user?.role === UserRole.ADMIN && (
          <>
            <Separator className="my-2" />
            <Link href="/admin">
              <span
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  pathname.startsWith("/admin")
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent/60"
                )}
              >
                <LayoutDashboard className="size-4 shrink-0" />
                管理后台
              </span>
            </Link>
          </>
        )}
      </nav>

      <Separator />

      {/* 用户信息 + 登出 */}
      <div className="p-3 space-y-2">
        <Link href="/profile">
          <div className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-sidebar-accent/60 transition-colors cursor-pointer">
            <Avatar className="size-7">
              <AvatarImage src={user?.avatarUrl} alt={user?.nickname} />
              <AvatarFallback>
                {user?.nickname?.charAt(0).toUpperCase() ?? "U"}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-sidebar-foreground truncate">
                {user?.nickname ?? "未登录"}
              </p>
              <p className="text-[10px] text-muted-foreground truncate">
                {user?.username}
              </p>
            </div>
            <User className="size-3 text-muted-foreground" />
          </div>
        </Link>
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start text-muted-foreground hover:text-destructive"
          onClick={handleLogout}
        >
          <LogOut className="size-4" />
          退出登录
        </Button>
      </div>
    </aside>
  );
}

// 管理侧专用 Sidebar
const adminNavItems = [
  { href: "/admin", label: "数据看板", icon: LayoutDashboard },
  { href: "/admin/clubs", label: "社团管理", icon: Users },
  { href: "/admin/users", label: "用户管理", icon: User },
  { href: "/admin/activities", label: "活动看板", icon: Calendar },
];

export function AdminSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { clearAuth, accessToken } = useAuthStore();

  async function handleLogout() {
    if (accessToken) await userApi.logout(accessToken).catch(() => null);
    clearAuth();
    router.push("/login");
  }

  return (
    <aside className="flex h-full w-56 flex-col border-r bg-sidebar">
      <div className="flex h-14 items-center gap-2 px-4 font-bold text-sidebar-foreground">
        <LayoutDashboard className="size-4" />
        管理后台
      </div>
      <Separator />
      <nav className="flex-1 px-2 py-3 space-y-1">
        {adminNavItems.map(({ href, label, icon: Icon }) => {
          const active =
            href === "/admin" ? pathname === "/admin" : pathname.startsWith(href);
          return (
            <Link key={href} href={href}>
              <span
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent/60"
                )}
              >
                <Icon className="size-4 shrink-0" />
                {label}
              </span>
            </Link>
          );
        })}
        <Separator className="my-2" />
        <Link href="/">
          <span className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-sidebar-foreground hover:bg-sidebar-accent/60 transition-colors">
            <Home className="size-4 shrink-0" />
            返回用户端
          </span>
        </Link>
      </nav>
      <Separator />
      <div className="p-3">
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start text-muted-foreground hover:text-destructive"
          onClick={handleLogout}
        >
          <LogOut className="size-4" />
          退出登录
        </Button>
      </div>
    </aside>
  );
}

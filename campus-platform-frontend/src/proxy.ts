import { NextRequest, NextResponse } from "next/server";

const PUBLIC_PATHS = ["/login", "/register"];
const ADMIN_PATHS = ["/admin"];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const hasSession = request.cookies.has("campus-session");
  const role = request.cookies.get("campus-role")?.value;

  // 已登录用户访问认证页 → 跳首页
  if (hasSession && PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  // 未登录访问受保护页面 → 跳登录
  if (!hasSession && !PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirect", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // 非管理员访问 /admin → 跳首页
  if (
    hasSession &&
    ADMIN_PATHS.some((p) => pathname.startsWith(p)) &&
    role !== "2"
  ) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/).*)"],
};

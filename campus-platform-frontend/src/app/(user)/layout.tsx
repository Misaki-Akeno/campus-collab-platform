import type { ReactNode } from "react";
import { Sidebar } from "@/components/layout/Sidebar";

export default function UserLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <main className="flex-1 overflow-y-auto bg-background">
        <div className="h-full">{children}</div>
      </main>
    </div>
  );
}

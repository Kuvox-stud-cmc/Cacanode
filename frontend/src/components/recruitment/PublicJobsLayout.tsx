import PublicNavbar from "@/components/landing/PublicNavbar";

export function PublicJobsLayout({ children }: { children: React.ReactNode }) {
  return <div className="min-h-screen bg-slate-50"><PublicNavbar /><main className="mx-auto max-w-6xl px-4 pb-16 pt-24">{children}</main></div>;
}

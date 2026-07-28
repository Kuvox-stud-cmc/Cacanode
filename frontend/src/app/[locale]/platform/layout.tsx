import { PlatformAdminShell } from "@/components/platform/PlatformAdminShell"

export default function PlatformLayout({ children }: { children: React.ReactNode }) {
  return <PlatformAdminShell>{children}</PlatformAdminShell>
}

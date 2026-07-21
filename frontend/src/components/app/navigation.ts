import {
  BarChart2,
  BookOpen,
  FileText,
  LayoutDashboard,
  MessageSquare,
  Settings,
  TicketCheck,
  Users,
  type LucideIcon,
} from "lucide-react"

export type AppNavigationItem = {
  href: string
  labelKey: "chat" | "dashboard" | "documents" | "conversations" | "tickets" | "analytics" | "documentation" | "users" | "settings"
  icon: LucideIcon
  tenantAdminOnly?: boolean
  placement?: "main" | "footer"
}

export const appNavigation: AppNavigationItem[] = [
  { href: "/", labelKey: "chat", icon: MessageSquare },
  { href: "/dashboard", labelKey: "dashboard", icon: LayoutDashboard },
  { href: "/documents", labelKey: "documents", icon: FileText },
  { href: "/conversations", labelKey: "conversations", icon: MessageSquare },
  { href: "/tickets", labelKey: "tickets", icon: TicketCheck },
  { href: "/analytics", labelKey: "analytics", icon: BarChart2 },
  { href: "/documentation", labelKey: "documentation", icon: BookOpen, placement: "footer" },
  { href: "/users", labelKey: "users", icon: Users },
  { href: "/settings", labelKey: "settings", icon: Settings, tenantAdminOnly: true },
]

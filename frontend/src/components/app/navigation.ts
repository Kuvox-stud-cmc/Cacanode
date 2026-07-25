import {
  BarChart2,
  BookOpen,
  FileText,
  LayoutDashboard,
  MessageSquare,
  Settings,
  TicketCheck,
  Users,
  BriefcaseBusiness,
  type LucideIcon,
} from "lucide-react"

export type AppNavigationItem = {
  href: string
  labelKey: "chat" | "dashboard" | "documents" | "conversations" | "tickets" | "analytics" | "recruitment" | "documentation" | "users" | "settings"
  icon: LucideIcon
  tenantAdminOnly?: boolean
  placement?: "main" | "footer"
  recruitmentOnly?: boolean
  beta?: boolean
}

export const appNavigation: AppNavigationItem[] = [
  { href: "/", labelKey: "chat", icon: MessageSquare },
  { href: "/dashboard", labelKey: "dashboard", icon: LayoutDashboard },
  { href: "/documents", labelKey: "documents", icon: FileText },
  { href: "/conversations", labelKey: "conversations", icon: MessageSquare },
  { href: "/tickets", labelKey: "tickets", icon: TicketCheck },
  { href: "/analytics", labelKey: "analytics", icon: BarChart2 },
  { href: "/recruitment", labelKey: "recruitment", icon: BriefcaseBusiness, recruitmentOnly: true, beta: true },
  { href: "/documentation", labelKey: "documentation", icon: BookOpen, placement: "footer" },
  { href: "/users", labelKey: "users", icon: Users },
  { href: "/settings", labelKey: "settings", icon: Settings, tenantAdminOnly: true },
]

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
  label: string
  icon: LucideIcon
  tenantAdminOnly?: boolean
  placement?: "main" | "footer"
}

export const appNavigation: AppNavigationItem[] = [
  { href: "/", label: "Chat", icon: MessageSquare },
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/documents", label: "Documents", icon: FileText },
  { href: "/conversations", label: "Conversations", icon: MessageSquare },
  { href: "/tickets", label: "Tickets", icon: TicketCheck },
  { href: "/analytics", label: "Analytics", icon: BarChart2 },
  { href: "/documentation", label: "Documentation", icon: BookOpen, placement: "footer" },
  { href: "/users", label: "Users", icon: Users },
  { href: "/settings", label: "Settings", icon: Settings, tenantAdminOnly: true },
]

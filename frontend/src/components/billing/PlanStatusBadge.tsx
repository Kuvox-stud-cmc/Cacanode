"use client"

import { useTranslations } from "next-intl"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

export type PlanPresentation = {
  className: string
}

export function getPlanPresentation(
  plan: string | null | undefined,
  status?: string | null,
): PlanPresentation {
  if (status?.trim().toUpperCase() === "GRACE") {
    return {
      className: "border-red-300 bg-red-100 text-red-800",
    }
  }

  switch (plan?.trim().toUpperCase()) {
    case "TRIAL":
      return {
        className: "border-amber-300 bg-amber-100 text-amber-800",
      }
    case "FREE":
    case "STARTER":
      return {
        className: "border-slate-300 bg-slate-100 text-slate-700",
      }
    case "PRO":
      return {
        className: "border-indigo-300 bg-indigo-100 text-indigo-800",
      }
    case "BUSINESS":
    case "ENTERPRISE":
      return {
        className: "border-emerald-300 bg-emerald-100 text-emerald-800",
      }
    default:
      return {
        className: "border-slate-300 bg-slate-100 text-slate-700",
      }
  }
}

export function PlanStatusBadge({
  className,
  plan,
  status,
}: {
  className?: string
  plan: string | null | undefined
  status?: string | null
}) {
  const t = useTranslations("PlanBadge")
  const presentation = getPlanPresentation(plan, status)
  const normalizedPlan = plan?.trim().toUpperCase()
  const label = status?.trim().toUpperCase() === "GRACE"
    ? normalizedPlan === "BUSINESS" ? t("businessGrace") : t("proGrace")
    : normalizedPlan === "TRIAL" ? t("trial")
    : normalizedPlan === "FREE" || normalizedPlan === "STARTER" ? t("starter")
    : normalizedPlan === "PRO" ? t("pro")
    : normalizedPlan === "BUSINESS" ? t("business")
    : normalizedPlan === "ENTERPRISE" ? t("enterprise")
    : t("current")
  return (
    <Badge
      variant="outline"
      className={cn("font-semibold", presentation.className, className)}
    >
      {label}
    </Badge>
  )
}

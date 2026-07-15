"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import PublicNavbar from "@/components/landing/PublicNavbar";
import PricingSection from "@/components/landing/PricingSection";
import { useTokenRehydration } from "@/hooks/useTokenRehydration";

export default function PricingPage() {
  const router = useRouter();
  const status = useTokenRehydration();

  useEffect(() => {
    if (status === "authenticated") {
      router.replace("/settings?tab=quota");
    }
  }, [router, status]);

  if (status !== "unauthenticated") {
    return (
      <div className="grid min-h-dvh place-items-center bg-white">
        <Loader2 className="size-8 animate-spin text-indigo-600" aria-label="Loading" />
      </div>
    );
  }

  return (
    <div className="min-h-dvh bg-white">
      <PublicNavbar />
      <main className="pt-16">
        <PricingSection />
      </main>
    </div>
  );
}

"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { CircleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export type RecruitmentConfirmationOptions = {
  title: string;
  description: string;
  confirmLabel: string;
  destructive?: boolean;
};

export function useRecruitmentConfirmation() {
  const common = useTranslations("Common");
  const [options, setOptions] = useState<RecruitmentConfirmationOptions | null>(null);
  const resolver = useRef<((accepted: boolean) => void) | null>(null);

  const settle = useCallback((accepted: boolean) => {
    const resolve = resolver.current;
    resolver.current = null;
    setOptions(null);
    resolve?.(accepted);
  }, []);

  const confirm = useCallback((next: RecruitmentConfirmationOptions) => {
    resolver.current?.(false);
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
      setOptions(next);
    });
  }, []);

  useEffect(() => () => resolver.current?.(false), []);

  const confirmationDialog = (
    <Dialog open={Boolean(options)} onOpenChange={(open) => { if (!open) settle(false); }}>
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <div className="flex items-start gap-3">
            <span className={`mt-0.5 rounded-full p-2 ${options?.destructive ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"}`}>
              <CircleAlert className="size-5" aria-hidden="true" />
            </span>
            <div className="space-y-2">
              <DialogTitle>{options?.title}</DialogTitle>
              <DialogDescription>{options?.description}</DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => settle(false)}>{common("cancel")}</Button>
          <Button variant={options?.destructive ? "destructive" : "default"} onClick={() => settle(true)}>
            {options?.confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

  return { confirm, confirmationDialog };
}

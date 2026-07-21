"use client";


import { Suspense, useEffect, useMemo, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import type { CSSProperties } from "react";
import { useSearchParams } from "next/navigation";
import { useRouter } from "@/i18n/navigation";
import { Check, Code2, Copy, CreditCard, KeyRound, Loader2, Play, Plus, RefreshCw, RotateCcw, ShieldCheck, Trash2 } from "lucide-react";
import toast from "react-hot-toast";
import PlanCardGrid, {
  normalizePlanId,
  type PlanId,
} from "@/components/landing/PlanCardGrid";
import { useAuthStore } from "@/components/providers/StoreProvider";
import { useApiClient } from "@/hooks/useApiClient";
import { publicConfig } from "@/lib/public-config";
import InteractiveWidgetPreview from "@/components/widget/InteractiveWidgetPreview";
import {
  createIntegrationToken,
  createWebhook,
  deleteWidgetIcon,
  deleteWebhook,
  downloadWidgetIcon,
  generateWidgetEmbed,
  getWidgetEmbed,
  getWidgetSettings,
  listIntegrationTokens,
  listWebhooks,
  revokeIntegrationToken,
  rotateIntegrationToken,
  rotateWebhookSecret,
  testWebhook,
  updateWidgetSettings,
  uploadWidgetIcon,
  type IntegrationScope,
  type IntegrationToken,
  type WebhookEndpoint,
  type WidgetSettings,
  type WidgetEmbed,
} from "@/lib/integrations-api";
import {
  getCustomerAnswerPrompt,
  updateCustomerAnswerPrompt,
  type CustomerAnswerPromptSettings,
} from "@/lib/tenant-settings-api";
import { Badge } from "@/components/ui/badge";
import { PlanStatusBadge } from "@/components/billing/PlanStatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  createBillingCheckout,
  downgradeBilling,
  getBillingAccount,
  getBillingPayment,
  getPublicBillingPlans,
  type BillingAccount,
  type BillingInterval,
  type BillingPlan,
  type BillingUsage,
} from "@/lib/billing-api";

const WEBHOOK_EVENTS = ["conversation.started", "conversation.closed", "ticket.created"];
const WIDGET_ICON_STYLES: WidgetSettings["iconStyle"][] = [
  "STANDARD",
  "GLOW",
  "PULSE",
  "SOFT_SHADOW",
];

function widgetIconStyleClass(style: WidgetSettings["iconStyle"]): string {
  return `widget-launcher-style widget-launcher-style--${(style ?? "STANDARD").toLowerCase().replace("_", "-")}`;
}

function widgetIconStyleVars(color: string): CSSProperties {
  return { backgroundColor: color, "--widget-launcher-color": color } as CSSProperties;
}

function usagePercent(usage: BillingUsage): number {
  if (usage.limit === null || usage.limit === 0) return 0;
  return Math.min(100, Math.round((usage.used / usage.limit) * 100));
}

function SettingsPageContent() {
  const t = useTranslations("Settings");
  const planT = useTranslations("PlanBadge");
  const format = useFormatter();
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const setPlan = useAuthStore((state) => state.setPlan);
  const { request } = useApiClient();
  const [loading, setLoading] = useState(true);
  const [widget, setWidget] = useState<WidgetSettings | null>(null);
  const [widgetIconPreview, setWidgetIconPreview] = useState<string | null>(null);
  const [widgetIconBusy, setWidgetIconBusy] = useState(false);
  const [widgetEmbed, setWidgetEmbed] = useState<WidgetEmbed | null>(null);
  const [widgetEmbedLoading, setWidgetEmbedLoading] = useState(true);
  const [widgetEmbedError, setWidgetEmbedError] = useState<string | null>(null);
  const [managedWidgetSecret, setManagedWidgetSecret] = useState<string | null>(null);
  const [origins, setOrigins] = useState("");
  const [tokens, setTokens] = useState<IntegrationToken[]>([]);
  const [showDeletedTokens, setShowDeletedTokens] = useState(false);
  const [webhooks, setWebhooks] = useState<WebhookEndpoint[]>([]);
  const [tokenDialog, setTokenDialog] = useState(false);
  const [tokenName, setTokenName] = useState("");
  const [tokenExpiry, setTokenExpiry] = useState("");
  const [tokenScopes, setTokenScopes] = useState<IntegrationScope[]>(["widget:chat"]);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [newSecretScopes, setNewSecretScopes] = useState<IntegrationScope[]>([]);
  const [webhookDialog, setWebhookDialog] = useState(false);
  const [webhookName, setWebhookName] = useState("");
  const [webhookUrl, setWebhookUrl] = useState("");
  const [webhookEvents, setWebhookEvents] = useState<string[]>(WEBHOOK_EVENTS);
  const [webhookSecret, setWebhookSecret] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [copied, setCopied] = useState(false);
  const [promptSettings, setPromptSettings] = useState<CustomerAnswerPromptSettings | null>(null);
  const [promptDraft, setPromptDraft] = useState("");
  const [restorePromptDialog, setRestorePromptDialog] = useState(false);
  const [planDialog, setPlanDialog] = useState(false);
  const [billingAccount, setBillingAccount] = useState<BillingAccount | null>(null);
  const [billingPlans, setBillingPlans] = useState<BillingPlan[]>([]);
  const [billingInterval, setBillingInterval] = useState<BillingInterval>("MONTHLY");
  const [billingBusy, setBillingBusy] = useState(false);
  const formatDate = (value: string | null) => value ? format.dateTime(new Date(value), { dateStyle: "medium", timeStyle: "short" }) : t("never");
  const usageLabel = (usage: BillingUsage, suffix = "") => `${format.number(usage.used)}${suffix} / ${usage.limit === null ? t("unlimited") : `${format.number(usage.limit)}${suffix}`}`;
  const planLabel = (plan: string | null | undefined, status?: string | null) => {
    if (status?.trim().toUpperCase() === "GRACE") return planT("grace");
    switch (plan?.trim().toUpperCase()) {
      case "TRIAL": return planT("trial");
      case "FREE":
      case "STARTER": return planT("starter");
      case "PRO": return planT("pro");
      case "BUSINESS":
      case "ENTERPRISE": return planT("enterprise");
      default: return planT("current");
    }
  };
  const billingIntervalLabel = (interval: BillingInterval) => interval === "ANNUAL"
    ? t("billing.intervals.ANNUAL")
    : t("billing.intervals.MONTHLY");

  useEffect(() => {
    if (!user) return;
    if (user.role !== "TENANT_ADMIN") {
      router.replace("/dashboard");
      return;
    }
    let cancelled = false;
    Promise.all([
      getWidgetSettings(request),
      (async () => {
        const embedResult = await getWidgetEmbed(request)
          .then((embed) => ({ embed, error: null as string | null }))
          .catch((error: unknown) => ({
            embed: null,
            error: error instanceof Error ? error.message : t("fallback.widgetCode"),
          }));
        return { ...embedResult, tokens: await listIntegrationTokens(request) };
      })(),
      listWebhooks(request),
      getCustomerAnswerPrompt(request),
      (async () => {
        const account = await getBillingAccount(request);
        const pending = account.pendingPayment;
        if (!pending || !["PENDING", "PROCESSING"].includes(pending.status)) return account;
        try {
          await getBillingPayment(request, pending.paymentId);
          return await getBillingAccount(request);
        } catch {
          return account;
        }
      })(),
      getPublicBillingPlans(),
    ])
      .then(([widgetResult, integrationResult, webhookResult, promptResult, accountResult, plansResult]) => {
        if (cancelled) return;
        setWidget({ ...widgetResult, iconStyle: widgetResult.iconStyle ?? "STANDARD" });
        setWidgetEmbed(integrationResult.embed);
        setWidgetEmbedError(integrationResult.error);
        setWidgetEmbedLoading(false);
        setOrigins(widgetResult.allowedOrigins.join("\n"));
        setTokens(integrationResult.tokens);
        setWebhooks(webhookResult);
        setPromptSettings(promptResult);
        setPromptDraft(promptResult.prompt);
        setBillingAccount(accountResult);
        setBillingPlans(plansResult);
        setBillingInterval(accountResult.interval ?? "MONTHLY");
        setPlan(accountResult.planCode);
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : t("fallback.load")))
      .finally(() => {
        if (!cancelled) {
          setWidgetEmbedLoading(false);
          setLoading(false);
        }
      });
    return () => { cancelled = true; };
  }, [request, router, setPlan, t, user]);

  useEffect(() => {
    if (!widget?.iconUrl) return;
    let cancelled = false;
    downloadWidgetIcon(request)
      .then((blob) => {
        if (!cancelled) setWidgetIconPreview(URL.createObjectURL(blob));
      })
      .catch(() => {
        if (!cancelled) setWidgetIconPreview(null);
      });
    return () => { cancelled = true; };
  }, [request, widget?.iconUrl]);

  useEffect(() => () => {
    if (widgetIconPreview) URL.revokeObjectURL(widgetIconPreview);
  }, [widgetIconPreview]);

  const returnedFromPayOs = searchParams.get("payment") === "return"
    || searchParams.get("status") === "PAID";
  const returnedPaymentId = searchParams.get("paymentId")
    ?? (returnedFromPayOs ? billingAccount?.pendingPayment?.paymentId ?? null : null);

  useEffect(() => {
    if (!returnedPaymentId || !user) return;
    let cancelled = false;
    void (async () => {
      for (let attempt = 0; attempt < 10 && !cancelled; attempt += 1) {
        const payment = await getBillingPayment(request, returnedPaymentId);
        if (payment.status === "PAID") {
          const account = await getBillingAccount(request);
          if (cancelled) return;
          setBillingAccount(account);
          setPlan(account.planCode);
          toast.success(t("billing.paymentActivated"));
          router.replace("/settings?tab=quota");
          return;
        }
        if (["CANCELLED", "EXPIRED", "FAILED", "REVIEW"].includes(payment.status)) {
          toast.error(payment.failureReason ?? t("billing.paymentFailed", {
            status: t(`billing.statuses.${payment.status}` as "billing.statuses.FAILED"),
          }));
          router.replace("/settings?tab=quota");
          return;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 3000));
      }
      if (!cancelled) toast(t("billing.paymentPending"));
    })().catch((error) => toast.error(error instanceof Error ? error.message : t("fallback.verifyPayment")));
    return () => { cancelled = true; };
  }, [request, returnedPaymentId, router, setPlan, t, user]);

  const promptDirty = promptSettings !== null && promptDraft !== promptSettings.prompt;
  const promptLength = Array.from(promptDraft).length;
  const promptInvalid = promptDraft.trim().length === 0 || promptLength > 4000;
  const backendPlan = normalizePlanId(billingAccount?.planCode ?? user?.plan);
  const embedCode = useMemo(() => {
    if (!newSecretScopes.includes("widget:chat")) return null;
    const widgetUrl = publicConfig.widgetUrl ?? "/widget/v1/cacanode-chat.js";
    return `<script async src="${widgetUrl}" data-token="\${CACANODE_WIDGET_TOKEN}"></script>`;
  }, [newSecretScopes]);

  const managedWidgetEmbedCode = useMemo(() => {
    const configuredUrl = publicConfig.widgetUrl;
    const fallbackUrl = publicConfig.apiBaseUrl
      ? new URL("/widget/v1/cacanode-chat.js", publicConfig.apiBaseUrl).toString()
      : "/widget/v1/cacanode-chat.js";
    return `<script async src="${configuredUrl ?? fallbackUrl}" data-token="\${CACANODE_WIDGET_TOKEN}"></script>`;
  }, []);

  async function saveWidget() {
    if (!widget) return;
    setSaving(true);
    try {
      const updated = await updateWidgetSettings(request, {
        ...widget,
        allowedOrigins: origins.split(/[,\n]/).map((value) => value.trim()).filter(Boolean),
      });
      setWidget(updated);
      setOrigins(updated.allowedOrigins.join("\n"));
      toast.success(t("toast.widgetSaved"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.saveWidget"));
    } finally {
      setSaving(false);
    }
  }

  async function generateManagedWidgetToken() {
    setWidgetEmbedLoading(true);
    setWidgetEmbedError(null);
    try {
      const generated = await generateWidgetEmbed(request);
      setWidgetEmbed({
        tokenId: generated.tokenId,
        tokenPrefix: generated.tokenPrefix,
        configured: true,
        previewToken: generated.previewToken,
      });
      setManagedWidgetSecret(generated.secret);
      setWidget((current) => current ? { ...current, active: true } : current);
      setTokens(await listIntegrationTokens(request, showDeletedTokens));
      toast.success(t("toast.widgetTokenGenerated"));
    } catch (error) {
      setWidgetEmbedError(error instanceof Error ? error.message : t("fallback.generateWidgetToken"));
    } finally {
      setWidgetEmbedLoading(false);
    }
  }

  async function changeDeletedTokenVisibility(checked: boolean) {
    setShowDeletedTokens(checked);
    try {
      setTokens(await listIntegrationTokens(request, checked));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.loadTokens"));
    }
  }

  async function uploadIcon(file: File | undefined) {
    if (!file) return;
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size > 2 * 1024 * 1024) {
      toast.error(t("validation.icon"));
      return;
    }
    setWidgetIconBusy(true);
    try {
      const updated = await uploadWidgetIcon(request, file);
      setWidget(updated);
      try {
        const blob = await downloadWidgetIcon(request);
        setWidgetIconPreview(URL.createObjectURL(blob));
      } catch {
        setWidgetIconPreview(null);
      }
      toast.success(t("toast.iconUploaded"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.uploadIcon"));
    } finally {
      setWidgetIconBusy(false);
    }
  }

  async function removeIcon() {
    setWidgetIconBusy(true);
    try {
      await deleteWidgetIcon(request);
      setWidget((current) => current ? { ...current, iconUrl: null } : current);
      setWidgetIconPreview(null);
      toast.success(t("toast.iconRemoved"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.removeIcon"));
    } finally {
      setWidgetIconBusy(false);
    }
  }

  async function saveCustomerPrompt() {
    if (!promptDirty || promptInvalid) return;
    setSaving(true);
    try {
      const updated = await updateCustomerAnswerPrompt(request, promptDraft);
      setPromptSettings(updated);
      setPromptDraft(updated.prompt);
      toast.success(t("toast.promptSaved"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.savePrompt"));
    } finally {
      setSaving(false);
    }
  }

  async function restoreDefaultPrompt() {
    setSaving(true);
    try {
      const updated = await updateCustomerAnswerPrompt(request, "");
      setPromptSettings(updated);
      setPromptDraft(updated.prompt);
      setRestorePromptDialog(false);
      toast.success(t("toast.defaultPromptRestored"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.restorePrompt"));
    } finally {
      setSaving(false);
    }
  }

  async function createToken() {
    if (!tokenName.trim() || tokenScopes.length === 0) return;
    setSaving(true);
    try {
      const created = await createIntegrationToken(request, {
        name: tokenName.trim(),
        scopes: tokenScopes,
        expiresAt: tokenExpiry ? `${tokenExpiry}T23:59:59` : null,
      });
      setTokens((current) => [created.token, ...current]);
      setNewSecret(created.secret);
      setNewSecretScopes(created.token.scopes);
      setTokenDialog(false);
      setTokenName("");
      setTokenExpiry("");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.createToken"));
    } finally {
      setSaving(false);
    }
  }

  async function revokeToken(id: string) {
    try {
      const managed = widgetEmbed?.tokenId === id;
      await revokeIntegrationToken(request, id);
      setTokens((current) => showDeletedTokens
        ? current.map((token) => token.id === id
          ? { ...token, revokedAt: new Date().toISOString() }
          : token)
        : current.filter((token) => token.id !== id));
      if (managed) {
        setWidgetEmbed({ tokenId: null, tokenPrefix: null, configured: false, previewToken: null });
        setManagedWidgetSecret(null);
        setWidget((current) => current ? { ...current, active: false } : current);
      }
      toast.success(t("toast.tokenDeleted"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.revokeToken"));
    }
  }

  async function rotateToken(id: string) {
    try {
      const created = await rotateIntegrationToken(request, id);
      setTokens((current) => [created.token, ...current.map((token) => (
        token.id === id ? { ...token, revokedAt: new Date().toISOString() } : token
      ))]);
      setNewSecret(created.secret);
      setNewSecretScopes(created.token.scopes);
      toast.success(t("toast.tokenRotated"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.rotateToken"));
    }
  }

  async function createWebhookEndpoint() {
    if (!webhookName.trim() || !webhookUrl.trim() || webhookEvents.length === 0) return;
    setSaving(true);
    try {
      const created = await createWebhook(request, {
        name: webhookName.trim(), url: webhookUrl.trim(), events: webhookEvents, active: true,
      });
      setWebhooks((current) => [created.endpoint, ...current]);
      setWebhookSecret(created.signingSecret);
      setWebhookDialog(false);
      setWebhookName("");
      setWebhookUrl("");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.createWebhook"));
    } finally {
      setSaving(false);
    }
  }

  async function sendWebhookTest(endpointId: string) {
    try {
      await testWebhook(request, endpointId);
      toast.success(t("webhooks.testQueued"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.testWebhook"));
    }
  }

  async function rotateSigningSecret(endpointId: string) {
    try {
      const rotated = await rotateWebhookSecret(request, endpointId);
      setWebhookSecret(rotated.signingSecret);
      toast.success(t("webhooks.secretRotated"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.rotateWebhookSecret"));
    }
  }

  async function removeWebhookEndpoint(endpointId: string) {
    try {
      await deleteWebhook(request, endpointId);
      setWebhooks((current) => current.filter((item) => item.id !== endpointId));
      toast.success(t("webhooks.deleted"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.deleteWebhook"));
    }
  }

  async function copyText(value: string) {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  }

  async function selectPlan(plan: PlanId, interval: BillingInterval) {
    setBillingBusy(true);
    try {
      if (plan === "pro") {
        const checkout = await createBillingCheckout(request, interval);
        window.location.assign(checkout.checkoutUrl);
        return;
      }
      if (plan === "enterprise") {
        const enterprise = billingPlans.find((item) => item.planCode === "ENTERPRISE");
        window.location.assign(enterprise?.salesUrl ?? "mailto:sales@cacanode.com");
        return;
      }
      if (!window.confirm(billingAccount?.planCode === "TRIAL"
        ? t("billing.confirmTrialDowngrade")
        : t("billing.confirmPaidDowngrade", { date: formatDate(billingAccount?.graceEndsAt ?? null) }))) return;
      const result = await downgradeBilling(request);
      setBillingAccount(result.account);
      setPlan(result.account.planCode);
      setPlanDialog(false);
      toast.success(result.scheduled
        ? t("billing.starterScheduledFor", { date: formatDate(result.effectiveAt) })
        : t("billing.switchedToStarter"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.updateBilling"));
    } finally {
      setBillingBusy(false);
    }
  }

  async function refreshBilling() {
    setBillingBusy(true);
    try {
      const pending = billingAccount?.pendingPayment;
      if (pending && ["PENDING", "PROCESSING"].includes(pending.status)) {
        await getBillingPayment(request, pending.paymentId);
      }
      const account = await getBillingAccount(request);
      setBillingAccount(account);
      setPlan(account.planCode);
      toast.success(t("billing.refreshed"));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("fallback.refreshBilling"));
    } finally {
      setBillingBusy(false);
    }
  }

  if (user?.role !== "TENANT_ADMIN") {
    return null;
  }

  if (loading) {
    return <div className="max-w-5xl space-y-4"><Skeleton className="h-8 w-40" /><Skeleton className="h-96 w-full" /></div>;
  }

  return (
    <div className="max-w-5xl space-y-6">
      <h2 className="text-xl font-semibold text-slate-800">{t("title")}</h2>
      <Tabs defaultValue={searchParams.get("tab") === "quota" ? "quota" : "widget"}>
        <TabsList className="mb-6 h-auto max-w-full flex-wrap justify-start">
          <TabsTrigger value="widget">{t("tabs.widget")}</TabsTrigger>
          <TabsTrigger value="ai-prompt">{t("tabs.prompt")}</TabsTrigger>
          <TabsTrigger value="quota">{t("tabs.quota")}</TabsTrigger>
          <TabsTrigger value="tokens">{t("tabs.tokens")}</TabsTrigger>
          <TabsTrigger value="webhooks">{t("tabs.webhooks")}</TabsTrigger>
        </TabsList>

        <TabsContent value="widget">
          {widget && <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
            <Card>
              <CardHeader><CardTitle className="text-base">{t("widget.configuration")}</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-1.5"><Label>{t("widget.displayName")}</Label><Input value={widget.displayName} onChange={(event) => setWidget({ ...widget, displayName: event.target.value })} /></div>
                <div className="space-y-1.5"><Label>{t("widget.welcomeMessage")}</Label><textarea className="min-h-24 w-full rounded-md border border-slate-200 p-3 text-sm" value={widget.welcomeMessage} onChange={(event) => setWidget({ ...widget, welcomeMessage: event.target.value })} /></div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1.5"><Label>{t("widget.primaryColor")}</Label><div className="flex gap-2"><input type="color" className="h-9 w-12" value={widget.primaryColor} onChange={(event) => setWidget({ ...widget, primaryColor: event.target.value })} /><Input value={widget.primaryColor} onChange={(event) => setWidget({ ...widget, primaryColor: event.target.value })} /></div></div>
                  <div className="space-y-1.5"><Label>{t("widget.position")}</Label><select className="h-9 w-full rounded-md border border-slate-200 px-3 text-sm" value={widget.position} onChange={(event) => setWidget({ ...widget, position: event.target.value as WidgetSettings["position"] })}><option value="BOTTOM_RIGHT">{t("widget.bottomRight")}</option><option value="BOTTOM_LEFT">{t("widget.bottomLeft")}</option></select></div>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="widget-icon">{t("widget.icon")}</Label>
                  <div className="flex flex-wrap items-center gap-3">
                    <label htmlFor="widget-icon" className="inline-flex h-9 cursor-pointer items-center rounded-md border border-slate-200 bg-white px-3 text-sm font-medium hover:bg-slate-50">
                      {widgetIconBusy ? t("widget.uploading") : widget.iconUrl ? t("widget.replaceIcon") : t("widget.uploadIcon")}
                    </label>
                    <input id="widget-icon" className="sr-only" type="file" accept="image/png,image/jpeg,image/webp" disabled={widgetIconBusy}
                      onChange={(event) => { void uploadIcon(event.target.files?.[0]); event.currentTarget.value = ""; }} />
                    {widget.iconUrl && <Button type="button" variant="outline" onClick={() => void removeIcon()} disabled={widgetIconBusy}><Trash2 />{t("widget.remove")}</Button>}
                  </div>
                  <p className="text-xs text-slate-500">{t("widget.iconHelp")}</p>
                </div>
                <div className="space-y-2">
                  <Label>{t("widget.iconStyle")}</Label>
                  <div className="grid grid-cols-2 gap-2">
                    {WIDGET_ICON_STYLES.map((option) => (
                      <button key={option} type="button"
                        aria-pressed={widget.iconStyle === option}
                        onClick={() => setWidget({ ...widget, iconStyle: option })}
                        className={`flex items-center gap-3 rounded-md border p-3 text-left transition ${widget.iconStyle === option ? "border-indigo-500 bg-indigo-50 ring-1 ring-indigo-200" : "border-slate-200 bg-white hover:bg-slate-50"}`}>
                        <span className={`${widgetIconStyleClass(option)} grid size-9 shrink-0 place-items-center rounded-full text-xs text-white`}
                          style={widgetIconStyleVars(widget.primaryColor)} aria-hidden="true">?</span>
                        <span className="min-w-0"><strong className="block text-sm text-slate-800">{t(`widget.iconStyles.${option}.label` as "widget.iconStyles.STANDARD.label")}</strong><span className="block text-xs text-slate-500">{t(`widget.iconStyles.${option}.description` as "widget.iconStyles.STANDARD.description")}</span></span>
                      </button>
                    ))}
                  </div>
                </div>
                <div className="space-y-1.5"><Label>{t("widget.allowedOrigins")}</Label><textarea className="min-h-24 w-full rounded-md border border-slate-200 p-3 font-mono text-sm" value={origins} onChange={(event) => setOrigins(event.target.value)} placeholder="https://example.com" /><p className="text-xs text-amber-700">{t("widget.originsHelp")}</p></div>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={widget.active} onChange={(event) => setWidget({ ...widget, active: event.target.checked })} />{t("widget.active")}</label>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={widget.hideCacanodeBranding}
                  disabled={!billingAccount?.features.customBranding}
                  onChange={(event) => setWidget({ ...widget, hideCacanodeBranding: event.target.checked })} />{t("widget.hideBranding")}</label>
                {!billingAccount?.features.customBranding && <p className="text-xs text-amber-700">{t("widget.brandingHelp")}</p>}
                <Button onClick={() => void saveWidget()} disabled={saving}>{saving && <Loader2 className="animate-spin" />}{t("widget.save")}</Button>
                <section className="overflow-hidden rounded-xl border border-indigo-100 bg-gradient-to-br from-indigo-50 via-white to-violet-50 shadow-sm">
                  <div className="flex items-start gap-3 p-4 pb-3">
                    <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-indigo-600 text-white shadow-sm shadow-indigo-200"><Code2 className="size-5" /></span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold text-slate-900">{t("widget.installTitle")}</h3><Badge className={widgetEmbed?.configured ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-amber-200 bg-amber-50 text-amber-700"}>{widgetEmbed?.configured ? t("widget.tokenConfigured") : t("widget.tokenRequired")}</Badge></div>
                      <p className="mt-1 text-sm leading-5 text-slate-600">{t("widget.installDescriptionBeforeCode")} <code className="rounded bg-white px-1 py-0.5 text-xs text-indigo-700 shadow-sm">&lt;/body&gt;</code> {t("widget.installDescriptionAfterCode")}</p>
                    </div>
                  </div>
                  {managedWidgetSecret && <div className="mx-4 mb-3 rounded-lg border border-amber-200 bg-amber-50 p-4">
                    <p className="text-sm font-medium text-amber-900">{t("widget.tokenShownOnce")}</p>
                    <p className="mt-1 text-xs leading-5 text-amber-800">{t("widget.tokenShownOnceHelp")}</p>
                    <div className="mt-3 flex gap-2"><code className="min-w-0 flex-1 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{managedWidgetSecret}</code><Button type="button" variant="outline" size="icon" onClick={() => void copyText(managedWidgetSecret)}>{copied ? <Check /> : <Copy />}</Button></div>
                  </div>}
                  <div className="mx-4 mb-3 rounded-lg border border-slate-200 bg-white p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div><p className="text-sm font-medium text-slate-800">{widgetEmbed?.configured ? t("widget.replaceToken") : t("widget.generateToken")}</p><p className="mt-1 text-xs text-slate-500">{t("widget.replaceTokenHelp")}</p></div>
                      <Button type="button" variant={widgetEmbed?.configured ? "outline" : "default"} disabled={widgetEmbedLoading} onClick={() => void generateManagedWidgetToken()}>{widgetEmbedLoading && <Loader2 className="animate-spin" />}{widgetEmbed?.configured ? t("widget.regenerate") : t("widget.generate")}</Button>
                    </div>
                    {widgetEmbedError && <p className="mt-3 text-xs text-red-700">{widgetEmbedError}</p>}
                  </div>
                  <div className="mx-4 mb-3 overflow-hidden rounded-lg border border-slate-200 bg-white">
                    <div className="border-b border-slate-200 px-3 py-2 text-[10px] font-medium uppercase tracking-widest text-slate-500">{t("widget.environment")}</div>
                    <pre className="overflow-auto p-4 font-mono text-xs leading-6 text-slate-700">CACANODE_WIDGET_TOKEN=ccn_it_...</pre>
                  </div>
                  <div className="mx-4 overflow-hidden rounded-lg border border-slate-800 bg-slate-950 shadow-lg shadow-slate-900/10">
                    <div className="flex items-center justify-between border-b border-white/10 bg-white/[0.04] px-3 py-2">
                      <div className="flex items-center gap-1.5" aria-hidden="true"><span className="size-2.5 rounded-full bg-red-400" /><span className="size-2.5 rounded-full bg-amber-400" /><span className="size-2.5 rounded-full bg-emerald-400" /></div>
                      <span className="font-mono text-[10px] font-medium uppercase tracking-widest text-slate-400">HTML</span>
                      <Button type="button" size="sm" variant="ghost" aria-label={t("widget.copyInstallationCode")}
                        className="h-7 border border-white/10 bg-white/5 px-2 text-xs text-slate-200 hover:bg-white/10 hover:text-white"
                        onClick={() => void copyText(managedWidgetEmbedCode)}>{copied ? <><Check className="text-emerald-400" />{t("widget.copied")}</> : <><Copy />{t("widget.copy")}</>}</Button>
                    </div>
                    <pre className="max-h-36 overflow-auto whitespace-pre-wrap break-all p-4 font-mono text-xs leading-6 text-sky-200 selection:bg-indigo-500/40">{managedWidgetEmbedCode}</pre>
                  </div>
                  <div className="flex items-start gap-2.5 p-4 text-xs leading-5 text-slate-600">
                    <ShieldCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" />
                    <p><span className="font-medium text-slate-700">{t("widget.browserTokenNotice")}</span> · {t("widget.browserTokenHelp")}{widgetEmbed?.tokenPrefix ? ` ${t("widget.currentPrefix", { prefix: widgetEmbed.tokenPrefix })}` : ""}</p>
                  </div>
                </section>
              </CardContent>
            </Card>
            <InteractiveWidgetPreview widget={widget} token={managedWidgetSecret ?? widgetEmbed?.previewToken ?? null} iconPreviewUrl={widgetIconPreview} />
          </div>}
        </TabsContent>

        <TabsContent value="ai-prompt">
          <Card>
            <CardHeader className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <CardTitle className="text-base">{t("prompt.title")}</CardTitle>
                {promptSettings && (
                  <Badge variant="outline">
                    {promptSettings.usingDefault ? t("prompt.defaultBadge") : t("prompt.customBadge")}
                  </Badge>
                )}
              </div>
              <p className="text-sm text-slate-500">{t("prompt.description")}</p>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="customer-answer-prompt">{t("prompt.title")}</Label>
                <textarea
                  id="customer-answer-prompt"
                  className="min-h-64 w-full rounded-md border border-slate-200 p-3 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                  value={promptDraft}
                  onChange={(event) => setPromptDraft(event.target.value)}
                  aria-describedby="customer-answer-prompt-guidance"
                />
                <div className="flex items-start justify-between gap-4 text-xs">
                  <p id="customer-answer-prompt-guidance" className="text-amber-700">
                    {t("prompt.secretsHelp")}
                  </p>
                  <span className={promptLength > 4000 ? "font-medium text-red-600" : "text-slate-500"}>
                    {promptLength}/4000
                  </span>
                </div>
                {promptDraft.trim().length === 0 && (
                  <p className="text-xs text-red-600">{t("prompt.blankError")}</p>
                )}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
                <p className="text-xs text-slate-500">
                  {t("prompt.lastUpdated", { date: formatDate(promptSettings?.updatedAt ?? null) })}
                </p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setRestorePromptDialog(true)}
                    disabled={saving || (promptSettings?.usingDefault !== false && !promptDirty)}
                  >
                    <RotateCcw />{t("prompt.restore")}
                  </Button>
                  <Button
                    type="button"
                    onClick={() => void saveCustomerPrompt()}
                    disabled={saving || !promptDirty || promptInvalid}
                  >
                    {saving && <Loader2 className="animate-spin" />}{t("prompt.save")}
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="quota" className="space-y-6">
          <Card>
            <CardHeader className="space-y-3">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="space-y-1">
                  <CardTitle className="text-base">{t("billing.currentPlan")}</CardTitle>
                  <p className="text-sm text-slate-500">
                    {billingAccount?.status === "GRACE"
                      ? t("billing.graceDescription", { date: formatDate(billingAccount.graceEndsAt) })
                      : t("billing.planDescription", { plan: planLabel(billingAccount?.planCode ?? user?.plan, billingAccount?.status) })}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {billingAccount?.cancelAtPeriodEnd && <Badge variant="outline">{t("billing.starterScheduled")}</Badge>}
                  <PlanStatusBadge
                    plan={billingAccount?.planCode ?? user?.plan}
                    status={billingAccount?.status}
                  />
                </div>
              </div>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => setPlanDialog(true)} disabled={billingBusy}>
                <CreditCard />{t("billing.changePlan")}
              </Button>
              <Button type="button" variant="outline" onClick={() => void refreshBilling()} disabled={billingBusy}>
                {billingBusy && <Loader2 className="animate-spin" />}{t("billing.refresh")}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="space-y-2">
              <CardTitle className="text-base">{t("billing.quota")}</CardTitle>
              <p className="text-sm text-slate-500">{t("billing.quotaDescription")}</p>
            </CardHeader>
            <CardContent className="space-y-5">
              <div className="grid gap-4 sm:grid-cols-2">
                {billingAccount && [
                  { label: t("billing.quotaLabels.messages"), usage: billingAccount.messages, suffix: "" },
                  { label: t("billing.quotaLabels.documents"), usage: billingAccount.documents, suffix: "" },
                  { label: t("billing.quotaLabels.teamMembers"), usage: billingAccount.teamMembers, suffix: "" },
                  { label: t("billing.quotaLabels.storage"), usage: billingAccount.storageMb, suffix: " MB" },
                ].map((quota) => (
                  <div key={quota.label} className={`rounded-lg border p-4 ${quota.usage.overLimit ? "border-red-200 bg-red-50" : "border-slate-200 bg-slate-50"}`}>
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{quota.label}</p>
                      <p className="text-sm font-semibold text-slate-900">{usageLabel(quota.usage, quota.suffix)}</p>
                    </div>
                    <Progress value={usagePercent(quota.usage)} />
                    {quota.usage.overLimit && <p className="mt-2 text-xs text-red-700">{t("billing.overLimit")}</p>}
                  </div>
                ))}
              </div>
              <div className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 text-sm sm:grid-cols-2">
                <p><span className="text-slate-500">{t("billing.nextQuotaReset")}</span> <strong>{formatDate(billingAccount?.nextQuotaResetAt ?? null)}</strong></p>
                {billingAccount?.trialEndsAt && <p><span className="text-slate-500">{t("billing.trialEnds")}</span> <strong>{formatDate(billingAccount.trialEndsAt)}</strong></p>}
                {billingAccount?.paidThroughAt && <p><span className="text-slate-500">{t("billing.paidThrough")}</span> <strong>{formatDate(billingAccount.paidThroughAt)}</strong></p>}
                {billingAccount?.graceEndsAt && <p><span className="text-slate-500">{t("billing.graceEnds")}</span> <strong>{formatDate(billingAccount.graceEndsAt)}</strong></p>}
              </div>
              {billingAccount?.pendingPayment && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  {t("billing.pendingPayment", {
                    interval: billingIntervalLabel(billingAccount.pendingPayment.interval),
                    status: t(`billing.statuses.${billingAccount.pendingPayment.status}` as "billing.statuses.PENDING"),
                  })}
                  {billingAccount.pendingPayment.checkoutUrl && <Button className="ml-3" size="sm" onClick={() => window.location.assign(billingAccount.pendingPayment!.checkoutUrl!)}>{t("billing.continueCheckout")}</Button>}
                </div>
              )}
              {billingAccount?.status === "GRACE" && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                  {t("billing.graceWarning", { date: formatDate(billingAccount.graceEndsAt) })}
                </div>
              )}
              {billingAccount?.cancelAtPeriodEnd && (
                <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">
                  {t("billing.starterScheduledDescription")}
                </div>
              )}
              <div className="flex flex-wrap gap-2 text-xs text-slate-600">
                <Badge variant="outline">{t("billing.features.api")} {billingAccount?.features.apiAccess ? t("enabled") : t("disabled")}</Badge>
                <Badge variant="outline">{t("billing.features.webhooks")} {billingAccount?.features.webhooks ? t("enabled") : t("disabled")}</Badge>
                <Badge variant="outline">{t("billing.features.advancedAnalytics")} {billingAccount?.features.advancedAnalytics ? t("enabled") : t("disabled")}</Badge>
                <Badge variant="outline">{t("billing.features.customBranding")} {billingAccount?.features.customBranding ? t("enabled") : t("disabled")}</Badge>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="tokens" className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-slate-500">{t("tokens.description")}</p><div className="flex items-center gap-3"><label className="flex items-center gap-2 text-sm text-slate-600"><input type="checkbox" checked={showDeletedTokens} onChange={(event) => void changeDeletedTokenVisibility(event.target.checked)} />{t("tokens.showDeleted")}</label><Button onClick={() => setTokenDialog(true)}><Plus />{t("tokens.create")}</Button></div></div>
          {!billingAccount?.features.apiAccess && <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">{t("tokens.proRequired")}</p>}
          {newSecret && <Card><CardHeader><CardTitle className="text-base">{t("tokens.newToken")}</CardTitle></CardHeader><CardContent className="space-y-3"><p className="text-sm text-amber-700">{t("tokens.shownOnce")}</p><div className="flex gap-2"><code className="min-w-0 flex-1 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{newSecret}</code><Button variant="outline" size="icon" aria-label={t("widget.copy")} onClick={() => void copyText(newSecret)}>{copied ? <Check /> : <Copy />}</Button></div>{embedCode && <div className="space-y-2"><Label>{t("tokens.environmentVariable")}</Label><pre className="overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">CACANODE_WIDGET_TOKEN=ccn_it_...</pre><Label>{t("tokens.embedTemplate")}</Label><pre className="overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{embedCode}</pre><Button variant="outline" onClick={() => void copyText(embedCode)}><Copy />{t("tokens.copyTemplate")}</Button><p className="text-xs text-slate-500">{t("tokens.browserNotice")}</p></div>}</CardContent></Card>}
          <div className="divide-y rounded-md border bg-white">
            {tokens.map((token) => <div key={token.id} className="flex flex-wrap items-center gap-3 p-4"><KeyRound className="size-4 text-slate-400" /><div className="min-w-44 flex-1"><p className="font-medium text-slate-800">{token.name}</p><p className="font-mono text-xs text-slate-500">{token.tokenPrefix}...</p></div><div className="flex gap-1">{token.scopes.map((scope) => <Badge key={scope} variant="outline">{scope}</Badge>)}{widgetEmbed?.tokenId === token.id && <Badge variant="outline">{t("tokens.managed")}</Badge>}</div><div className="text-right text-xs text-slate-500"><p>{t("tokens.expires", { date: formatDate(token.expiresAt) })}</p><p>{t("tokens.lastUsed", { date: formatDate(token.lastUsedAt) })}</p></div>{token.revokedAt ? <Badge variant="outline">{t("tokens.deleted")}</Badge> : <>{widgetEmbed?.tokenId !== token.id && <Button variant="ghost" size="icon" title={t("tokens.rotate")} onClick={() => void rotateToken(token.id)}><RefreshCw /></Button>}<Button variant="ghost" size="icon" title={t("tokens.delete")} onClick={() => void revokeToken(token.id)}><Trash2 /></Button></>}</div>)}
            {tokens.length === 0 && <p className="p-8 text-center text-sm text-slate-500">{t("tokens.empty")}</p>}
          </div>
        </TabsContent>

        <TabsContent value="webhooks" className="space-y-4">
          <div className="flex items-center justify-between"><p className="text-sm text-slate-500">{t("webhooks.description")}</p><Button disabled={!billingAccount?.features.webhooks} onClick={() => setWebhookDialog(true)}><Plus />{t("webhooks.add")}</Button></div>
          {!billingAccount?.features.webhooks && <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">{t("webhooks.proRequired")}</p>}
          {webhookSecret && <Card><CardContent className="space-y-2 pt-6"><p className="text-sm text-amber-700">{t("webhooks.signingSecretOnce")}</p><div className="flex gap-2"><code className="min-w-0 flex-1 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{webhookSecret}</code><Button variant="outline" size="icon" aria-label={t("widget.copy")} onClick={() => void copyText(webhookSecret)}><Copy /></Button></div></CardContent></Card>}
          <div className="divide-y rounded-md border bg-white">{webhooks.map((endpoint) => <div key={endpoint.id} className="flex flex-wrap items-center gap-3 p-4"><div className="min-w-56 flex-1"><p className="font-medium">{endpoint.name}</p><p className="truncate text-xs text-slate-500">{endpoint.url}</p></div><div className="flex flex-wrap gap-1">{endpoint.events.map((event) => <Badge key={event} variant="outline">{event}</Badge>)}</div><Badge variant="outline">{endpoint.lastDeliveryStatus ?? t("webhooks.notDelivered")}</Badge><Button disabled={!billingAccount?.features.webhooks} variant="ghost" size="icon" title={t("webhooks.sendTest")} onClick={() => void sendWebhookTest(endpoint.id)}><Play /></Button><Button disabled={!billingAccount?.features.webhooks} variant="ghost" size="icon" title={t("webhooks.rotateSecret")} onClick={() => void rotateSigningSecret(endpoint.id)}><RefreshCw /></Button><Button variant="ghost" size="icon" title={t("webhooks.delete")} onClick={() => void removeWebhookEndpoint(endpoint.id)}><Trash2 /></Button></div>)}{webhooks.length === 0 && <p className="p-8 text-center text-sm text-slate-500">{t("webhooks.empty")}</p>}</div>
        </TabsContent>
      </Tabs>

      <Dialog open={planDialog} onOpenChange={setPlanDialog}>
        <DialogContent className="inset-0 flex h-dvh w-screen max-w-none translate-x-0 translate-y-0 flex-col gap-0 rounded-none bg-[radial-gradient(circle_at_top,_rgba(99,102,241,0.28),_transparent_40%),linear-gradient(to_bottom,_rgba(15,23,42,0.52),_rgba(2,6,23,0.68))] p-0 text-white backdrop-blur-2xl sm:max-w-none [&_[data-slot=dialog-close]]:top-4 [&_[data-slot=dialog-close]]:right-4 [&_[data-slot=dialog-close]]:size-9 [&_[data-slot=dialog-close]]:rounded-full [&_[data-slot=dialog-close]]:border [&_[data-slot=dialog-close]]:border-white/15 [&_[data-slot=dialog-close]]:bg-white/10 [&_[data-slot=dialog-close]]:text-slate-200 [&_[data-slot=dialog-close]]:shadow-lg [&_[data-slot=dialog-close]]:backdrop-blur-md [&_[data-slot=dialog-close]]:hover:bg-white/20 [&_[data-slot=dialog-close]]:hover:text-white [&_[data-slot=dialog-close]_svg]:size-5">
          <DialogHeader className="shrink-0 items-center bg-transparent px-14 py-7 text-center sm:px-20 sm:py-9">
            <DialogTitle className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">{t("billing.choosePlan")}</DialogTitle>
            <DialogDescription className="max-w-xl text-center text-sm leading-6 text-slate-400 sm:text-base">
              {t("billing.checkoutDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 overflow-y-auto bg-transparent px-4 py-12 sm:px-8 sm:py-16">
            <div className="mx-auto max-w-6xl">
              <PlanCardGrid plans={billingPlans} currentPlan={backendPlan} interval={billingInterval}
                onIntervalChange={setBillingInterval} onSelectPlan={(plan, interval) => void selectPlan(plan, interval)} />
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={tokenDialog} onOpenChange={setTokenDialog}><DialogContent><DialogHeader><DialogTitle>{t("tokens.dialogTitle")}</DialogTitle><DialogDescription>{t("tokens.dialogDescription")}</DialogDescription></DialogHeader><div className="space-y-4"><div className="space-y-1.5"><Label>{t("tokens.name")}</Label><Input value={tokenName} onChange={(event) => setTokenName(event.target.value)} placeholder={t("tokens.namePlaceholder")} /></div><div className="space-y-2"><Label>{t("tokens.scopes")}</Label>{(["widget:chat", "api:chat"] as IntegrationScope[]).map((scope) => <label key={scope} className="flex items-center gap-2 text-sm"><input type="checkbox" disabled={scope === "api:chat" && !billingAccount?.features.apiAccess} checked={tokenScopes.includes(scope)} onChange={(event) => setTokenScopes((current) => event.target.checked ? [...current, scope] : current.filter((item) => item !== scope))} />{scope}</label>)}</div><div className="space-y-1.5"><Label>{t("tokens.expiry")}</Label><Input type="date" value={tokenExpiry} onChange={(event) => setTokenExpiry(event.target.value)} /></div></div><DialogFooter><Button variant="outline" onClick={() => setTokenDialog(false)}>{t("actions.cancel")}</Button><Button onClick={() => void createToken()} disabled={saving || !tokenName.trim() || tokenScopes.length === 0}>{t("actions.create")}</Button></DialogFooter></DialogContent></Dialog>

      <Dialog open={webhookDialog} onOpenChange={setWebhookDialog}><DialogContent><DialogHeader><DialogTitle>{t("webhooks.dialogTitle")}</DialogTitle><DialogDescription>{t("webhooks.dialogDescription")}</DialogDescription></DialogHeader><div className="space-y-4"><div className="space-y-1.5"><Label>{t("webhooks.name")}</Label><Input value={webhookName} onChange={(event) => setWebhookName(event.target.value)} /></div><div className="space-y-1.5"><Label>{t("webhooks.url")}</Label><Input value={webhookUrl} onChange={(event) => setWebhookUrl(event.target.value)} placeholder="https://example.com/webhooks/cacanode" /></div><div className="space-y-2"><Label>{t("webhooks.events")}</Label>{WEBHOOK_EVENTS.map((event) => <label key={event} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={webhookEvents.includes(event)} onChange={(inputEvent) => setWebhookEvents((current) => inputEvent.target.checked ? [...current, event] : current.filter((item) => item !== event))} />{event}</label>)}</div></div><DialogFooter><Button variant="outline" onClick={() => setWebhookDialog(false)}>{t("actions.cancel")}</Button><Button onClick={() => void createWebhookEndpoint()} disabled={saving}>{t("actions.addEndpoint")}</Button></DialogFooter></DialogContent></Dialog>

      <Dialog open={restorePromptDialog} onOpenChange={setRestorePromptDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("prompt.restoreTitle")}</DialogTitle>
            <DialogDescription>
              {t("prompt.restoreDescription")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setRestorePromptDialog(false)} disabled={saving}>
              {t("actions.cancel")}
            </Button>
            <Button type="button" onClick={() => void restoreDefaultPrompt()} disabled={saving}>
              {saving && <Loader2 className="animate-spin" />}{t("prompt.restore")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default function SettingsPage() {
  return (
    <Suspense fallback={<div className="max-w-5xl space-y-4"><Skeleton className="h-8 w-40" /><Skeleton className="h-96 w-full" /></div>}>
      <SettingsPageContent />
    </Suspense>
  );
}

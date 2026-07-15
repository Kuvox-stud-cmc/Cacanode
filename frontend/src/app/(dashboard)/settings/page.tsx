"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Check, Copy, CreditCard, KeyRound, Loader2, Play, Plus, RefreshCw, RotateCcw, Trash2 } from "lucide-react";
import toast from "react-hot-toast";
import PlanCardGrid, {
  normalizePlanId,
  type PlanId,
} from "@/components/landing/PlanCardGrid";
import { useAuthStore } from "@/components/providers/StoreProvider";
import { useApiClient } from "@/hooks/useApiClient";
import { publicConfig } from "@/lib/public-config";
import {
  createIntegrationToken,
  createWebhook,
  deleteWebhook,
  getWidgetSettings,
  listIntegrationTokens,
  listWebhooks,
  revokeIntegrationToken,
  rotateIntegrationToken,
  rotateWebhookSecret,
  testWebhook,
  updateWidgetSettings,
  type IntegrationScope,
  type IntegrationToken,
  type WebhookEndpoint,
  type WidgetSettings,
} from "@/lib/integrations-api";
import {
  getCustomerAnswerPrompt,
  updateCustomerAnswerPrompt,
  type CustomerAnswerPromptSettings,
} from "@/lib/tenant-settings-api";
import { Badge } from "@/components/ui/badge";
import { getPlanPresentation, PlanStatusBadge } from "@/components/billing/PlanStatusBadge";
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

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString() : "Never";
}

function usageLabel(usage: BillingUsage, suffix = ""): string {
  return `${usage.used.toLocaleString()}${suffix} / ${usage.limit === null ? "Unlimited" : `${usage.limit.toLocaleString()}${suffix}`}`;
}

function usagePercent(usage: BillingUsage): number {
  if (usage.limit === null || usage.limit === 0) return 0;
  return Math.min(100, Math.round((usage.used / usage.limit) * 100));
}

function SettingsPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const setPlan = useAuthStore((state) => state.setPlan);
  const { request } = useApiClient();
  const [loading, setLoading] = useState(true);
  const [widget, setWidget] = useState<WidgetSettings | null>(null);
  const [origins, setOrigins] = useState("");
  const [tokens, setTokens] = useState<IntegrationToken[]>([]);
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

  useEffect(() => {
    if (!user) return;
    if (user.role !== "TENANT_ADMIN") {
      router.replace("/dashboard");
      return;
    }
    let cancelled = false;
    Promise.all([
      getWidgetSettings(request),
      listIntegrationTokens(request),
      listWebhooks(request),
      getCustomerAnswerPrompt(request),
      getBillingAccount(request),
      getPublicBillingPlans(),
    ])
      .then(([widgetResult, tokenResult, webhookResult, promptResult, accountResult, plansResult]) => {
        if (cancelled) return;
        setWidget(widgetResult);
        setOrigins(widgetResult.allowedOrigins.join("\n"));
        setTokens(tokenResult);
        setWebhooks(webhookResult);
        setPromptSettings(promptResult);
        setPromptDraft(promptResult.prompt);
        setBillingAccount(accountResult);
        setBillingPlans(plansResult);
        setBillingInterval(accountResult.interval ?? "MONTHLY");
        setPlan(accountResult.planCode);
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : "Unable to load settings"))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [request, router, setPlan, user]);

  useEffect(() => {
    const paymentId = searchParams.get("paymentId");
    if (!paymentId || !user) return;
    let cancelled = false;
    void (async () => {
      for (let attempt = 0; attempt < 15 && !cancelled; attempt += 1) {
        const payment = await getBillingPayment(request, paymentId);
        if (payment.status === "PAID") {
          const account = await getBillingAccount(request);
          if (cancelled) return;
          setBillingAccount(account);
          setPlan(account.planCode);
          toast.success("Pro subscription activated");
          router.replace("/settings?tab=quota");
          return;
        }
        if (["CANCELLED", "EXPIRED", "FAILED", "REVIEW"].includes(payment.status)) {
          toast.error(payment.failureReason ?? `Payment is ${payment.status.toLowerCase()}`);
          router.replace("/settings?tab=quota");
          return;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 2000));
      }
      if (!cancelled) toast("Payment is still pending. Use Refresh payment status to check again.");
    })().catch((error) => toast.error(error instanceof Error ? error.message : "Unable to verify payment"));
    return () => { cancelled = true; };
  }, [request, router, searchParams, setPlan, user]);

  const promptDirty = promptSettings !== null && promptDraft !== promptSettings.prompt;
  const promptLength = Array.from(promptDraft).length;
  const promptInvalid = promptDraft.trim().length === 0 || promptLength > 4000;
  const backendPlan = normalizePlanId(billingAccount?.planCode ?? user?.plan);
  const currentPlanPresentation = getPlanPresentation(
    billingAccount?.planCode ?? user?.plan,
    billingAccount?.status,
  );

  const embedCode = useMemo(() => {
    if (!newSecret || !newSecretScopes.includes("widget:chat")) return null;
    const widgetUrl = publicConfig.widgetUrl ?? "/widget/v1/cacanode-chat.js";
    return `<script async src="${widgetUrl}" data-token="${newSecret}"></script>`;
  }, [newSecret, newSecretScopes]);

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
      toast.success("Widget settings saved");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to save widget settings");
    } finally {
      setSaving(false);
    }
  }

  async function saveCustomerPrompt() {
    if (!promptDirty || promptInvalid) return;
    setSaving(true);
    try {
      const updated = await updateCustomerAnswerPrompt(request, promptDraft);
      setPromptSettings(updated);
      setPromptDraft(updated.prompt);
      toast.success("AI prompt saved");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to save AI prompt");
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
      toast.success("Default AI prompt restored");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to restore the default prompt");
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
      toast.error(error instanceof Error ? error.message : "Unable to create token");
    } finally {
      setSaving(false);
    }
  }

  async function revokeToken(id: string) {
    try {
      await revokeIntegrationToken(request, id);
      setTokens((current) => current.map((token) => (
        token.id === id ? { ...token, revokedAt: new Date().toISOString() } : token
      )));
      toast.success("Token revoked");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to revoke token");
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
      toast.success("Token rotated");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to rotate token");
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
      toast.error(error instanceof Error ? error.message : "Unable to create webhook");
    } finally {
      setSaving(false);
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
        ? "End the Pro trial immediately and switch to Starter?"
        : `Schedule Starter after prepaid Pro access and grace end on ${formatDate(billingAccount?.graceEndsAt ?? null)}?`)) return;
      const result = await downgradeBilling(request);
      setBillingAccount(result.account);
      setPlan(result.account.planCode);
      setPlanDialog(false);
      toast.success(result.scheduled
        ? `Starter is scheduled for ${formatDate(result.effectiveAt)}`
        : "Switched to Starter");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update billing");
    } finally {
      setBillingBusy(false);
    }
  }

  async function refreshBilling() {
    setBillingBusy(true);
    try {
      const account = await getBillingAccount(request);
      setBillingAccount(account);
      setPlan(account.planCode);
      toast.success("Billing status refreshed");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to refresh billing");
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
      <h2 className="text-xl font-semibold text-slate-800">Settings</h2>
      <Tabs defaultValue={searchParams.get("tab") === "quota" ? "quota" : "widget"}>
        <TabsList className="mb-6 h-auto max-w-full flex-wrap justify-start">
          <TabsTrigger value="widget">Widget</TabsTrigger>
          <TabsTrigger value="ai-prompt">AI Prompt</TabsTrigger>
          <TabsTrigger value="quota">Quota Management</TabsTrigger>
          <TabsTrigger value="tokens">Integration Tokens</TabsTrigger>
          <TabsTrigger value="webhooks">Webhooks</TabsTrigger>
        </TabsList>

        <TabsContent value="widget">
          {widget && <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
            <Card>
              <CardHeader><CardTitle className="text-base">Widget Configuration</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-1.5"><Label>Display name</Label><Input value={widget.displayName} onChange={(event) => setWidget({ ...widget, displayName: event.target.value })} /></div>
                <div className="space-y-1.5"><Label>Welcome message</Label><textarea className="min-h-24 w-full rounded-md border border-slate-200 p-3 text-sm" value={widget.welcomeMessage} onChange={(event) => setWidget({ ...widget, welcomeMessage: event.target.value })} /></div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1.5"><Label>Primary color</Label><div className="flex gap-2"><input type="color" className="h-9 w-12" value={widget.primaryColor} onChange={(event) => setWidget({ ...widget, primaryColor: event.target.value })} /><Input value={widget.primaryColor} onChange={(event) => setWidget({ ...widget, primaryColor: event.target.value })} /></div></div>
                  <div className="space-y-1.5"><Label>Position</Label><select className="h-9 w-full rounded-md border border-slate-200 px-3 text-sm" value={widget.position} onChange={(event) => setWidget({ ...widget, position: event.target.value as WidgetSettings["position"] })}><option value="BOTTOM_RIGHT">Bottom right</option><option value="BOTTOM_LEFT">Bottom left</option></select></div>
                </div>
                <div className="space-y-1.5"><Label>Allowed origins</Label><textarea className="min-h-24 w-full rounded-md border border-slate-200 p-3 font-mono text-sm" value={origins} onChange={(event) => setOrigins(event.target.value)} placeholder="https://example.com" /><p className="text-xs text-amber-700">Leave empty to allow the widget on every website. Browser tokens can be inspected and consume your quota.</p></div>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={widget.active} onChange={(event) => setWidget({ ...widget, active: event.target.checked })} />Widget active</label>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={widget.hideCacanodeBranding}
                  disabled={!billingAccount?.features.customBranding}
                  onChange={(event) => setWidget({ ...widget, hideCacanodeBranding: event.target.checked })} />Hide CacaNode branding</label>
                {!billingAccount?.features.customBranding && <p className="text-xs text-amber-700">Your preference is preserved, but Starter always displays CacaNode branding.</p>}
                <Button onClick={() => void saveWidget()} disabled={saving}>{saving && <Loader2 className="animate-spin" />}Save widget</Button>
              </CardContent>
            </Card>
            <div className="relative min-h-80 overflow-hidden rounded-md border bg-slate-100">
              <div className="p-5 text-sm text-slate-500">Customer website preview</div>
              <button type="button" className={`absolute bottom-5 ${widget.position === "BOTTOM_LEFT" ? "left-5" : "right-5"} grid size-14 place-items-center rounded-full text-white shadow-lg`} style={{ backgroundColor: widget.primaryColor }}>?</button>
            </div>
          </div>}
        </TabsContent>

        <TabsContent value="ai-prompt">
          <Card>
            <CardHeader className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <CardTitle className="text-base">Customer answer instructions</CardTitle>
                {promptSettings && (
                  <Badge variant="outline">
                    {promptSettings.usingDefault ? "Using platform default" : "Custom prompt saved"}
                  </Badge>
                )}
              </div>
              <p className="text-sm text-slate-500">
                Control tone, terminology, formatting, and escalation behavior for Widget and Custom API answers.
                Platform grounding, citations, tenant isolation, safety, and ticket response rules always remain in force.
              </p>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="customer-answer-prompt">Customer answer instructions</Label>
                <textarea
                  id="customer-answer-prompt"
                  className="min-h-64 w-full rounded-md border border-slate-200 p-3 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                  value={promptDraft}
                  onChange={(event) => setPromptDraft(event.target.value)}
                  aria-describedby="customer-answer-prompt-guidance"
                />
                <div className="flex items-start justify-between gap-4 text-xs">
                  <p id="customer-answer-prompt-guidance" className="text-amber-700">
                    Do not enter passwords, API tokens, private keys, or other secrets.
                  </p>
                  <span className={promptLength > 4000 ? "font-medium text-red-600" : "text-slate-500"}>
                    {promptLength}/4000
                  </span>
                </div>
                {promptDraft.trim().length === 0 && (
                  <p className="text-xs text-red-600">Instructions cannot be blank. Use Restore default instead.</p>
                )}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
                <p className="text-xs text-slate-500">
                  Last updated: {formatDate(promptSettings?.updatedAt ?? null)}
                </p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setRestorePromptDialog(true)}
                    disabled={saving || (promptSettings?.usingDefault !== false && !promptDirty)}
                  >
                    <RotateCcw />Restore default
                  </Button>
                  <Button
                    type="button"
                    onClick={() => void saveCustomerPrompt()}
                    disabled={saving || !promptDirty || promptInvalid}
                  >
                    {saving && <Loader2 className="animate-spin" />}Save prompt
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
                  <CardTitle className="text-base">Current plan</CardTitle>
                  <p className="text-sm text-slate-500">
                    {billingAccount?.status === "GRACE"
                      ? `Pro access is in grace until ${formatDate(billingAccount.graceEndsAt)}.`
                      : `Your account is using the ${currentPlanPresentation.label} plan.`}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {billingAccount?.cancelAtPeriodEnd && <Badge variant="outline">Starter scheduled</Badge>}
                  <PlanStatusBadge
                    plan={billingAccount?.planCode ?? user?.plan}
                    status={billingAccount?.status}
                  />
                </div>
              </div>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => setPlanDialog(true)} disabled={billingBusy}>
                <CreditCard />Change plan
              </Button>
              <Button type="button" variant="outline" onClick={() => void refreshBilling()} disabled={billingBusy}>
                {billingBusy && <Loader2 className="animate-spin" />}Refresh payment status
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="space-y-2">
              <CardTitle className="text-base">Quota management</CardTitle>
              <p className="text-sm text-slate-500">
                Message usage resets on your billing anniversary. Documents, storage, and members are live totals.
              </p>
            </CardHeader>
            <CardContent className="space-y-5">
              <div className="grid gap-4 sm:grid-cols-2">
                {billingAccount && [
                  { label: "Messages", usage: billingAccount.messages, suffix: "" },
                  { label: "Documents", usage: billingAccount.documents, suffix: "" },
                  { label: "Team members", usage: billingAccount.teamMembers, suffix: "" },
                  { label: "Storage", usage: billingAccount.storageMb, suffix: " MB" },
                ].map((quota) => (
                  <div key={quota.label} className={`rounded-lg border p-4 ${quota.usage.overLimit ? "border-red-200 bg-red-50" : "border-slate-200 bg-slate-50"}`}>
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{quota.label}</p>
                      <p className="text-sm font-semibold text-slate-900">{usageLabel(quota.usage, quota.suffix)}</p>
                    </div>
                    <Progress value={usagePercent(quota.usage)} />
                    {quota.usage.overLimit && <p className="mt-2 text-xs text-red-700">Existing data is preserved, but new growth is blocked until usage is reduced or Pro is renewed.</p>}
                  </div>
                ))}
              </div>
              <div className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 text-sm sm:grid-cols-2">
                <p><span className="text-slate-500">Next quota reset:</span> <strong>{formatDate(billingAccount?.nextQuotaResetAt ?? null)}</strong></p>
                {billingAccount?.trialEndsAt && <p><span className="text-slate-500">Trial ends:</span> <strong>{formatDate(billingAccount.trialEndsAt)}</strong></p>}
                {billingAccount?.paidThroughAt && <p><span className="text-slate-500">Paid through:</span> <strong>{formatDate(billingAccount.paidThroughAt)}</strong></p>}
                {billingAccount?.graceEndsAt && <p><span className="text-slate-500">Grace ends:</span> <strong>{formatDate(billingAccount.graceEndsAt)}</strong></p>}
              </div>
              {billingAccount?.pendingPayment && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  A {billingAccount.pendingPayment.interval.toLowerCase()} Pro payment is {billingAccount.pendingPayment.status.toLowerCase()}.
                  {billingAccount.pendingPayment.checkoutUrl && <Button className="ml-3" size="sm" onClick={() => window.location.assign(billingAccount.pendingPayment!.checkoutUrl!)}>Continue checkout</Button>}
                </div>
              )}
              {billingAccount?.status === "GRACE" && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                  Grace keeps the final Pro quota window; it does not grant another monthly allowance. Renew before {formatDate(billingAccount.graceEndsAt)}.
                </div>
              )}
              {billingAccount?.cancelAtPeriodEnd && (
                <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">
                  Starter is scheduled after prepaid access and the grace period. Your existing resources will remain stored.
                </div>
              )}
              <div className="flex flex-wrap gap-2 text-xs text-slate-600">
                <Badge variant="outline">API {billingAccount?.features.apiAccess ? "enabled" : "disabled"}</Badge>
                <Badge variant="outline">Webhooks {billingAccount?.features.webhooks ? "enabled" : "disabled"}</Badge>
                <Badge variant="outline">Advanced analytics {billingAccount?.features.advancedAnalytics ? "enabled" : "disabled"}</Badge>
                <Badge variant="outline">Custom branding {billingAccount?.features.customBranding ? "enabled" : "disabled"}</Badge>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="tokens" className="space-y-4">
          <div className="flex items-center justify-between"><p className="text-sm text-slate-500">Create separate scoped tokens for hosted widgets and server-side Chat API integrations.</p><Button onClick={() => setTokenDialog(true)}><Plus />Create token</Button></div>
          {!billingAccount?.features.apiAccess && <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">Widget tokens remain available. Creating or using api:chat tokens requires Pro.</p>}
          {newSecret && <Card><CardHeader><CardTitle className="text-base">New token</CardTitle></CardHeader><CardContent className="space-y-3"><p className="text-sm text-amber-700">This value is shown once. Store it before closing this panel.</p><div className="flex gap-2"><code className="min-w-0 flex-1 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{newSecret}</code><Button variant="outline" size="icon" onClick={() => void copyText(newSecret)}>{copied ? <Check /> : <Copy />}</Button></div>{embedCode && <div className="space-y-2"><Label>Widget embed code</Label><pre className="overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{embedCode}</pre><Button variant="outline" onClick={() => void copyText(embedCode)}><Copy />Copy embed code</Button></div>}</CardContent></Card>}
          <div className="divide-y rounded-md border bg-white">
            {tokens.map((token) => <div key={token.id} className="flex flex-wrap items-center gap-3 p-4"><KeyRound className="size-4 text-slate-400" /><div className="min-w-44 flex-1"><p className="font-medium text-slate-800">{token.name}</p><p className="font-mono text-xs text-slate-500">{token.tokenPrefix}...</p></div><div className="flex gap-1">{token.scopes.map((scope) => <Badge key={scope} variant="outline">{scope}</Badge>)}</div><div className="text-right text-xs text-slate-500"><p>Expires: {formatDate(token.expiresAt)}</p><p>Last used: {formatDate(token.lastUsedAt)}</p></div>{token.revokedAt ? <Badge variant="outline">Revoked</Badge> : <><Button variant="ghost" size="icon" title="Rotate token" onClick={() => void rotateToken(token.id)}><RefreshCw /></Button><Button variant="ghost" size="icon" title="Revoke token" onClick={() => void revokeToken(token.id)}><Trash2 /></Button></>}</div>)}
            {tokens.length === 0 && <p className="p-8 text-center text-sm text-slate-500">No integration tokens</p>}
          </div>
        </TabsContent>

        <TabsContent value="webhooks" className="space-y-4">
          <div className="flex items-center justify-between"><p className="text-sm text-slate-500">Receive signed conversation and ticket lifecycle events.</p><Button disabled={!billingAccount?.features.webhooks} onClick={() => setWebhookDialog(true)}><Plus />Add endpoint</Button></div>
          {!billingAccount?.features.webhooks && <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">Saved endpoints are preserved, but creation, testing, and delivery are disabled until Pro is renewed.</p>}
          {webhookSecret && <Card><CardContent className="space-y-2 pt-6"><p className="text-sm text-amber-700">Signing secret shown once</p><div className="flex gap-2"><code className="min-w-0 flex-1 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-white">{webhookSecret}</code><Button variant="outline" size="icon" onClick={() => void copyText(webhookSecret)}><Copy /></Button></div></CardContent></Card>}
          <div className="divide-y rounded-md border bg-white">{webhooks.map((endpoint) => <div key={endpoint.id} className="flex flex-wrap items-center gap-3 p-4"><div className="min-w-56 flex-1"><p className="font-medium">{endpoint.name}</p><p className="truncate text-xs text-slate-500">{endpoint.url}</p></div><div className="flex flex-wrap gap-1">{endpoint.events.map((event) => <Badge key={event} variant="outline">{event}</Badge>)}</div><Badge variant="outline">{endpoint.lastDeliveryStatus ?? "Not delivered"}</Badge><Button disabled={!billingAccount?.features.webhooks} variant="ghost" size="icon" title="Send test" onClick={async () => { await testWebhook(request, endpoint.id); toast.success("Test queued"); }}><Play /></Button><Button disabled={!billingAccount?.features.webhooks} variant="ghost" size="icon" title="Rotate signing secret" onClick={async () => { const rotated = await rotateWebhookSecret(request, endpoint.id); setWebhookSecret(rotated.signingSecret); toast.success("Signing secret rotated"); }}><RefreshCw /></Button><Button variant="ghost" size="icon" title="Delete endpoint" onClick={async () => { await deleteWebhook(request, endpoint.id); setWebhooks((current) => current.filter((item) => item.id !== endpoint.id)); }}><Trash2 /></Button></div>)}{webhooks.length === 0 && <p className="p-8 text-center text-sm text-slate-500">No webhook endpoints</p>}</div>
        </TabsContent>
      </Tabs>

      <Dialog open={planDialog} onOpenChange={setPlanDialog}>
        <DialogContent className="inset-0 flex h-dvh w-screen max-w-none translate-x-0 translate-y-0 flex-col gap-0 rounded-none bg-[radial-gradient(circle_at_top,_rgba(99,102,241,0.28),_transparent_40%),linear-gradient(to_bottom,_rgba(15,23,42,0.52),_rgba(2,6,23,0.68))] p-0 text-white backdrop-blur-2xl sm:max-w-none [&_[data-slot=dialog-close]]:top-4 [&_[data-slot=dialog-close]]:right-4 [&_[data-slot=dialog-close]]:size-9 [&_[data-slot=dialog-close]]:rounded-full [&_[data-slot=dialog-close]]:border [&_[data-slot=dialog-close]]:border-white/15 [&_[data-slot=dialog-close]]:bg-white/10 [&_[data-slot=dialog-close]]:text-slate-200 [&_[data-slot=dialog-close]]:shadow-lg [&_[data-slot=dialog-close]]:backdrop-blur-md [&_[data-slot=dialog-close]]:hover:bg-white/20 [&_[data-slot=dialog-close]]:hover:text-white [&_[data-slot=dialog-close]_svg]:size-5">
          <DialogHeader className="shrink-0 items-center bg-transparent px-14 py-7 text-center sm:px-20 sm:py-9">
            <DialogTitle className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">Choose your plan.</DialogTitle>
            <DialogDescription className="max-w-xl text-center text-sm leading-6 text-slate-400 sm:text-base">
              Pro uses secure prepaid PayOS checkout. Renewals are manual and extend from your current paid-through date.
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

      <Dialog open={tokenDialog} onOpenChange={setTokenDialog}><DialogContent><DialogHeader><DialogTitle>Create integration token</DialogTitle><DialogDescription>The secret is displayed once after creation.</DialogDescription></DialogHeader><div className="space-y-4"><div className="space-y-1.5"><Label>Name</Label><Input value={tokenName} onChange={(event) => setTokenName(event.target.value)} placeholder="Production website" /></div><div className="space-y-2"><Label>Scopes</Label>{(["widget:chat", "api:chat"] as IntegrationScope[]).map((scope) => <label key={scope} className="flex items-center gap-2 text-sm"><input type="checkbox" disabled={scope === "api:chat" && !billingAccount?.features.apiAccess} checked={tokenScopes.includes(scope)} onChange={(event) => setTokenScopes((current) => event.target.checked ? [...current, scope] : current.filter((item) => item !== scope))} />{scope}</label>)}</div><div className="space-y-1.5"><Label>Expiry date (optional)</Label><Input type="date" value={tokenExpiry} onChange={(event) => setTokenExpiry(event.target.value)} /></div></div><DialogFooter><Button variant="outline" onClick={() => setTokenDialog(false)}>Cancel</Button><Button onClick={() => void createToken()} disabled={saving || !tokenName.trim() || tokenScopes.length === 0}>Create</Button></DialogFooter></DialogContent></Dialog>

      <Dialog open={webhookDialog} onOpenChange={setWebhookDialog}><DialogContent><DialogHeader><DialogTitle>Add webhook endpoint</DialogTitle><DialogDescription>Payloads are signed with a secret displayed once.</DialogDescription></DialogHeader><div className="space-y-4"><div className="space-y-1.5"><Label>Name</Label><Input value={webhookName} onChange={(event) => setWebhookName(event.target.value)} /></div><div className="space-y-1.5"><Label>URL</Label><Input value={webhookUrl} onChange={(event) => setWebhookUrl(event.target.value)} placeholder="https://example.com/webhooks/cacanode" /></div><div className="space-y-2"><Label>Events</Label>{WEBHOOK_EVENTS.map((event) => <label key={event} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={webhookEvents.includes(event)} onChange={(inputEvent) => setWebhookEvents((current) => inputEvent.target.checked ? [...current, event] : current.filter((item) => item !== event))} />{event}</label>)}</div></div><DialogFooter><Button variant="outline" onClick={() => setWebhookDialog(false)}>Cancel</Button><Button onClick={() => void createWebhookEndpoint()} disabled={saving}>Add endpoint</Button></DialogFooter></DialogContent></Dialog>

      <Dialog open={restorePromptDialog} onOpenChange={setRestorePromptDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Restore the platform default?</DialogTitle>
            <DialogDescription>
              Your custom customer-answer instructions will be replaced immediately for all Widget and Custom API conversations.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setRestorePromptDialog(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="button" onClick={() => void restoreDefaultPrompt()} disabled={saving}>
              {saving && <Loader2 className="animate-spin" />}Restore default
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

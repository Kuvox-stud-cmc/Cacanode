import { getApiBase } from "@/lib/auth-api";
import { parseApiError, readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type IntegrationScope = "widget:chat" | "api:chat";

export type IntegrationToken = {
  id: string;
  name: string;
  tokenPrefix: string;
  scopes: IntegrationScope[];
  expiresAt: string | null;
  lastUsedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
};

export type WidgetSettings = {
  chatbotId: string;
  displayName: string;
  welcomeMessage: string;
  primaryColor: string;
  position: "BOTTOM_RIGHT" | "BOTTOM_LEFT";
  active: boolean;
  allowedOrigins: string[];
};

export type WebhookEndpoint = {
  id: string;
  name: string;
  url: string;
  events: string[];
  active: boolean;
  lastDeliveryAt: string | null;
  lastDeliveryStatus: string | null;
  createdAt: string;
};

export async function listIntegrationTokens(request: ApiRequest): Promise<IntegrationToken[]> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/tokens`));
}

export async function createIntegrationToken(
  request: ApiRequest,
  payload: { name: string; scopes: IntegrationScope[]; expiresAt: string | null },
): Promise<{ token: IntegrationToken; secret: string }> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/tokens`, {
    method: "POST",
    body: JSON.stringify(payload),
  }));
}

export async function revokeIntegrationToken(request: ApiRequest, id: string): Promise<void> {
  const response = await request(`${getApiBase()}/tenants/me/integrations/tokens/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) throw await parseApiError(response);
}

export async function rotateIntegrationToken(
  request: ApiRequest,
  id: string,
): Promise<{ token: IntegrationToken; secret: string }> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/tokens/${id}/rotate`, {
    method: "POST",
  }));
}

export async function getWidgetSettings(request: ApiRequest): Promise<WidgetSettings> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/widget`));
}

export async function updateWidgetSettings(
  request: ApiRequest,
  payload: WidgetSettings,
): Promise<WidgetSettings> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/widget`, {
    method: "PUT",
    body: JSON.stringify(payload),
  }));
}

export async function listWebhooks(request: ApiRequest): Promise<WebhookEndpoint[]> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/webhooks`));
}

export async function createWebhook(
  request: ApiRequest,
  payload: { name: string; url: string; events: string[]; active: boolean },
): Promise<{ endpoint: WebhookEndpoint; signingSecret: string }> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/integrations/webhooks`, {
    method: "POST",
    body: JSON.stringify(payload),
  }));
}

export async function testWebhook(request: ApiRequest, id: string): Promise<void> {
  const response = await request(`${getApiBase()}/tenants/me/integrations/webhooks/${id}/test`, {
    method: "POST",
  });
  if (!response.ok) throw await parseApiError(response);
}

export async function rotateWebhookSecret(
  request: ApiRequest,
  id: string,
): Promise<{ endpoint: WebhookEndpoint; signingSecret: string }> {
  return readJsonOrThrow(await request(
    `${getApiBase()}/tenants/me/integrations/webhooks/${id}/rotate-secret`,
    { method: "POST" },
  ));
}

export async function deleteWebhook(request: ApiRequest, id: string): Promise<void> {
  const response = await request(`${getApiBase()}/tenants/me/integrations/webhooks/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) throw await parseApiError(response);
}

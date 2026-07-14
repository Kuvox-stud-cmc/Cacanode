import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type CustomerAnswerPromptSettings = {
  prompt: string;
  usingDefault: boolean;
  updatedAt: string;
};

export async function getCustomerAnswerPrompt(
  request: ApiRequest,
): Promise<CustomerAnswerPromptSettings> {
  return readJsonOrThrow<CustomerAnswerPromptSettings>(
    await request(`${getApiBase()}/tenants/me/customer-answer-prompt`),
  );
}

export async function updateCustomerAnswerPrompt(
  request: ApiRequest,
  prompt: string,
): Promise<CustomerAnswerPromptSettings> {
  return readJsonOrThrow<CustomerAnswerPromptSettings>(
    await request(`${getApiBase()}/tenants/me/customer-answer-prompt`, {
      method: "PUT",
      body: JSON.stringify({ prompt }),
    }),
  );
}

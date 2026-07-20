import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { DocumentUnit } from "@/types";

export type PublicEvidence = {
  document_id: string;
  source_name: string;
  focus: string;
  expires_at: string;
  units: DocumentUnit[];
};

export async function getPublicEvidence(token: string): Promise<PublicEvidence> {
  const response = await fetch(`${getApiBase()}/public/evidence/${encodeURIComponent(token)}`, {
    headers: { "X-Request-ID": crypto.randomUUID() },
    cache: "no-store",
  });
  return readJsonOrThrow<PublicEvidence>(response);
}

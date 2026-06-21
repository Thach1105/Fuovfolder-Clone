import { apiFetch } from "@/lib/api/client";

export interface AwardDefinition {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  awardType: string;
  active: boolean;
}

export interface UpdateAwardDefinitionBody {
  name?: string;
  description?: string;
  iconUrl?: string;
  active?: boolean;
}

export function listAwardDefinitions() {
  return apiFetch<AwardDefinition[]>("/api/v1/admin/awards/definitions");
}

export function updateAwardDefinition(id: string, body: UpdateAwardDefinitionBody) {
  return apiFetch<AwardDefinition>(`/api/v1/admin/awards/definitions/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

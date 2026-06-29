import { API_V1 } from "@/lib/constants/api";
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

export function listAwardDefinitions(): Promise<AwardDefinition[]> {
  return apiFetch<AwardDefinition[]>(`${API_V1}/awards/definitions`);
}

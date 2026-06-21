"use client";

import { Checkbox } from "@/components/ui/checkbox";
import type { PermissionCatalogResponse } from "@/types/api";

export function PermissionMatrix({
  catalog,
  selected,
  onToggle,
  disabled,
}: {
  catalog: PermissionCatalogResponse | null;
  selected: Set<string>;
  onToggle: (slug: string) => void;
  disabled?: boolean;
}) {
  const modules = catalog?.modules ?? {};

  return (
    <div className="space-y-6">
      {Object.entries(modules).map(([module, permissions]) => (
        <section key={module} className="overflow-hidden rounded-xl border border-border">
          <h3 className="border-b border-border bg-muted/50 px-4 py-3 text-sm font-semibold text-foreground">
            {module}
          </h3>
          <div className="divide-y divide-border">
            {permissions.map((permission) => (
              <label
                key={permission.slug}
                className="flex cursor-pointer items-start gap-3 px-4 py-3 hover:bg-accent/40"
              >
                <Checkbox
                  className="mt-0.5"
                  checked={selected.has(permission.slug)}
                  disabled={disabled}
                  onCheckedChange={() => onToggle(permission.slug)}
                />
                <span>
                  <span className="block font-mono text-xs text-primary">{permission.slug}</span>
                  <span className="block text-sm text-muted-foreground">
                    {permission.description}
                  </span>
                </span>
              </label>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

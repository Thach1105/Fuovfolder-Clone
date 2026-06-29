export function getInitial(name: string | null | undefined): string {
  if (!name?.trim()) return "?";
  return name.trim().charAt(0).toUpperCase();
}

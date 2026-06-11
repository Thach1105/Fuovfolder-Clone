import Link from "next/link";

interface PlaceholderPageProps {
  title: string;
  description: string;
}

export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <div className="card mx-auto max-w-lg p-8 text-center">
      <h1 className="text-xl font-bold text-slate-900">{title}</h1>
      <p className="mt-2 text-sm text-slate-500">{description}</p>
      <Link href="/" className="btn-primary mt-6 inline-flex">
        Về trang chủ
      </Link>
    </div>
  );
}

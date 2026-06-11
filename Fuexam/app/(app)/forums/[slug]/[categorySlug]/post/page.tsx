"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { CreateThreadForm } from "@/components/forum/CreateThreadForm";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { ApiError } from "@/lib/api/client";
import { type Category, getCategory, listCategories } from "@/lib/api/forum";

export default function CreateThreadPage() {
  const params = useParams();
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const forumSlug = typeof params.slug === "string" ? params.slug : "";
  const categorySlug = typeof params.categorySlug === "string" ? params.categorySlug : "";

  const [category, setCategory] = useState<Category | null>(null);
  const [parentTitle, setParentTitle] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.replace(`/login?next=/forums/${forumSlug}/${categorySlug}/post`);
      return;
    }
    if (!can(user, "forum.thread:create")) {
      router.replace("/membership");
    }
  }, [authLoading, user, router, forumSlug, categorySlug]);

  useEffect(() => {
    if (!forumSlug || !categorySlug) return;
    Promise.all([getCategory(forumSlug, categorySlug), listCategories(forumSlug)])
      .then(([categoryData, allCategories]) => {
        setCategory(categoryData);
        if (categoryData.parentId) {
          setParentTitle(
            allCategories.find((item) => item.id === categoryData.parentId)?.title ?? null,
          );
        }
      })
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được mục diễn đàn"),
      );
  }, [forumSlug, categorySlug]);

  if (authLoading || !user) {
    return <p className="text-sm text-slate-500">Đang kiểm tra đăng nhập...</p>;
  }

  if (error || !category) {
    return (
      <div className="card p-6 text-sm text-slate-600">
        <p>{error ?? "Không tìm thấy mục diễn đàn."}</p>
        <Link
          href={`/forums/${forumSlug}`}
          className="mt-3 inline-block font-medium text-fuo-600 hover:underline"
        >
          ← Quay lại diễn đàn
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <PromoBanner />
      <nav className="flex flex-wrap items-center gap-1 text-sm text-slate-500">
        <Link href="/" className="hover:text-fuo-600">
          Trang chủ
        </Link>
        <span>/</span>
        <Link href={`/forums/${forumSlug}`} className="hover:text-fuo-600">
          {forumSlug}
        </Link>
        <span>/</span>
        <Link href={`/forums/${forumSlug}/${categorySlug}`} className="hover:text-fuo-600">
          {category.title}
        </Link>
        <span>/</span>
        <span className="font-medium text-slate-700">Đăng bài</span>
      </nav>
      <CreateThreadForm
        categoryId={category.id}
        forumSlug={forumSlug}
        categoryTitle={category.title}
        parentTitle={parentTitle}
      />
    </div>
  );
}

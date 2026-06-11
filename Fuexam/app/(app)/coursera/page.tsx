"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  type CatalogItem,
  createCourseraRequest,
  formatPoints,
  getMyRequestStats,
  listCatalog,
} from "@/lib/api/coursera";
import { getPointsBalance } from "@/lib/api/points";
import { ApiError } from "@/lib/api/client";

function CatalogCourseButton({
  item,
  selected,
  onSelect,
}: {
  item: CatalogItem;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        className={`flex w-full items-start justify-between gap-3 rounded-lg border px-3 py-2 text-left transition ${
          selected
            ? "border-fuo-400 bg-fuo-50"
            : "border-slate-200 hover:border-slate-300"
        }`}
      >
        <div className="min-w-0">
          <span className="inline-flex items-center gap-1.5">
            <span className="rounded bg-fuo-100 px-2 py-0.5 text-xs font-semibold text-fuo-800">
              {item.code}
            </span>
            {item.featured && (
              <span className="text-[10px] font-semibold uppercase text-amber-600">Nổi bật</span>
            )}
          </span>
          <p className="mt-1 text-sm font-medium text-slate-800">{item.title}</p>
        </div>
        <span className="shrink-0 text-sm font-semibold text-fuo-700">
          {formatPoints(item.pricePoints)}
        </span>
      </button>
    </li>
  );
}

export default function CourseraPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [featuredCourses, setFeaturedCourses] = useState<CatalogItem[]>([]);
  const [browseCourses, setBrowseCourses] = useState<CatalogItem[]>([]);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [balance, setBalance] = useState(0);
  const [stats, setStats] = useState({ pending: 0, inProgress: 0, completed: 0 });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [loadingCatalog, setLoadingCatalog] = useState(true);
  const [searching, setSearching] = useState(false);

  const isSearchMode = search.trim().length > 0;

  const loadFeatured = useCallback(async () => {
    const items = await listCatalog(undefined, true);
    setFeaturedCourses(items);
    return items;
  }, []);

  const loadBrowse = useCallback(async (query?: string) => {
    const items = await listCatalog(query?.trim() || undefined);
    setBrowseCourses(items);
    return items;
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoadingCatalog(true);
      setCatalogError(null);
      try {
        const featured = await loadFeatured();
        const all = await loadBrowse();
        if (cancelled) return;
        const defaultId = featured[0]?.id ?? all[0]?.id ?? null;
        setSelectedId((prev) => prev ?? defaultId);
      } catch (err) {
        if (!cancelled) {
          setCatalogError(
            err instanceof ApiError ? err.message : "Không tải được danh mục khóa học",
          );
          setFeaturedCourses([]);
          setBrowseCourses([]);
        }
      } finally {
        if (!cancelled) setLoadingCatalog(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadFeatured, loadBrowse]);

  useEffect(() => {
    if (!isSearchMode) {
      setSearching(false);
      loadBrowse().catch(() => {});
      return;
    }
    setSearching(true);
    const timer = setTimeout(async () => {
      setCatalogError(null);
      try {
        const results = await loadBrowse(search);
        setSelectedId((prev) => {
          if (prev && results.some((c) => c.id === prev)) return prev;
          return results[0]?.id ?? null;
        });
      } catch (err) {
        setCatalogError(
          err instanceof ApiError ? err.message : "Không tìm được khóa học",
        );
        setBrowseCourses([]);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [search, isSearchMode, loadBrowse]);

  useEffect(() => {
    if (!user) return;
    getPointsBalance().then((r) => setBalance(r.balance)).catch(() => setBalance(0));
    getMyRequestStats()
      .then((s) =>
        setStats({ pending: s.pending, inProgress: s.inProgress, completed: s.completed }),
      )
      .catch(() => {});
  }, [user]);

  const allVisible = isSearchMode
    ? browseCourses
    : [
        ...featuredCourses,
        ...browseCourses.filter((c) => !featuredCourses.some((f) => f.id === c.id)),
      ];

  const selected =
    allVisible.find((c) => c.id === selectedId) ??
    featuredCourses.find((c) => c.id === selectedId) ??
    browseCourses.find((c) => c.id === selectedId);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user) {
      router.push("/login");
      return;
    }
    if (!selected) return;
    setError(null);
    setSubmitting(true);
    try {
      const idempotencyKey = crypto.randomUUID();
      await createCourseraRequest(
        {
          catalogItemId: selected.id,
          courseraEmail: email.trim(),
          courseraPassword: password,
          userNotes: notes.trim() || undefined,
        },
        idempotencyKey,
      );
      router.push("/coursera/orders");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể tạo yêu cầu");
    } finally {
      setSubmitting(false);
    }
  }

  if (authLoading) {
    return <p className="text-sm text-slate-500">Đang tải...</p>;
  }

  return (
    <div className="space-y-4">
      <PromoBanner />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Coursera Pro Service</h1>
          <p className="text-sm text-slate-600">Tạo yêu cầu nhanh — thanh toán bằng FUO Point</p>
        </div>
        <Link href="/coursera/orders" className="text-sm font-medium text-fuo-600 hover:underline">
          Đơn của tôi →
        </Link>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-4">
          <div className="card p-5">
            <h2 className="text-lg font-semibold text-slate-900">Tạo yêu cầu nhanh</h2>
            <div className="mt-4">
              <label className="text-xs font-medium text-slate-500" htmlFor="course-search">
                Tìm kiếm khóa học
              </label>
              <input
                id="course-search"
                className="input-field mt-1"
                placeholder="VD: ssl101, python, WOU203C"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <p className="mt-1 text-xs text-slate-500">
                Tìm theo mã khóa hoặc tên khóa (gõ ít nhất 1 ký tự).
              </p>
            </div>

            {catalogError && (
              <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{catalogError}</p>
            )}

            {loadingCatalog ? (
              <p className="mt-4 text-sm text-slate-500">Đang tải danh mục...</p>
            ) : (
              <>
                {!isSearchMode && (
                  <section className="mt-5">
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Khóa học phổ biến
                    </p>
                    {featuredCourses.length === 0 ? (
                      <p className="mt-2 text-sm text-slate-500">
                        Chưa có khóa nổi bật. Admin có thể bật &quot;Nổi bật&quot; trong danh mục.
                      </p>
                    ) : (
                      <ul className="mt-2 max-h-48 space-y-2 overflow-y-auto">
                        {featuredCourses.map((item) => (
                          <CatalogCourseButton
                            key={item.id}
                            item={item}
                            selected={selectedId === item.id}
                            onSelect={() => setSelectedId(item.id)}
                          />
                        ))}
                      </ul>
                    )}
                  </section>
                )}

                <section className="mt-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    {isSearchMode
                      ? searching
                        ? "Đang tìm kiếm..."
                        : `Kết quả tìm kiếm (${browseCourses.length})`
                      : "Tất cả khóa học"}
                  </p>
                  {!isSearchMode &&
                  browseCourses.filter((c) => !featuredCourses.some((f) => f.id === c.id)).length ===
                    0 &&
                  featuredCourses.length > 0 ? (
                    <p className="mt-2 text-sm text-slate-500">
                      Các khóa còn lại trùng với mục phổ biến ở trên.
                    </p>
                  ) : null}
                  {isSearchMode && !searching && browseCourses.length === 0 ? (
                    <p className="mt-2 text-sm text-slate-500">
                      Không tìm thấy khóa phù hợp với &quot;{search.trim()}&quot;.
                    </p>
                  ) : (
                    <ul className="mt-2 max-h-64 space-y-2 overflow-y-auto">
                      {(isSearchMode
                        ? browseCourses
                        : browseCourses.filter(
                            (c) => !featuredCourses.some((f) => f.id === c.id),
                          )
                      ).map((item) => (
                        <CatalogCourseButton
                          key={item.id}
                          item={item}
                          selected={selectedId === item.id}
                          onSelect={() => setSelectedId(item.id)}
                        />
                      ))}
                    </ul>
                  )}
                </section>
              </>
            )}

            {!user && (
              <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <Link href="/login" className="font-medium underline">
                  Đăng nhập
                </Link>{" "}
                để tạo yêu cầu và thanh toán.
              </p>
            )}

            <form onSubmit={handleSubmit} className="mt-6 space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="text-xs font-medium text-slate-600">Email Coursera</label>
                  <input
                    className="input-field mt-1"
                    type="email"
                    required
                    disabled={!user}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </div>
                <div>
                  <label className="text-xs font-medium text-slate-600">Mật khẩu Coursera</label>
                  <div className="relative mt-1">
                    <input
                      className="input-field pr-10"
                      type={showPassword ? "text" : "password"}
                      required
                      disabled={!user}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                    />
                    <button
                      type="button"
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-slate-500"
                      onClick={() => setShowPassword((v) => !v)}
                    >
                      {showPassword ? "Ẩn" : "Hiện"}
                    </button>
                  </div>
                </div>
              </div>
              <div className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-900">
                Dùng mật khẩu tài khoản Coursera (không phải mật khẩu Gmail). Nếu đăng nhập bằng Google,
                hãy đặt mật khẩu Coursera trong phần Cài đặt tài khoản Coursera trước khi gửi yêu cầu.
              </div>
              <div>
                <label className="text-xs font-medium text-slate-600">Ghi chú đặc biệt (không bắt buộc)</label>
                <textarea
                  className="input-field mt-1 min-h-[80px]"
                  disabled={!user}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Yêu cầu thêm về khóa học, deadline..."
                />
              </div>
              {error && (
                <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
              )}
              {selected && (
                <p className="text-sm text-slate-600">
                  Tổng thanh toán:{" "}
                  <span className="font-semibold text-fuo-700">{formatPoints(selected.pricePoints)}</span>
                </p>
              )}
              <button
                type="submit"
                className="btn-primary w-full sm:w-auto"
                disabled={!user || submitting || !selected}
              >
                {submitting ? "Đang xử lý..." : "Tạo yêu cầu và thanh toán"}
              </button>
            </form>
          </div>
        </div>

        <aside className="space-y-4">
          <div className="card p-4">
            <h3 className="font-semibold text-slate-900">Tài khoản của bạn</h3>
            <p className="mt-2 text-sm text-slate-600">
              Số dư hiện tại:{" "}
              <span className="font-bold text-fuo-700">{formatPoints(balance)}</span>
            </p>
            <dl className="mt-3 grid grid-cols-3 gap-2 text-center text-xs">
              <div className="rounded-lg bg-slate-50 p-2">
                <dt className="text-slate-500">Chờ xử lý</dt>
                <dd className="font-bold text-slate-800">{stats.pending}</dd>
              </div>
              <div className="rounded-lg bg-slate-50 p-2">
                <dt className="text-slate-500">Đang làm</dt>
                <dd className="font-bold text-slate-800">{stats.inProgress}</dd>
              </div>
              <div className="rounded-lg bg-slate-50 p-2">
                <dt className="text-slate-500">Hoàn thành</dt>
                <dd className="font-bold text-slate-800">{stats.completed}</dd>
              </div>
            </dl>
            <button
              type="button"
              className="mt-4 w-full rounded-lg bg-pink-500 px-3 py-2 text-sm font-semibold text-white opacity-60"
              disabled
              title="Module nạp tiền sẽ có sau"
            >
              + Nạp thêm FUO Point (sắp có)
            </button>
          </div>
          <div className="card p-4 text-sm text-slate-600">
            <h3 className="font-semibold text-slate-900">Hướng dẫn sử dụng</h3>
            <ol className="mt-2 list-decimal space-y-1 pl-4">
              <li>Chọn khóa học Coursera cần hỗ trợ.</li>
              <li>Nhập email và mật khẩu Coursera.</li>
              <li>Thanh toán bằng FUO Point và chờ xử lý.</li>
              <li>Theo dõi trạng thái tại Lịch sử yêu cầu.</li>
            </ol>
          </div>
          <Link href="/coursera/orders" className="btn-secondary block w-full text-center">
            Xem tất cả yêu cầu
          </Link>
        </aside>
      </div>
    </div>
  );
}

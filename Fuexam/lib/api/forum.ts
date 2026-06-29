import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";



export interface Forum {

  id: string;

  slug: string;

  title: string;

  description: string | null;

  visibility: string;

  createdAt: string;

  parentForumId?: string | null;

  children?: Forum[];

}



export interface Category {

  id: string;

  forumId: string;

  parentId: string | null;

  slug: string;

  title: string;

  description: string | null;

  visibility: string;

  iconColor: string | null;

  sortOrder: number;

}



export interface CategoryLatestActivity {

  threadId: string;

  threadTitle: string;

  threadType: string;

  authorHandle: string | null;

  postedAt: string;

}



export interface CategoryTreeNode {

  id: string;

  forumId: string;

  parentId: string | null;

  slug: string;

  title: string;

  description: string | null;

  visibility: string;

  iconColor: string | null;

  sortOrder: number;

  topicCount: number;

  postCount: number;

  latestActivity: CategoryLatestActivity | null;

  children: CategoryTreeNode[];

}



export type ThreadType = "discussion" | "article" | "poll" | "question";



export interface ThreadSummary {

  id: string;

  forumId: string;

  categoryId: string;

  title: string;

  slug: string;

  status: string;

  threadType: ThreadType;

  authorHandle: string | null;

  sourceUrl: string | null;

  campus: string | null;

  semester: string | null;

  materialType: string | null;

  tags: string | null;

  replyCount: number;

  viewCount: number;

  lastPostAt: string;

  createdAt: string;

}



export interface ThreadDetail extends ThreadSummary {

  reactionCount: number;

  updatedAt: string;

}



export interface ThreadPage {

  items: ThreadSummary[];

  page: number;

  size: number;

  totalElements: number;

  totalPages: number;

}



export interface Post {

  id: string;

  threadId: string;

  authorUserId: string;

  parentPostId: string | null;

  authorHandle: string | null;

  authorAvatarUrl: string | null;

  bodyMd: string;

  bodyHtml: string;

  status: string;

  editVersion: number;

  reactionCount: number;

  sourceUrl: string | null;

  attachments: PostAttachment[];

  createdAt: string;

  lastEditedAt: string | null;

}



export interface PostAttachment {

  fileId: string;

  originalFilename: string;

  mimeType: string;

  sizeBytes: number;

}



export interface PostPage {

  items: Post[];

  page: number;

  size: number;

  totalElements: number;

  totalPages: number;

}



export interface CreateThreadPayload {

  categoryId: string;

  title: string;

  body: string;

  threadType: ThreadType;

  campus?: string;

  semester?: string;

  materialType?: string;

  tags?: string;

  pollOptions?: string[];

  watchThread?: boolean;

  attachmentFileIds?: string[];

}



export interface ThreadBookmarkStatus {

  threadId: string;

  watched: boolean;

}



export type ThreadTab = "discussion" | "confession" | "popular";



export function listForums() {

  return apiFetch<Forum[]>(`${API_V1}/forums`);

}



export function getForum(forumSlug: string) {

  return apiFetch<Forum>(`${API_V1}/forums/${encodeURIComponent(forumSlug)}`);

}



export function listCategories(forumSlug: string) {

  return apiFetch<Category[]>(`${API_V1}/forums/${encodeURIComponent(forumSlug)}/categories`);

}



export function listCategoryTree(forumSlug: string) {

  return apiFetch<CategoryTreeNode[]>(

    `${API_V1}/forums/${encodeURIComponent(forumSlug)}/categories/tree`,

  );

}



export function getCategory(forumSlug: string, categorySlug: string) {

  return apiFetch<Category>(

    `${API_V1}/forums/${encodeURIComponent(forumSlug)}/categories/${encodeURIComponent(categorySlug)}`,

  );

}



export function browseThreads(opts?: {

  forumId?: string;

  categoryId?: string;

  page?: number;

  size?: number;

}) {

  const params = new URLSearchParams({

    page: String(opts?.page ?? 0),

    size: String(opts?.size ?? 20),

  });

  if (opts?.forumId) params.set("forumId", opts.forumId);

  if (opts?.categoryId) params.set("categoryId", opts.categoryId);

  return apiFetch<ThreadPage>(`${API_V1}/threads?${params}`);

}



export function getThread(threadId: string) {

  return apiFetch<ThreadDetail>(`${API_V1}/threads/${threadId}`);

}



export function createThread(payload: CreateThreadPayload) {

  return apiFetch<ThreadDetail>(`${API_V1}/threads`, {

    method: "POST",

    body: JSON.stringify(payload),

  });

}



export function listThreadPosts(threadId: string, page = 0, size = 20) {

  const params = new URLSearchParams({ page: String(page), size: String(size) });

  return apiFetch<PostPage>(`${API_V1}/threads/${threadId}/posts?${params}`);

}



export function createPost(
  threadId: string,
  body: string,
  parentPostId?: string,
  attachmentFileIds?: string[],
) {
  return apiFetch<Post>(`${API_V1}/threads/${threadId}/posts`, {
    method: "POST",
    body: JSON.stringify({
      body,
      parentPostId: parentPostId ?? null,
      attachmentFileIds: attachmentFileIds ?? [],
    }),
  });
}



export function updatePost(threadId: string, postId: string, body: string) {

  return apiFetch<Post>(`${API_V1}/threads/${threadId}/posts/${postId}`, {

    method: "PATCH",

    body: JSON.stringify({ body }),

  });

}



export function deletePost(threadId: string, postId: string) {

  return apiFetch<void>(`${API_V1}/threads/${threadId}/posts/${postId}`, {

    method: "DELETE",

  });

}



export function listWatchedThreads(page = 0, size = 20) {

  const params = new URLSearchParams({ page: String(page), size: String(size) });

  return apiFetch<ThreadPage>(`${API_V1}/users/me/thread-bookmarks?${params}`);

}



export function getThreadBookmarkStatus(threadId: string) {

  return apiFetch<ThreadBookmarkStatus>(`${API_V1}/threads/${threadId}/bookmark`);

}



export function watchThread(threadId: string) {

  return apiFetch<ThreadBookmarkStatus>(`${API_V1}/threads/${threadId}/bookmark`, {

    method: "POST",

  });

}



export function unwatchThread(threadId: string) {

  return apiFetch<ThreadBookmarkStatus>(`${API_V1}/threads/${threadId}/bookmark`, {

    method: "DELETE",

  });

}



export function authorInitial(handle: string | null | undefined): string {

  if (!handle?.trim()) return "?";

  return handle.trim().charAt(0).toUpperCase();

}



export function applyThreadTab(threads: ThreadSummary[], tab: ThreadTab): ThreadSummary[] {

  switch (tab) {

    case "popular":

      return [...threads].sort((a, b) => b.viewCount - a.viewCount);

    case "confession":

      return threads.filter(

        (t) =>

          t.slug.toLowerCase().includes("confession") ||

          t.title.toLowerCase().includes("confession"),

      );

    default:

      return threads;

  }

}


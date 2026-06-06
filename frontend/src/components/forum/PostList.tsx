import type { Post } from "@/lib/api/forum";
import { PostItem } from "@/components/forum/PostItem";

interface PostListProps {
  posts: Post[];
  threadId: string;
  onChanged: () => void;
}

export function PostList({ posts, threadId, onChanged }: PostListProps) {
  if (posts.length === 0) {
    return <p className="text-sm text-slate-500">Chưa có bài viết nào trong chủ đề này.</p>;
  }

  const childrenByParent = new Map<string, Post[]>();
  const topLevel: Post[] = [];

  for (const post of posts) {
    if (post.parentPostId) {
      const siblings = childrenByParent.get(post.parentPostId) ?? [];
      siblings.push(post);
      childrenByParent.set(post.parentPostId, siblings);
    } else {
      topLevel.push(post);
    }
  }

  return (
    <div className="divide-y divide-slate-100">
      {topLevel.map((post, index) => (
        <div key={post.id}>
          <PostItem post={post} index={index} threadId={threadId} onChanged={onChanged} />
          {(childrenByParent.get(post.id) ?? []).map((child, childIndex) => (
            <PostItem
              key={child.id}
              post={child}
              index={index + childIndex + 1}
              threadId={threadId}
              nested
              onChanged={onChanged}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

import type { MockThread } from "@/types/api";

interface ThreadTableProps {
  threads: MockThread[];
}

export function ThreadTable({ threads }: ThreadTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <thead>
          <tr className="border-b border-slate-100 text-left text-xs uppercase tracking-wide text-slate-500">
            <th className="px-3 py-2 font-medium">#</th>
            <th className="px-3 py-2 font-medium">Chủ đề</th>
            <th className="hidden px-3 py-2 font-medium sm:table-cell">Diễn đàn</th>
            <th className="px-3 py-2 font-medium text-center">Trả lời</th>
            <th className="hidden px-3 py-2 font-medium text-center md:table-cell">
              Xem
            </th>
            <th className="hidden px-3 py-2 font-medium lg:table-cell">
              Hoạt động cuối
            </th>
            <th className="px-3 py-2 font-medium">Tác giả</th>
          </tr>
        </thead>
        <tbody>
          {threads.map((thread, index) => (
            <tr
              key={thread.id}
              className="border-b border-slate-50 transition hover:bg-slate-50/80"
            >
              <td className="px-3 py-3 text-slate-400">{index + 1}</td>
              <td className="px-3 py-3">
                <div className="flex flex-col gap-1">
                  <span className="inline-flex w-fit rounded bg-fuo-50 px-1.5 py-0.5 text-xs font-medium text-fuo-700">
                    {thread.tag}
                  </span>
                  <span className="font-medium text-slate-800 hover:text-fuo-700">
                    {thread.title}
                  </span>
                </div>
              </td>
              <td className="hidden px-3 py-3 text-slate-600 sm:table-cell">
                {thread.forum}
              </td>
              <td className="px-3 py-3 text-center text-slate-600">
                {thread.replies}
              </td>
              <td className="hidden px-3 py-3 text-center text-slate-600 md:table-cell">
                {thread.views.toLocaleString()}
              </td>
              <td className="hidden px-3 py-3 text-slate-500 lg:table-cell">
                {thread.lastActivity}
              </td>
              <td className="px-3 py-3">
                <div className="flex items-center gap-2">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-600">
                    {thread.authorInitial}
                  </span>
                  <span className="text-slate-700">{thread.author}</span>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

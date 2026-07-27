"use client";

import { useEffect, useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getCheckScoreConfig, updateCheckScoreConfig } from "@/lib/api/check-score-config";
import { ApiError } from "@/lib/api/client";

export default function CheckScoreConfigPage() {
  const [configured, setConfigured] = useState(false);
  const [cookieConfigured, setCookieConfigured] = useState(false);
  const [authorizeKey, setAuthorizeKey] = useState("");
  const [xsrfCookie, setXsrfCookie] = useState("");
  const [showAuthorizeKey, setShowAuthorizeKey] = useState(false);
  const [showCookie, setShowCookie] = useState(false);
  const [checkScoreUrl, setCheckScoreUrl] = useState("https://api.ask-4-help.com/api/v2/api/check-score");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getCheckScoreConfig()
      .then((config) => {
        setConfigured(config.configured);
        setCookieConfigured(config.cookieConfigured);
        setAuthorizeKey(config.authorizeKey ?? "");
        setXsrfCookie(config.xsrfCookie ?? "");
        setCheckScoreUrl(config.checkScoreUrl);
      })
      .catch((error) => toast.error(error instanceof ApiError ? error.message : "Không tải được cấu hình."))
      .finally(() => setLoading(false));
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    try {
      const config = await updateCheckScoreConfig(authorizeKey, xsrfCookie, checkScoreUrl);
      setConfigured(config.configured);
      setCookieConfigured(config.cookieConfigured);
      setCheckScoreUrl(config.checkScoreUrl);
      setAuthorizeKey(config.authorizeKey ?? "");
      setXsrfCookie(config.xsrfCookie ?? "");
      toast.success("Đã lưu token chấm điểm.");
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : "Lưu token thất bại.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <AdminShell title="Check điểm" description="Cấu hình token gọi API chấm điểm">
      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle className="text-base">Token Ask 4 Help</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Trạng thái: {loading ? "Đang tải..." : configured ? "Đã cấu hình" : "Chưa cấu hình"}
            </p>
            <p className="text-sm text-muted-foreground">
              Cookie: {loading ? "Đang tải..." : cookieConfigured ? "Đã cấu hình" : "Chưa cấu hình"}
            </p>
            <div className="space-y-2">
              <Label htmlFor="authorize-key">X-Authorize-Key</Label>
              <div className="relative">
                <Input
                  id="authorize-key"
                  type={showAuthorizeKey ? "text" : "password"}
                  value={authorizeKey}
                  onChange={(event) => setAuthorizeKey(event.target.value)}
                  placeholder="a4h_..."
                  required
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowAuthorizeKey((value) => !value)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground"
                  aria-label={showAuthorizeKey ? "Ẩn token" : "Hiện token"}
                >
                  {showAuthorizeKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="check-score-url">API URL</Label>
              <Input
                id="check-score-url"
                type="url"
                value={checkScoreUrl}
                onChange={(event) => setCheckScoreUrl(event.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="xsrf-cookie">Cookie</Label>
              <div className="relative">
                <Input
                  id="xsrf-cookie"
                  type={showCookie ? "text" : "password"}
                  value={xsrfCookie}
                  onChange={(event) => setXsrfCookie(event.target.value)}
                  placeholder="XSRF-TOKEN=..."
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowCookie((value) => !value)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground"
                  aria-label={showCookie ? "Ẩn cookie" : "Hiện cookie"}
                >
                  {showCookie ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>
            <Button type="submit" disabled={saving}>
              {saving ? "Đang lưu..." : "Lưu token"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </AdminShell>
  );
}

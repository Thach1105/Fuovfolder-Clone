"use client";

import { useEffect, useState } from "react";
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
  const [checkScoreUrl, setCheckScoreUrl] = useState("https://api.ask-4-help.com/api/v2/api/check-score");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getCheckScoreConfig()
      .then((config) => {
        setConfigured(config.configured);
        setCookieConfigured(config.cookieConfigured);
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
      setAuthorizeKey("");
      setXsrfCookie("");
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
              <Input
                id="authorize-key"
                type="password"
                value={authorizeKey}
                onChange={(event) => setAuthorizeKey(event.target.value)}
                placeholder={configured ? "Để trống nếu không đổi token" : "a4h_..."}
                required={!configured}
              />
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
              <Input
                id="xsrf-cookie"
                type="password"
                value={xsrfCookie}
                onChange={(event) => setXsrfCookie(event.target.value)}
                placeholder={cookieConfigured ? "Để trống nếu không đổi cookie" : "XSRF-TOKEN=..."}
              />
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

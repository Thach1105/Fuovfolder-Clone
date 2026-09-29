"""Send raw FE/PE exports in bounded batches. Dry-run unless --send is supplied."""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request


def encode_batch(items):
    # Identity includes content and order: a retry of the same batch has the same event IDs.
    content = json.dumps(items, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    event_id = "files:" + hashlib.sha256(content.encode("utf-8")).hexdigest()
    return json.dumps({"eventId": event_id, "papers": items}, ensure_ascii=False,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def batches(items, max_count=50, max_bytes=32 * 1024 * 1024):
    pending = []
    for item in items:
        candidate = pending + [item]
        if len(candidate) > max_count or len(encode_batch(candidate)) > max_bytes:
            if pending:
                yield encode_batch(pending)
            pending = [item]
            if len(encode_batch(pending)) > max_bytes:
                raise ValueError("One paper exceeds the payload limit; split/compress its source assets first.")
        else:
            pending = candidate
    if pending:
        yield encode_batch(pending)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--url", help="Full HTTPS webhook URL")
    parser.add_argument("--send", action="store_true")
    args = parser.parse_args()
    items = []
    for path in args.files:
        try:
            item = json.loads(path.read_text(encoding="utf-8-sig"))
            if not isinstance(item, dict):
                raise ValueError("root must be a JSON object")
            items.append(item)
        except (ValueError, OSError) as exc:
            # Never print source excerpts, which can contain credentials/base64 content.
            if isinstance(exc, json.JSONDecodeError):
                parser.error(f"{path.name}: invalid JSON at line {exc.lineno}, column {exc.colno}")
            if isinstance(exc, UnicodeDecodeError):
                parser.error(f"{path.name}: invalid UTF-8 at byte {exc.start}; re-export the source, do not replace corrupt bytes")
            parser.error(f"{path.name}: {type(exc).__name__}")
    try:
        payloads = list(batches(items))
    except ValueError as exc:
        parser.error(str(exc))
    print(f"{len(items)} papers, {len(payloads)} batches; this checks JSON/size, not server content validation.")
    if not args.send:
        print("DRY RUN: no network request sent. Use --send after reviewing the input.")
        return
    client = os.environ.get("EXAM_WEBHOOK_CLIENT", "")
    secret = os.environ.get("EXAM_WEBHOOK_SECRET", "")
    if not client or not secret or not args.url or not args.url.startswith("https://"):
        parser.error("Set EXAM_WEBHOOK_CLIENT and EXAM_WEBHOOK_SECRET, and provide an HTTPS --url.")
    failed = False
    for body in payloads:
        timestamp = str(int(time.time()))
        signature = hmac.new(secret.encode(), timestamp.encode() + b"." + body, hashlib.sha256).hexdigest()
        request = urllib.request.Request(args.url, data=body, method="POST", headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Exam-Client": client,
            "X-Exam-Signature": f"t={timestamp},v1={signature}",
        })
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                result = json.load(response)
                data = result.get("data", {})
                failed |= (not result.get("success", False) or data.get("rejected", 0) > 0
                           or any(item.get("status") == "failed" for item in data.get("results", [])))
                print(json.dumps({"httpStatus": response.status, "response": result}, ensure_ascii=False))
                print("Accepted/queued is not published. Track receiptId in admin webhook events.")
        except urllib.error.HTTPError as exc:
            failed = True
            print(f"HTTP {exc.code}: {exc.read().decode('utf-8', errors='replace')}")
        except (urllib.error.URLError, TimeoutError, ValueError) as exc:
            failed = True
            print(f"UNKNOWN delivery result: {type(exc).__name__}; retry identical input to preserve event IDs.")
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

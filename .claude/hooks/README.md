# Optional hooks

Không bật hook tự động trong gói này để tránh Claude tự chạy lệnh ngoài ý muốn.

Nếu muốn enforce format/test sau này, hãy tự thêm vào `.claude/settings.json` sau khi project có formatter/test ổn định.

Gợi ý hook sau khi project đã có Checkstyle/Spotless:

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "cd backend && mvn -q -pl app -am test"
          }
        ]
      }
    ]
  }
}
```

Không nên bật hook này trong giai đoạn đầu nếu mỗi lần edit đều chạy Maven quá lâu.

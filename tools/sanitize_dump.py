#!/usr/bin/env python3
"""Make a screen dump safe to keep in the repo as a test fixture.

Keeps only the windows of one package and replaces every text / content-description value
that is not a generic UI label with a stable hash token (h_xxxxxx). Equal strings stay equal
and different strings stay different, so fingerprint tests still work, but names and message
text are not readable.

usage: sanitize_dump.py <dump.txt> <package> <out.txt> [extra-safe-label ...]
"""
import hashlib
import re
import sys

SAFE = {
    "Home", "Reels", "Shorts", "Search", "Search and explore", "Profile", "Message", "Messages",
    "Create", "Create a reel", "More", "More actions", "Like", "Comment", "Share", "Save", "Send",
    "Subscribe", "Subscriptions", "You", "All", "Back", "Camera", "Notifications", "Follow",
    "Following", "Remix", "Audio", "Direct", "Inbox", "Primary", "General", "Requests",
}


def token(value: str) -> str:
    return "h_" + hashlib.sha1(value.encode("utf-8")).hexdigest()[:6]


def main() -> None:
    src, pkg, dst, *extra = sys.argv[1:]
    safe = SAFE | set(extra)
    lines = open(src, encoding="utf-8").read().splitlines()

    keep, inside = [], False
    for line in lines:
        if line.startswith("=== WINDOW"):
            inside = f"pkg={pkg} " in line
            if inside:
                line = re.sub(r'title="[^"]*"', 'title=""', line)
        if inside:
            keep.append(line)

    reel = re.compile(r"^Reel by (.*)\. Double tap to play or pause\.$")

    def clean(m: re.Match) -> str:
        value = m.group(2)
        if value in safe:
            return f'{m.group(1)}"{value}"'
        r = reel.match(value)
        if r:  # keep the descriptive frame (a rule fallback), hide the creator
            return f'{m.group(1)}"Reel by {token(r.group(1))}. Double tap to play or pause."'
        return f'{m.group(1)}"{token(value)}"'

    out = []
    for line in keep:
        if not line.startswith("==="):
            line = re.sub(r'(\btext=)"([^"]*)"', clean, line)
            line = re.sub(r'(\bdesc=)"([^"]*)"', clean, line)
        out.append(line)
    open(dst, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print(f"{dst}: {len(out)} lines")


if __name__ == "__main__":
    main()

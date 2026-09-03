"""Builds a local Caddyfile out of the production one.

The point of this rehearsal is the two header directives, so they are copied
from deploy/aws/Caddyfile rather than retyped, and asserted to have survived the
copy. Only the site address and the upstream port differ from production.
"""
import re
import sys

production = open(sys.argv[1], encoding="utf-8").read()
out = sys.argv[2]

body = production[production.index("{", production.index("\n")) + 1:production.rindex("}")]

REQUIRED = ["request_header -Forwarded", "header_up X-Forwarded-For {http.request.remote.host}"]
for directive in REQUIRED:
    if directive not in body:
        sys.exit(f"production Caddyfile no longer contains: {directive}")

spring = body.replace("127.0.0.1:8080", "127.0.0.1:18083")
echo = body.replace("127.0.0.1:8080", "127.0.0.1:18084")

config = "{\n\tauto_https off\n\tadmin off\n}\n\n"
config += ":18443 {" + spring + "}\n\n"
config += ":18444 {" + echo + "}\n"
open(out, "w", encoding="utf-8").write(config)

for directive in REQUIRED:
    if config.count(directive) != 2:
        sys.exit(f"generated config lost a directive: {directive}")
print("generated from production, both directives intact in both site blocks")

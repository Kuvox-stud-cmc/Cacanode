#!/usr/bin/env python3
"""Provision disposable cache-baseline credentials for the local seeded tenant."""

from __future__ import annotations

import json
import os
import shlex
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def request_json(
    base_url: str,
    method: str,
    path: str,
    *,
    access_token: str | None = None,
    body: dict[str, Any] | None = None,
) -> Any:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    request = Request(f"{base_url}{path}", data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            payload = response.read()
            return json.loads(payload) if payload else None
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"{method} {path} failed with HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise SystemExit(f"Unable to reach {base_url}: {error.reason}") from error


def main() -> None:
    base_url = os.getenv("BASELINE_BUSINESS_API_BASE_URL", "http://localhost:8080").rstrip("/")
    email = os.getenv("DEV_SEED_EMAIL", "admin@cacanode.local")
    password = os.getenv("DEV_SEED_PASSWORD", "Cacanode@123")
    token_name = os.getenv("BASELINE_INTEGRATION_TOKEN_NAME", "Development cache baseline")
    knowledge_base_id = os.getenv(
        "BASELINE_KNOWLEDGE_BASE_ID", "00000000-0000-0000-0000-000000000004"
    )
    chatbot_id = os.getenv(
        "BASELINE_PLAYGROUND_CHATBOT_ID", "00000000-0000-0000-0000-000000000005"
    )

    login = request_json(
        base_url,
        "POST",
        "/api/v1/auth/login",
        body={"email": email, "password": password, "rememberMe": False},
    )
    access_token = login.get("accessToken") if isinstance(login, dict) else None
    if not access_token:
        raise SystemExit(
            "Seed login did not return an access token. Ensure the dev profile bypasses 2FA for "
            f"{email}."
        )

    tokens = request_json(
        base_url, "GET", "/api/v1/tenants/me/integrations/tokens", access_token=access_token
    )
    for token in tokens if isinstance(tokens, list) else []:
        if token.get("name") == token_name and token.get("revokedAt") is None:
            request_json(
                base_url,
                "DELETE",
                f"/api/v1/tenants/me/integrations/tokens/{token['id']}",
                access_token=access_token,
            )

    created = request_json(
        base_url,
        "POST",
        "/api/v1/tenants/me/integrations/tokens",
        access_token=access_token,
        body={"name": token_name, "scopes": ["api:chat"], "expiresAt": None},
    )
    integration_token = created.get("secret") if isinstance(created, dict) else None
    if not integration_token:
        raise SystemExit("Integration-token creation did not return a secret")

    exports = {
        "BASELINE_BUSINESS_API_BASE_URL": base_url,
        "BASELINE_ACCESS_TOKEN": access_token,
        "BASELINE_INTEGRATION_TOKEN": integration_token,
        "BASELINE_KNOWLEDGE_BASE_ID": knowledge_base_id,
        "BASELINE_PLAYGROUND_CHATBOT_ID": chatbot_id,
    }
    for name, value in exports.items():
        print(f"export {name}={shlex.quote(value)}")
    print(
        "# Credentials are disposable. Run this command again to rotate them.",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()

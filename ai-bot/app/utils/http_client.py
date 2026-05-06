import os
import httpx

GATEWAY_BASE = os.getenv("GATEWAY_BASE_URL", "http://localhost:9000")
_TIMEOUT = httpx.Timeout(10.0)


async def get(path: str, params: dict | None = None, token: str | None = None) -> dict:
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        resp = client.build_request("GET", GATEWAY_BASE + path, params=params, headers=headers)
        r = await client.send(resp)
        r.raise_for_status()
        return r.json()

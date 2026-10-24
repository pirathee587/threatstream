#!/usr/bin/env python3
"""Small, dependency-free and deliberately bounded local HTTP load test.

It never permits a non-local target.  It is intended for repeatable developer
benchmarks, not a replacement for a distributed load-testing platform.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import json
import statistics
import sys
import time
from collections import Counter
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}


def percentile(samples: list[float], p: float) -> float:
    ordered = sorted(samples)
    if not ordered:
        return 0.0
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * p))]


def request(url: str, method: str, body: bytes | None, headers: dict[str, str]) -> tuple[int, float]:
    started = time.perf_counter()
    try:
        req = Request(url, data=body, headers=headers, method=method)
        with urlopen(req, timeout=10) as response:
            response.read()
            return response.status, (time.perf_counter() - started) * 1000
    except HTTPError as error:
        error.read()
        return error.code, (time.perf_counter() - started) * 1000
    except (URLError, TimeoutError, OSError):
        return 0, (time.perf_counter() - started) * 1000


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8083")
    parser.add_argument("--endpoint", choices=("list", "statistics", "login", "acknowledge", "resolve"), default="list")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=2)
    parser.add_argument("--alert-id", help="required for acknowledge or resolve")
    parser.add_argument("--api-key")
    parser.add_argument("--login-base-url", default="http://localhost:8084")
    parser.add_argument("--allow-local-target", action="store_true")
    args = parser.parse_args()

    if not args.allow_local_target:
        parser.error("pass --allow-local-target after confirming this is a local environment")
    parsed = urlparse(args.login_base_url if args.endpoint == "login" else args.base_url)
    if parsed.hostname not in LOCAL_HOSTS:
        parser.error("only localhost, 127.0.0.1, and ::1 are permitted")
    if not 1 <= args.requests <= 1000 or not 1 <= args.concurrency <= 32:
        parser.error("requests must be 1..1000 and concurrency must be 1..32")
    if args.endpoint in {"acknowledge", "resolve"} and not args.alert_id:
        parser.error("--alert-id is required for state-change tests")

    headers = {"Accept": "application/json"}
    if args.api_key:
        headers["X-API-Key"] = args.api_key
    method, body = "GET", None
    if args.endpoint == "list":
        url = f"{args.base_url.rstrip('/')}/api/alerts?page=0&size=50&sortBy=detectedAt&direction=DESC"
    elif args.endpoint == "statistics":
        url = f"{args.base_url.rstrip('/')}/api/alerts/statistics"
    elif args.endpoint == "login":
        url, method = f"{args.login_base_url.rstrip('/')}/api/demo/auth/login", "POST"
        body = json.dumps({"username": "load-test", "password": "invalid-load-test-password"}).encode()
        headers["Content-Type"] = "application/json"
    else:
        url, method = f"{args.base_url.rstrip('/')}/api/alerts/{args.alert_id}/{args.endpoint}", "PATCH"

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results = list(executor.map(lambda _: request(url, method, body, headers), range(args.requests)))
    elapsed = time.perf_counter() - started
    statuses = Counter(status for status, _ in results)
    latencies = [latency for _, latency in results]
    successful = sum(count for status, count in statuses.items() if 200 <= status < 400)
    print(json.dumps({
        "endpoint": args.endpoint, "requests": args.requests, "concurrency": args.concurrency,
        "elapsed_seconds": round(elapsed, 3), "requests_per_second": round(args.requests / elapsed, 2),
        "http_statuses": dict(sorted(statuses.items())), "error_rate": round(1 - successful / args.requests, 4),
        "latency_ms": {"median": round(statistics.median(latencies), 2), "p95": round(percentile(latencies, .95), 2), "p99": round(percentile(latencies, .99), 2)},
    }, indent=2))
    return 0 if successful == args.requests else 2


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Structural OpenAPI path comparison.

Compares the set of paths declared in two OpenAPI YAML files. Exits 0 when
they match exactly, 1 when they differ.

This is a fallback for `oasdiff` — used when the binary isn't installed in
the CI runner. Once `mvn` is available in CI and we can boot the service to
generate a live OAS, the proper `oasdiff diff` invocation supersedes this.

Usage:
    python3 scripts/quality/oas_path_diff.py <baseline.yaml> <live.yaml>
"""
import sys
import yaml


def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <baseline.yaml> <live.yaml>", file=sys.stderr)
        return 2

    baseline_path, live_path = sys.argv[1], sys.argv[2]
    with open(baseline_path, encoding="utf-8") as f:
        baseline = yaml.safe_load(f) or {}
    with open(live_path, encoding="utf-8") as f:
        live = yaml.safe_load(f) or {}

    base_paths = sorted((baseline.get("paths") or {}).keys())
    live_paths = sorted((live.get("paths") or {}).keys())

    if base_paths != live_paths:
        print(
            f"OAS path drift detected:\n  baseline={base_paths}\n  live={live_paths}",
            file=sys.stderr,
        )
        return 1

    print(f"OAS paths match: {base_paths}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""
Tests for the readiness-check trigger fix.

Per `docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`,
both `scripts/quality/operational_readiness_check.py` and
`scripts/security/pci_readiness_check.py` must be advisory until
`.human-approvals/pci-production-approved.txt` exists, and strict once it does.

Each test:
  - Materialises a minimal workspace in `tmp_path`.
  - Invokes the script via `subprocess.run` with `cwd=tmp_path` (the scripts
    use bare relative paths like `Path('docs/operations/runbook.md')`).
  - Asserts on the script's exit code.

These tests intentionally do NOT exercise the full required-files catalogue;
they verify the *trigger* behaviour, which is the surface area of the fix.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
OPS_SCRIPT = REPO_ROOT / 'scripts' / 'quality' / 'operational_readiness_check.py'
PCI_SCRIPT = REPO_ROOT / 'scripts' / 'security' / 'pci_readiness_check.py'

PROD_MARKER_RELPATH = Path('.human-approvals/pci-production-approved.txt')
PROD_MARKER_CONTENT = 'APPROVED_FOR_PCI_PRODUCTION_RELEASE\n'

PCI_PROFILE_RELPATH = Path('security-profile.yml')
PCI_PROFILE_CONTENT = 'profile: pci_dss_tier1\n'


def _run(script: Path, cwd: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(script)],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        check=False,
    )


def _write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)


# ---------------------------------------------------------------------------
# Advisory mode — no production-approval marker present
# ---------------------------------------------------------------------------

def test_ops_check_advisory_when_no_prod_marker(tmp_path: Path) -> None:
    # No production marker; no required docs. Strict trigger should not fire.
    result = _run(OPS_SCRIPT, cwd=tmp_path)
    assert result.returncode == 0, f'expected advisory exit 0, got {result.returncode}: {result.stdout} {result.stderr}'
    assert 'advisory mode' in result.stdout.lower()


def test_ops_check_advisory_when_only_implementation_marker_present(tmp_path: Path) -> None:
    """The implementation-approved marker MUST NOT trigger strict mode after the fix.

    This is the regression test for the directive: the old code keyed off
    `implementation-approved.txt`, which made it strict at the start of code
    rather than at the production-readiness boundary.
    """
    _write(tmp_path / '.human-approvals' / 'implementation-approved.txt', 'APPROVED_FOR_IMPLEMENTATION\n')
    _write(tmp_path / '.human-approvals' / 'pci-security-approved.txt', 'APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION\n')
    # Also place a non-empty src/ to confirm src/ no longer flips the trigger.
    _write(tmp_path / 'src' / 'main' / 'java' / 'Dummy.java', 'class Dummy {}\n')
    result = _run(OPS_SCRIPT, cwd=tmp_path)
    assert result.returncode == 0, f'expected advisory exit 0, got {result.returncode}: {result.stdout} {result.stderr}'
    assert 'advisory mode' in result.stdout.lower()


def test_pci_check_advisory_when_no_prod_marker(tmp_path: Path) -> None:
    _write(tmp_path / PCI_PROFILE_RELPATH, PCI_PROFILE_CONTENT)
    result = _run(PCI_SCRIPT, cwd=tmp_path)
    assert result.returncode == 0, f'expected advisory exit 0, got {result.returncode}: {result.stdout} {result.stderr}'
    assert 'advisory mode' in result.stdout.lower()


def test_pci_check_advisory_when_only_security_marker_present(tmp_path: Path) -> None:
    """Regression test: the old code keyed off `pci-security-approved.txt` OR `src/`."""
    _write(tmp_path / PCI_PROFILE_RELPATH, PCI_PROFILE_CONTENT)
    _write(tmp_path / '.human-approvals' / 'pci-security-approved.txt', 'APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION\n')
    _write(tmp_path / 'src' / 'main' / 'java' / 'Dummy.java', 'class Dummy {}\n')
    result = _run(PCI_SCRIPT, cwd=tmp_path)
    assert result.returncode == 0, f'expected advisory exit 0, got {result.returncode}: {result.stdout} {result.stderr}'
    assert 'advisory mode' in result.stdout.lower()


def test_pci_check_skipped_when_profile_not_tier1(tmp_path: Path) -> None:
    # No security-profile.yml at all → script exits 0 with "skipped" message.
    result = _run(PCI_SCRIPT, cwd=tmp_path)
    assert result.returncode == 0
    assert 'skipped' in result.stdout.lower()


# ---------------------------------------------------------------------------
# Strict mode — production-approval marker present
# ---------------------------------------------------------------------------

def test_ops_check_strict_fails_on_missing_required_files(tmp_path: Path) -> None:
    _write(tmp_path / PROD_MARKER_RELPATH, PROD_MARKER_CONTENT)
    # Strict mode active; no required docs present; expect failure.
    result = _run(OPS_SCRIPT, cwd=tmp_path)
    assert result.returncode == 1, f'expected strict exit 1, got {result.returncode}: {result.stdout} {result.stderr}'
    assert 'failed' in result.stdout.lower()
    assert 'missing required file' in result.stdout.lower()


def test_pci_check_strict_fails_on_missing_required_files(tmp_path: Path) -> None:
    _write(tmp_path / PCI_PROFILE_RELPATH, PCI_PROFILE_CONTENT)
    _write(tmp_path / PROD_MARKER_RELPATH, PROD_MARKER_CONTENT)
    result = _run(PCI_SCRIPT, cwd=tmp_path)
    assert result.returncode == 1, f'expected strict exit 1, got {result.returncode}: {result.stdout} {result.stderr}'
    assert 'failed' in result.stdout.lower()
    assert 'missing required pci file' in result.stdout.lower()

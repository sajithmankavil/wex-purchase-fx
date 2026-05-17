#!/usr/bin/env python3
"""
Post-edit note.

This hook does not mutate files. It prints a reminder to run local checks.
"""
print("Enterprise reminder: update tests/docs when behavior changes, then run `make ci` or the stack-specific equivalent.")

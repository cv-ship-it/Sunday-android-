"""Placeholder runner. Replace AGNI_RUNNER with your real Agni pipeline (PicoGK + FEM) when Phase 1 works on your laptop.

Contract: the task text arrives as the last argument; print a short result to stdout; exit 0 on success.
Files you want to keep go into the folder in the AGNI_OUT_DIR environment variable.
"""
import os
import sys
import time

task = sys.argv[-1]
time.sleep(2)
out = os.environ.get("AGNI_OUT_DIR", ".")
with open(os.path.join(out, "task.txt"), "w") as f:
    f.write(task)
print(f"[stub] Agni pipeline abhi connected nahi hai. Task mila: {task[:200]}")

"""Agni job server: the always-on part of Agni that lives on your VPS.

Sunday (the phone app) and, later, a Telegram bot send tasks here. Jobs are queued in SQLite and a single worker
thread runs them one at a time through AGNI_RUNNER, so a crash or restart never loses the queue.

AGNI_RUNNER is any command that takes the task as its last argument and prints its result to stdout, for example
a Python script that does: LLM constraint extraction -> PicoGK geometry -> CalculiX/FreeCAD check.
If AGNI_RUNNER points to a machine you reach over SSH (your PC or a rented heavy server), heavy jobs run there.
"""
import hmac
import json
import os
import shlex
import sqlite3
import subprocess
import threading
import time
import uuid

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

TOKEN = os.environ.get("AGNI_TOKEN", "")
DB_PATH = os.environ.get("AGNI_DB", "agni.db")
RUNNER = os.environ.get("AGNI_RUNNER", "python3 runner_stub.py")
TIMEOUT = int(os.environ.get("AGNI_JOB_TIMEOUT", "1800"))
OUT_DIR = os.environ.get("AGNI_OUT", "outputs")

if len(TOKEN) < 16:
    raise SystemExit("Set AGNI_TOKEN to a long random secret (16+ characters). Example: openssl rand -hex 24")

os.makedirs(OUT_DIR, exist_ok=True)
app = FastAPI(title="Agni job server")
_lock = threading.Lock()


def db():
    c = sqlite3.connect(DB_PATH, timeout=30)
    c.row_factory = sqlite3.Row
    return c


with db() as c:
    c.execute(
        """CREATE TABLE IF NOT EXISTS jobs (
            id TEXT PRIMARY KEY, task TEXT NOT NULL, status TEXT NOT NULL,
            result TEXT, error TEXT, created REAL NOT NULL, started REAL, finished REAL)"""
    )
    # A restart means any job that was running died with it: put it back in the queue.
    c.execute("UPDATE jobs SET status='queued' WHERE status='running'")


def auth(authorization: str = Header(default="")):
    given = authorization.removeprefix("Bearer ").strip()
    if not hmac.compare_digest(given.encode(), TOKEN.encode()):
        raise HTTPException(status_code=401, detail="bad token")


class NewJob(BaseModel):
    task: str = Field(min_length=3, max_length=4000)


def row_to_dict(r):
    d = dict(r)
    for k in ("created", "started", "finished"):
        if d.get(k):
            d[k] = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(d[k]))
    return d


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/jobs", dependencies=[Depends(auth)])
def submit(job: NewJob):
    jid = uuid.uuid4().hex[:8]
    with db() as c:
        c.execute(
            "INSERT INTO jobs (id, task, status, created) VALUES (?,?,?,?)",
            (jid, job.task.strip(), "queued", time.time()),
        )
    return {"job_id": jid, "status": "queued"}


@app.get("/jobs", dependencies=[Depends(auth)])
def list_jobs(limit: int = 5):
    with db() as c:
        rows = c.execute(
            "SELECT id, task, status, created, finished FROM jobs ORDER BY created DESC LIMIT ?",
            (max(1, min(limit, 50)),),
        ).fetchall()
    return [row_to_dict(r) for r in rows]


@app.get("/jobs/{jid}", dependencies=[Depends(auth)])
def get_job(jid: str):
    with db() as c:
        r = c.execute("SELECT * FROM jobs WHERE id=?", (jid,)).fetchone()
    if not r:
        raise HTTPException(status_code=404, detail="no such job")
    return row_to_dict(r)


def worker():
    while True:
        with _lock:
            with db() as c:
                r = c.execute(
                    "SELECT id, task FROM jobs WHERE status='queued' ORDER BY created LIMIT 1"
                ).fetchone()
                if r:
                    c.execute(
                        "UPDATE jobs SET status='running', started=? WHERE id=?", (time.time(), r["id"])
                    )
        if not r:
            time.sleep(2)
            continue
        status, result, error = "done", None, None
        try:
            env = dict(os.environ, AGNI_JOB_ID=r["id"], AGNI_OUT_DIR=os.path.join(OUT_DIR, r["id"]))
            os.makedirs(env["AGNI_OUT_DIR"], exist_ok=True)
            p = subprocess.run(
                shlex.split(RUNNER) + [r["task"]],
                capture_output=True, text=True, timeout=TIMEOUT, env=env,
            )
            if p.returncode == 0:
                result = p.stdout.strip()[-3000:] or "(no output)"
            else:
                status, error = "failed", (p.stderr.strip() or p.stdout.strip())[-1500:]
        except subprocess.TimeoutExpired:
            status, error = "failed", f"timed out after {TIMEOUT}s"
        except Exception as e:  # keep the worker alive whatever happens
            status, error = "failed", str(e)
        with db() as c:
            c.execute(
                "UPDATE jobs SET status=?, result=?, error=?, finished=? WHERE id=?",
                (status, result, error, time.time(), r["id"]),
            )


threading.Thread(target=worker, daemon=True).start()

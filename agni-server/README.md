# Agni job server (VPS side)

Sunday (phone) -> HTTPS -> this server -> queue -> `AGNI_RUNNER` -> result back to Sunday.

## VPS par chalana (Hostinger KVM 2, Ubuntu)

```bash
# 1. Docker install (ek baar)
curl -fsSL https://get.docker.com | sh

# 2. Ye folder VPS par copy karo (scp / git), phir:
cd agni-server
cp .env.example .env
nano .env                 # AGNI_TOKEN daalo (openssl rand -hex 24)
nano Caddyfile            # apna domain likho (DNS A record VPS IP par)

# 3. Start
docker compose up -d --build

# 4. Check
curl https://agni.tumhara-domain.com/health
```

Firewall: sirf 22, 80, 443 kholo (`ufw allow 22,80,443/tcp && ufw enable`), aur SSH ke liye key login lagao.

## Sunday mein jodna

Sunday > Settings > **Agni server URL** = `https://agni.tumhara-domain.com`, **Agni token** = `.env` wala AGNI_TOKEN.
Phir bolo: "Agni, ek mounting bracket design karo, 5 kg load, aluminium, 80 mm span." Baad mein: "Agni ka kaam kahan tak pahuncha?"

## Asli Agni pipeline jodna

`runner_stub.py` sirf placeholder hai (task wapas bol deta hai). Jab laptop par PicoGK + FEM chal jaye:
- apna script banao jo task ko last argument leke result stdout par print kare, files `$AGNI_OUT_DIR` mein rakhe,
- `.env` mein `AGNI_RUNNER=python3 mera_agni.py` karo.
- Heavy jobs (voxel/FEM) 8GB VPS par na chalein to runner ssh se tumhare PC ya kisi bade server par kaam bhej sakta hai.

## Endpoints (sab par `Authorization: Bearer <token>`)

- `POST /jobs` body `{"task": "..."}` -> `{"job_id": "..."}`
- `GET /jobs/{id}` -> status (`queued/running/done/failed`), result, error
- `GET /jobs?limit=5` -> latest jobs
- `GET /health` -> bina token

Jobs ek ke baad ek chalte hain, queue SQLite mein save rehti hai (restart par kuch nahi khota), aur har job ka timeout hai (default 30 min).

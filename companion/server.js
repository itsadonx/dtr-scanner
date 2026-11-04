const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const os = require("os");

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";
const ADMIN_USER = process.env.ADMIN_USER || "admin";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "admin123";
const DEVICE_KEY = process.env.DEVICE_KEY || "dtr-sync-key";
const DATA_DIR = path.join(__dirname, "data");
const STORE_PATH = path.join(DATA_DIR, "store.json");
const PUBLIC_DIR = path.join(__dirname, "public");
const PBKDF2_ITERATIONS = 12000;
const PBKDF2_KEY_BYTES = 32;

const sessions = new Map();

function emptyStore() {
  return { guards: [], scans: [] };
}

function loadStore() {
  try {
    return JSON.parse(fs.readFileSync(STORE_PATH, "utf8"));
  } catch {
    return emptyStore();
  }
}

function saveStore(store) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  const tmp = `${STORE_PATH}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(store, null, 2));
  fs.renameSync(tmp, STORE_PATH);
}

function hashPassword(password, salt = crypto.randomBytes(16)) {
  const hash = crypto.pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BYTES, "sha256");
  return { passwordSalt: salt.toString("hex"), passwordHash: hash.toString("hex") };
}

function safeEqual(a, b) {
  const left = crypto.createHash("sha256").update(String(a)).digest();
  const right = crypto.createHash("sha256").update(String(b)).digest();
  return crypto.timingSafeEqual(left, right);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > 2_000_000) {
        reject(new Error("Request is too large."));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error("Invalid JSON."));
      }
    });
    req.on("error", reject);
  });
}

function send(res, status, body, extraHeaders = {}) {
  const raw = Buffer.isBuffer(body) || typeof body === "string";
  const payload = raw ? body : JSON.stringify(body);
  const headers = {
    "Content-Type": raw ? "text/html; charset=utf-8" : "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    ...extraHeaders,
  };
  res.writeHead(status, headers);
  res.end(payload);
}

function sendJson(res, status, body, extraHeaders) {
  send(res, status, body, extraHeaders);
}

function cookieToken(req) {
  const header = req.headers.cookie || "";
  const match = header.match(/(?:^|;\s*)dtr_session=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : "";
}

function isAdmin(req) {
  const token = cookieToken(req);
  const session = sessions.get(token);
  if (!session) return false;
  if (session.expiresAt < Date.now()) {
    sessions.delete(token);
    return false;
  }
  return true;
}

function deviceAuthorized(req) {
  return safeEqual(req.headers["x-device-key"] || "", DEVICE_KEY);
}

function publicGuard(guard) {
  return {
    id: guard.id,
    guardId: guard.guardId,
    displayName: guard.displayName,
    active: guard.active,
    updatedAtEpochMs: guard.updatedAtEpochMs,
  };
}

function syncGuard(guard) {
  return {
    id: guard.id,
    guardId: guard.guardId,
    displayName: guard.displayName,
    passwordSalt: guard.passwordSalt,
    passwordHash: guard.passwordHash,
    active: guard.active,
    updatedAtEpochMs: guard.updatedAtEpochMs,
  };
}

function lanAddresses() {
  const addresses = [];
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries || []) {
      if (entry.family === "IPv4" && !entry.internal) addresses.push(entry.address);
    }
  }
  return addresses;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const route = `${req.method} ${url.pathname}`;

  try {
    if (route === "GET /" || route === "GET /index.html") {
      const file = fs.readFileSync(path.join(PUBLIC_DIR, "index.html"));
      send(res, 200, file, { "Content-Type": "text/html; charset=utf-8" });
      return;
    }

    if (route === "GET /api/health") {
      sendJson(res, 200, { ok: true, service: "secureattend-companion" });
      return;
    }

    if (route === "POST /api/admin/login") {
      const body = await readBody(req);
      if (!safeEqual(body.username || "", ADMIN_USER) || !safeEqual(body.password || "", ADMIN_PASSWORD)) {
        sendJson(res, 401, { error: "Wrong admin username or password." });
        return;
      }
      const token = crypto.randomBytes(24).toString("hex");
      sessions.set(token, { expiresAt: Date.now() + 12 * 60 * 60 * 1000 });
      sendJson(res, 200, { ok: true }, {
        "Set-Cookie": `dtr_session=${encodeURIComponent(token)}; HttpOnly; Path=/; SameSite=Lax; Max-Age=43200`,
      });
      return;
    }

    if (route === "POST /api/admin/logout") {
      sessions.delete(cookieToken(req));
      sendJson(res, 200, { ok: true }, {
        "Set-Cookie": "dtr_session=; HttpOnly; Path=/; SameSite=Lax; Max-Age=0",
      });
      return;
    }

    if (route === "GET /api/admin/session") {
      sendJson(res, 200, { signedIn: isAdmin(req) });
      return;
    }

    if (url.pathname.startsWith("/api/admin/") || url.pathname === "/api/guards" || url.pathname.startsWith("/api/guards/") || url.pathname === "/api/scans") {
      if (!isAdmin(req)) {
        sendJson(res, 401, { error: "Sign in required." });
        return;
      }
    }

    if (route === "GET /api/admin/config") {
      sendJson(res, 200, { deviceKey: DEVICE_KEY, port: PORT });
      return;
    }

    if (route === "GET /api/guards") {
      const store = loadStore();
      sendJson(res, 200, { guards: store.guards.map(publicGuard) });
      return;
    }

    if (route === "POST /api/guards") {
      const body = await readBody(req);
      const guardId = String(body.guardId || "").trim();
      const displayName = String(body.displayName || "").trim();
      const password = String(body.password || "");
      if (guardId.length < 2 || displayName.length < 1 || password.length < 4) {
        sendJson(res, 400, { error: "Guard ID, name, and a password of at least 4 characters are required." });
        return;
      }
      const store = loadStore();
      if (store.guards.some((guard) => guard.guardId.toLowerCase() === guardId.toLowerCase())) {
        sendJson(res, 409, { error: "That Guard ID already exists." });
        return;
      }
      const now = Date.now();
      store.guards.push({
        id: crypto.randomUUID(),
        guardId,
        displayName,
        ...hashPassword(password),
        active: true,
        updatedAtEpochMs: now,
      });
      saveStore(store);
      sendJson(res, 201, { ok: true });
      return;
    }

    const guardMatch = url.pathname.match(/^\/api\/guards\/([^/]+)$/);
    if (guardMatch && req.method === "PATCH") {
      const body = await readBody(req);
      const store = loadStore();
      const guard = store.guards.find((item) => item.id === decodeURIComponent(guardMatch[1]));
      if (!guard) {
        sendJson(res, 404, { error: "Guard not found." });
        return;
      }
      if (typeof body.displayName === "string" && body.displayName.trim()) {
        guard.displayName = body.displayName.trim();
      }
      if (typeof body.active === "boolean") guard.active = body.active;
      if (typeof body.password === "string" && body.password.length > 0) {
        if (body.password.length < 4) {
          sendJson(res, 400, { error: "Password must be at least 4 characters." });
          return;
        }
        Object.assign(guard, hashPassword(body.password));
      }
      guard.updatedAtEpochMs = Date.now();
      saveStore(store);
      sendJson(res, 200, { ok: true });
      return;
    }

    if (guardMatch && req.method === "DELETE") {
      const store = loadStore();
      const id = decodeURIComponent(guardMatch[1]);
      store.guards = store.guards.filter((guard) => guard.id !== id);
      saveStore(store);
      sendJson(res, 200, { ok: true });
      return;
    }

    if (route === "GET /api/scans") {
      const store = loadStore();
      const scans = [...store.scans].sort((a, b) => b.scannedAtEpochMs - a.scannedAtEpochMs);
      sendJson(res, 200, { scans });
      return;
    }

    if (route === "GET /api/sync/guards") {
      if (!deviceAuthorized(req)) {
        sendJson(res, 401, { error: "Wrong device key." });
        return;
      }
      const store = loadStore();
      sendJson(res, 200, { guards: store.guards.map(syncGuard) });
      return;
    }

    if (route === "POST /api/sync/scans") {
      if (!deviceAuthorized(req)) {
        sendJson(res, 401, { error: "Wrong device key." });
        return;
      }
      const body = await readBody(req);
      const incoming = Array.isArray(body.scans) ? body.scans : [];
      const store = loadStore();
      const saved = [];
      for (const item of incoming) {
        const clientId = String(item.clientId || "").trim();
        const content = String(item.content || "").trim();
        if (!clientId || !content) continue;
        const record = {
          clientId,
          content,
          scannedAtEpochMs: Number(item.scannedAtEpochMs) || Date.now(),
          scanType: String(item.scanType || "QR"),
          attendanceDirection: String(item.attendanceDirection || "IN"),
          recordedBy: String(item.recordedBy || ""),
          receivedAtEpochMs: Date.now(),
        };
        const existing = store.scans.findIndex((scan) => scan.clientId === clientId);
        if (existing >= 0) store.scans[existing] = record;
        else store.scans.push(record);
        saved.push(clientId);
      }
      saveStore(store);
      sendJson(res, 200, { savedClientIds: saved });
      return;
    }

    sendJson(res, 404, { error: "Not found." });
  } catch (error) {
    sendJson(res, 400, { error: error.message || "Request failed." });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`SecureAttend companion listening on http://localhost:${PORT}`);
  for (const address of lanAddresses()) {
    console.log(`Phone on the same Wi-Fi: http://${address}:${PORT}`);
  }
  console.log(`Admin login: ${ADMIN_USER} / ${ADMIN_PASSWORD}`);
  console.log(`Device key for the app: ${DEVICE_KEY}`);
});

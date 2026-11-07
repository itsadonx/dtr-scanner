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
  return { guards: [], scans: [], employees: [] };
}

const POSITIONS = ["Farmtech", "Farm Aid", "Other"];

function personFromContent(content) {
  const raw = String(content || "").replace(/^Face check-in:\s*/i, "").trim();
  if (!raw) return null;
  const parts = raw.split(",").map((part) => part.trim()).filter(Boolean);
  if (parts.length >= 4) {
    const employeeCode = parts[0];
    const lastName = parts[1];
    const firstName = parts[2];
    const middleName = parts.slice(3).join(" ");
    return {
      employeeCode,
      lastName,
      firstName,
      middleName,
      qrPayload: [employeeCode, lastName, firstName, middleName].join(","),
      matchKey: `id:${employeeCode.toLowerCase()}`,
    };
  }
  return {
    employeeCode: "",
    lastName: raw,
    firstName: "",
    middleName: "",
    qrPayload: raw,
    matchKey: `raw:${raw.toLowerCase()}`,
  };
}

function employeeMatchKey(employee) {
  if (employee.employeeCode) return `id:${String(employee.employeeCode).toLowerCase()}`;
  return `raw:${String(employee.qrPayload || "").toLowerCase()}`;
}

function makeEmployee(fields) {
  const now = Date.now();
  return {
    id: crypto.randomUUID(),
    employeeCode: fields.employeeCode || "",
    lastName: fields.lastName || "",
    firstName: fields.firstName || "",
    middleName: fields.middleName || "",
    house: fields.house || "",
    areaAssigned: fields.areaAssigned || "",
    position: fields.position || "",
    positionNote: fields.positionNote || "",
    dtrNote: fields.dtrNote || "",
    qrPayload: fields.qrPayload,
    source: fields.source || "manual",
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
  };
}

function ensureEmployeesFromScans(store) {
  if (!Array.isArray(store.employees)) store.employees = [];
  let changed = false;
  for (const scan of store.scans || []) {
    const person = personFromContent(scan.content);
    if (!person) continue;
    const exists = store.employees.some((employee) => employeeMatchKey(employee) === person.matchKey);
    if (exists) continue;
    store.employees.push(makeEmployee({ ...person, source: "scan" }));
    changed = true;
  }
  return changed;
}

function readPosition(body) {
  const position = String(body.position || "").trim();
  if (!position) return { position: "", positionNote: "" };
  if (!POSITIONS.includes(position)) {
    throw new Error("Position must be Farmtech, Farm Aid, or Other.");
  }
  const positionNote = position === "Other" ? String(body.positionNote || "").trim() : "";
  if (position === "Other" && !positionNote) {
    throw new Error("Type the position when you choose Other.");
  }
  return { position, positionNote };
}

function publicEmployee(employee) {
  return {
    id: employee.id,
    employeeCode: employee.employeeCode,
    lastName: employee.lastName,
    firstName: employee.firstName,
    middleName: employee.middleName,
    house: employee.house,
    areaAssigned: employee.areaAssigned,
    position: employee.position,
    positionNote: employee.positionNote,
    dtrNote: employee.dtrNote || "",
    qrPayload: employee.qrPayload,
    source: employee.source,
    updatedAtEpochMs: employee.updatedAtEpochMs,
  };
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

function manilaStamp(epochMs) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Manila",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(epochMs));
  const value = (type) => (parts.find((part) => part.type === type) || {}).value || "";
  const hour = value("hour") === "24" ? "00" : value("hour");
  if (!value("year") || !hour) return null;
  return {
    date: `${value("year")}-${value("month")}-${value("day")}`,
    time: `${hour}:${value("minute")}:${value("second")}`,
  };
}

function attendanceRows(store, from, to) {
  const grouped = new Map();
  for (const scan of store.scans || []) {
    const person = personFromContent(scan.content);
    const employeeCode = String((person && person.employeeCode) || "").trim();
    if (!employeeCode) continue;
    const stamp = manilaStamp(Number(scan.scannedAtEpochMs) || 0);
    if (!stamp || stamp.date < from || stamp.date > to) continue;
    const key = `${employeeCode.toLowerCase()}|${stamp.date}`;
    let row = grouped.get(key);
    if (!row) {
      row = { employeeCode, date: stamp.date, timeIn: "", timeOut: "" };
      grouped.set(key, row);
    }
    const isOut = String(scan.attendanceDirection || "IN").toUpperCase() === "OUT";
    if (!isOut && !row.timeIn) row.timeIn = stamp.time;
    if (isOut) row.timeOut = stamp.time;
  }
  return [...grouped.values()].sort((a, b) =>
    a.date.localeCompare(b.date) || a.employeeCode.localeCompare(b.employeeCode),
  );
}

function exportRange(from, to) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) {
    throw new Error("Choose a from date and a to date.");
  }
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    throw new Error("The end date must be on or after the start date.");
  }
  if ((end - start) / 86400000 > 62) {
    throw new Error("Import up to 62 days at a time.");
  }
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
  const origin = req.headers.origin || "";
  if (origin) {
    const cors = {
      "Access-Control-Allow-Origin": origin,
      "Vary": "Origin",
      "Access-Control-Allow-Headers": "Content-Type, X-Device-Key",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Private-Network": "true",
    };
    const writeHead = res.writeHead.bind(res);
    res.writeHead = (status, headers) => writeHead(status, { ...cors, ...(headers || {}) });
  }
  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  try {
    if (route === "GET /" || route === "GET /index.html") {
      const file = fs.readFileSync(path.join(PUBLIC_DIR, "index.html"));
      send(res, 200, file, { "Content-Type": "text/html; charset=utf-8" });
      return;
    }

    if (route === "GET /qrcode.min.js") {
      const file = fs.readFileSync(path.join(PUBLIC_DIR, "qrcode.min.js"));
      send(res, 200, file, { "Content-Type": "application/javascript; charset=utf-8" });
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

    if (
      url.pathname.startsWith("/api/admin/") ||
      url.pathname === "/api/guards" ||
      url.pathname.startsWith("/api/guards/") ||
      url.pathname === "/api/scans" ||
      url.pathname === "/api/employees" ||
      url.pathname.startsWith("/api/employees/")
    ) {
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

    if (route === "POST /api/export/attendance") {
      if (!deviceAuthorized(req)) {
        sendJson(res, 401, { error: "Wrong device key." });
        return;
      }
      const body = await readBody(req);
      const from = String(body.from || "").trim();
      const to = String(body.to || "").trim();
      exportRange(from, to);
      const records = attendanceRows(loadStore(), from, to);
      sendJson(res, 200, { ok: true, from, to, records });
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
      ensureEmployeesFromScans(store);
      saveStore(store);
      sendJson(res, 200, { savedClientIds: saved });
      return;
    }

    if (route === "GET /api/employees") {
      const store = loadStore();
      if (ensureEmployeesFromScans(store)) saveStore(store);
      const employees = [...store.employees].sort((a, b) =>
        `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`),
      );
      sendJson(res, 200, { employees: employees.map(publicEmployee) });
      return;
    }

    if (route === "POST /api/employees") {
      const body = await readBody(req);
      const employeeCode = String(body.employeeCode || "").trim();
      const lastName = String(body.lastName || "").trim();
      const firstName = String(body.firstName || "").trim();
      const middleName = String(body.middleName || "").trim();
      if (!employeeCode || !lastName || !firstName || !middleName) {
        sendJson(res, 400, { error: "ID, last name, first name, and middle name are required." });
        return;
      }
      const placement = readPosition(body);
      const store = loadStore();
      ensureEmployeesFromScans(store);
      if (store.employees.some((employee) => employee.employeeCode.toLowerCase() === employeeCode.toLowerCase())) {
        sendJson(res, 409, { error: "That employee ID is already in the list. Edit that row instead." });
        return;
      }
      const employee = makeEmployee({
        employeeCode,
        lastName,
        firstName,
        middleName,
        house: String(body.house || "").trim(),
        areaAssigned: String(body.areaAssigned || "").trim(),
        ...placement,
        dtrNote: String(body.dtrNote || "").trim(),
        qrPayload: [employeeCode, lastName, firstName, middleName].join(","),
        source: "manual",
      });
      store.employees.push(employee);
      saveStore(store);
      sendJson(res, 201, { employee: publicEmployee(employee) });
      return;
    }

    const employeeMatch = url.pathname.match(/^\/api\/employees\/([^/]+)$/);
    if (employeeMatch && req.method === "PATCH") {
      const body = await readBody(req);
      const store = loadStore();
      const employee = store.employees.find((item) => item.id === decodeURIComponent(employeeMatch[1]));
      if (!employee) {
        sendJson(res, 404, { error: "Employee not found." });
        return;
      }
      const placement = readPosition(body);
      employee.house = String(body.house || "").trim();
      employee.areaAssigned = String(body.areaAssigned || "").trim();
      employee.position = placement.position;
      employee.positionNote = placement.positionNote;
      employee.dtrNote = String(body.dtrNote || "").trim();
      if (employee.source !== "scan") {
        const employeeCode = String(body.employeeCode || employee.employeeCode).trim();
        const lastName = String(body.lastName || employee.lastName).trim();
        const firstName = String(body.firstName || employee.firstName).trim();
        const middleName = String(body.middleName || "").trim();
        if (!employeeCode || !lastName || !firstName) {
          sendJson(res, 400, { error: "ID, last name, and first name are required." });
          return;
        }
        const duplicate = store.employees.some((item) =>
          item.id !== employee.id && item.employeeCode.toLowerCase() === employeeCode.toLowerCase(),
        );
        if (duplicate) {
          sendJson(res, 409, { error: "That employee ID is already in the list." });
          return;
        }
        employee.employeeCode = employeeCode;
        employee.lastName = lastName;
        employee.firstName = firstName;
        employee.middleName = middleName;
        employee.qrPayload = [employeeCode, lastName, firstName, middleName].join(",");
      }
      employee.updatedAtEpochMs = Date.now();
      saveStore(store);
      sendJson(res, 200, { employee: publicEmployee(employee) });
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
  console.log(`SHRIS upload: ${shrisBaseUrl()}`);
});

function shrisBaseUrl() {
  try {
    const saved = JSON.parse(fs.readFileSync(path.join(DATA_DIR, "shris.json"), "utf8"));
    if (saved && saved.url) return String(saved.url).replace(/\/$/, "");
  } catch {
    // The hosted site is the default when no local address is saved.
  }
  return String(process.env.SHRIS_URL || "https://scifi.is-great.net").replace(/\/$/, "");
}

let shrisCookie = "";
let shrisReady = false;

function infinityFreeCookie(html) {
  if (!String(html).includes("slowAES.decrypt")) return "";
  const pick = (name) => {
    const match = String(html).match(new RegExp(name + '=toNumbers\\("([0-9a-f]+)"\\)'));
    return match ? match[1] : "";
  };
  const key = Buffer.from(pick("a"), "hex");
  const iv = Buffer.from(pick("b"), "hex");
  const cipher = Buffer.from(pick("c"), "hex");
  if (key.length !== 16 || iv.length !== 16 || cipher.length === 0) return "";
  const decipher = crypto.createDecipheriv("aes-128-cbc", key, iv);
  decipher.setAutoPadding(false);
  return `__test=${Buffer.concat([decipher.update(cipher), decipher.final()]).toString("hex")}`;
}

async function shrisFetch(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (shrisCookie) headers.Cookie = shrisCookie;
  let response = await fetch(url, { ...options, headers });
  let text = await response.text();
  const cookie = infinityFreeCookie(text);
  if (cookie) {
    shrisCookie = cookie;
    headers.Cookie = shrisCookie;
    const next = text.match(/location\.href="([^"]+)"/);
    const retryUrl = next ? next[1].replace(/&amp;/g, "&") : url;
    response = await fetch(retryUrl, { ...options, headers });
    text = await response.text();
  }
  try {
    return { ok: response.ok, data: text ? JSON.parse(text) : {} };
  } catch {
    return { ok: false, data: { error: text.slice(0, 160).replace(/\s+/g, " ") } };
  }
}

async function pushAttendanceToShris() {
  const base = shrisBaseUrl();
  let listed;
  try {
    listed = await shrisFetch(`${base}/attendance/import_poll.php`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Device-Key": DEVICE_KEY },
      body: JSON.stringify({ deviceKey: DEVICE_KEY }),
    });
  } catch (error) {
    console.log(`SHRIS check failed: ${error.message}`);
    return;
  }
  if (!listed.ok || listed.data.error) {
    console.log(`SHRIS check failed: ${listed.data.error || "No answer"}`);
    return;
  }
  if (!shrisReady) {
    shrisReady = true;
    console.log(`Connected to SHRIS at ${base}`);
  }
  if (!listed.data.job || !listed.data.job.token) return;
  const records = attendanceRows(loadStore(), listed.data.job.from, listed.data.job.to);
  try {
    const saved = await shrisFetch(`${base}/attendance/import_receive.php`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: listed.data.job.token, records }),
    });
    if (!saved.ok || !saved.data.ok) {
      console.log(`SHRIS upload failed: ${saved.data.error || "Not saved"}`);
      return;
    }
    console.log(`Uploaded ${records.length} attendance day${records.length === 1 ? "" : "s"} to SHRIS.`);
  } catch (error) {
    console.log(`SHRIS upload failed: ${error.message}`);
  }
}

setInterval(pushAttendanceToShris, 4000);
pushAttendanceToShris();

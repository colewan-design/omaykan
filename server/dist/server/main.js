"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const node_http_1 = require("node:http");
const create_online_order_1 = __importDefault(require("../api/create-online-order"));
const platform_admin_1 = __importDefault(require("../api/platform-admin"));
const resolve_staff_store_code_1 = __importDefault(require("../api/resolve-staff-store-code"));
const resolve_store_code_1 = __importDefault(require("../api/resolve-store-code"));
const signup_1 = __importDefault(require("../api/signup"));
const staff_create_1 = __importDefault(require("../api/staff-create"));
const ROUTES = {
    '/api/create-online-order': create_online_order_1.default,
    '/api/platform-admin': platform_admin_1.default,
    '/api/resolve-staff-store-code': resolve_staff_store_code_1.default,
    '/api/resolve-store-code': resolve_store_code_1.default,
    '/api/signup': signup_1.default,
    '/api/staff-create': staff_create_1.default,
};
const PORT = Number(process.env.PORT) || 3005;
const HOST = process.env.HOST || '127.0.0.1';
/** Refuse bodies larger than this rather than buffering without limit. */
const MAX_BODY_BYTES = 1_000_000;
function readBody(req) {
    return new Promise((resolve, reject) => {
        let size = 0;
        const chunks = [];
        req.on('data', (chunk) => {
            size += chunk.length;
            if (size > MAX_BODY_BYTES) {
                reject(new Error('payload-too-large'));
                req.destroy();
                return;
            }
            chunks.push(chunk);
        });
        req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
        req.on('error', reject);
    });
}
/** Adds the `status()`/`json()` sugar the Vercel handlers call. */
function decorate(res) {
    const r = res;
    r.status = (code) => {
        r.statusCode = code;
        return r;
    };
    r.json = (body) => {
        if (!r.headersSent)
            r.setHeader('Content-Type', 'application/json; charset=utf-8');
        r.end(JSON.stringify(body));
    };
    return r;
}
const server = (0, node_http_1.createServer)(async (req, res) => {
    const decorated = decorate(res);
    const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
    // Unauthenticated liveness probe for systemd/nginx checks — deliberately
    // does not touch Firestore, so it stays green even if credentials are wrong.
    if (url.pathname === '/api/health') {
        decorated.status(200).json({ ok: true, routes: Object.keys(ROUTES).length });
        return;
    }
    const handler = ROUTES[url.pathname];
    if (!handler) {
        decorated.status(404).json({ error: 'Not found.' });
        return;
    }
    try {
        let body = {};
        if (req.method !== 'GET' && req.method !== 'HEAD' && req.method !== 'OPTIONS') {
            const raw = await readBody(req);
            if (raw) {
                try {
                    body = JSON.parse(raw);
                }
                catch {
                    decorated.status(400).json({ error: 'Request body must be valid JSON.' });
                    return;
                }
            }
        }
        const vercelReq = Object.assign(req, {
            body,
            query: Object.fromEntries(url.searchParams),
            cookies: {},
        });
        await handler(vercelReq, decorated);
        if (!res.writableEnded)
            decorated.status(204).end();
    }
    catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'payload-too-large') {
            if (!res.writableEnded)
                decorated.status(413).json({ error: 'Request body too large.' });
            return;
        }
        // Never leak internals to the client; the detail goes to the journal.
        console.error(`[api] ${req.method} ${url.pathname} failed:`, err);
        if (!res.writableEnded)
            decorated.status(500).json({ error: 'Something went wrong.' });
    }
});
server.listen(PORT, HOST, () => {
    console.log(`[api] listening on http://${HOST}:${PORT} — ${Object.keys(ROUTES).length} routes`);
});
for (const signal of ['SIGTERM', 'SIGINT']) {
    process.on(signal, () => {
        console.log(`[api] ${signal} received, shutting down`);
        server.close(() => process.exit(0));
    });
}

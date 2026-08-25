"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = handler;
const admin_1 = require("./_lib/admin");
const SUPPORTED_MODES = ['coffee-shop', 'grocery', 'restaurant'];
async function resolveStoreCode(rawCode) {
    const code = typeof rawCode === 'string' ? rawCode.trim() : '';
    if (!code) {
        throw new admin_1.ApiError(400, 'A store code is required.');
    }
    const snap = await (0, admin_1.getDb)().collectionGroup('stores').where('pairingCode', '==', code).limit(1).get();
    if (snap.empty) {
        throw new admin_1.ApiError(404, "We couldn't find a store with that code.");
    }
    const storeDoc = snap.docs[0];
    const store = storeDoc.data();
    const orgRef = storeDoc.ref.parent.parent;
    if (!orgRef) {
        throw new admin_1.ApiError(500, 'Store is missing its parent organization.');
    }
    if (!store.businessMode || !SUPPORTED_MODES.includes(store.businessMode)) {
        throw new admin_1.ApiError(409, 'This store is not set up for online ordering.');
    }
    return {
        orgSlug: orgRef.id,
        storeCode: storeDoc.id,
        businessMode: store.businessMode,
        storeName: store.name,
        storeAddress: store.address ?? '',
        storeLat: typeof store.lat === 'number' ? store.lat : null,
        storeLng: typeof store.lng === 'number' ? store.lng : null,
    };
}
async function handler(req, res) {
    (0, admin_1.setCorsHeaders)(res);
    if (req.method === 'OPTIONS') {
        res.status(204).end();
        return;
    }
    if (req.method !== 'POST') {
        res.status(405).json({ error: 'Method not allowed.' });
        return;
    }
    try {
        const body = (req.body ?? {});
        const result = await resolveStoreCode(body.code);
        res.status(200).json(result);
    }
    catch (err) {
        if (err instanceof admin_1.ApiError) {
            res.status(err.status).json({ error: err.message });
            return;
        }
        console.error('resolve-store-code failed:', err);
        res.status(500).json({ error: 'Something went wrong looking up that store.' });
    }
}

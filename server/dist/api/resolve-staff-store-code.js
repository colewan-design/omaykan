"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = handler;
const admin_1 = require("./_lib/admin");
// Looks up a store by pairing code with no businessMode gating — used only
// to bind a staff browser to an existing org ("I already have a store" on
// the signup page), not for customer online-ordering eligibility. Kept
// separate from api/resolve-store-code.ts (the customer-storefront lookup,
// which rejects nail-salon stores) rather than relaxing a check that endpoint
// still needs for its own purpose.
async function resolveStaffStoreCode(rawCode) {
    const code = typeof rawCode === 'string' ? rawCode.trim().toUpperCase() : '';
    if (!code) {
        throw new admin_1.ApiError(400, 'A store code is required.');
    }
    const snap = await (0, admin_1.getDb)().collectionGroup('stores').where('pairingCode', '==', code).limit(1).get();
    if (snap.empty) {
        throw new admin_1.ApiError(404, "We couldn't find a store with that code.");
    }
    const storeDoc = snap.docs[0];
    const orgRef = storeDoc.ref.parent.parent;
    if (!orgRef) {
        throw new admin_1.ApiError(500, 'Store is missing its parent organization.');
    }
    return { organizationSlug: orgRef.id, storeCode: storeDoc.id };
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
        const result = await resolveStaffStoreCode(body.code);
        res.status(200).json(result);
    }
    catch (err) {
        if (err instanceof admin_1.ApiError) {
            res.status(err.status).json({ error: err.message });
            return;
        }
        console.error('resolve-staff-store-code failed:', err);
        res.status(500).json({ error: 'Something went wrong looking up that store.' });
    }
}

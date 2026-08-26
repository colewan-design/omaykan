"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = handler;
const firestore_1 = require("firebase-admin/firestore");
const admin_1 = require("./_lib/admin");
// Public self-serve signup: creates a brand-new organization + store + admin
// account in one shot, so a new business owner gets a fully working store
// immediately. Payment is GCash, verified manually later via
// api/platform-admin.ts — this endpoint only records the reference number.
// Placeholder — replace once a real price exists. Mirrored (display-only) by
// PLAN_PRICE_PESOS in apps/web/src/onboarding/pricingConstants.ts.
const PLAN_ID = 'standard-monthly';
const PLAN_AMOUNT_CENTS = 49900; // ₱499.00 placeholder
const VALID_BUSINESS_MODES = ['coffee-shop', 'grocery', 'restaurant', 'nail-salon'];
const PAIRING_CODE_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
function syntheticEmail(username, orgSlug) {
    return `${username.trim().toLowerCase()}@${orgSlug}.pos`;
}
function slugify(input) {
    return input.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}
async function resolveUniqueOrgSlug(db, businessName) {
    const base = slugify(businessName) || 'store';
    let candidate = base;
    for (let attempt = 1; attempt <= 50; attempt++) {
        const snap = await db.doc(`organizations/${candidate}`).get();
        if (!snap.exists)
            return candidate;
        candidate = `${base}-${attempt + 1}`;
    }
    throw new admin_1.ApiError(409, 'Could not allocate a store ID — try a slightly different business name.');
}
function generatePairingCode() {
    return Array.from({ length: 6 }, () => PAIRING_CODE_ALPHABET[Math.floor(Math.random() * PAIRING_CODE_ALPHABET.length)]).join('');
}
async function resolveUniquePairingCode(db) {
    for (let i = 0; i < 10; i++) {
        const code = generatePairingCode();
        const existing = await db.collectionGroup('stores').where('pairingCode', '==', code).limit(1).get();
        if (existing.empty)
            return code;
    }
    throw new admin_1.ApiError(500, 'Could not generate a unique store code — please try again.');
}
async function signup(body) {
    const businessName = body.businessName?.trim() ?? '';
    const ownerFullName = body.ownerFullName?.trim() ?? '';
    const username = body.username?.trim().toLowerCase() ?? '';
    const password = body.password?.trim() ?? '';
    const businessMode = body.businessMode?.trim() ?? '';
    const gcashReference = body.gcashReference?.trim() ?? '';
    // gcashReference is deliberately not required: the signup form stopped
    // collecting payment when early access was made free. It is still read and
    // stored, so reinstating the step needs no change here.
    if (!businessName || !ownerFullName || !username || !password) {
        throw new admin_1.ApiError(400, 'Please fill in every field.');
    }
    if (password.length < 6) {
        throw new admin_1.ApiError(400, 'Password must be at least 6 characters.');
    }
    if (!VALID_BUSINESS_MODES.includes(businessMode)) {
        throw new admin_1.ApiError(400, 'Please choose a valid business type.');
    }
    const db = (0, admin_1.getDb)();
    const orgSlug = await resolveUniqueOrgSlug(db, businessName);
    const pairingCode = await resolveUniquePairingCode(db);
    const email = syntheticEmail(username, orgSlug);
    let uid;
    try {
        const created = await (0, admin_1.getAdminAuth)().createUser({ email, password, displayName: ownerFullName });
        uid = created.uid;
    }
    catch (err) {
        if (err && typeof err === 'object' && 'code' in err && err.code === 'auth/email-already-exists') {
            throw new admin_1.ApiError(409, 'That username is already taken — try a different one.');
        }
        throw err;
    }
    // Four independent docs in one batch with .create() (not .set()) so a
    // same-instant race on an identical business name throws instead of
    // silently colliding. Accepted v1 risk: if this batch fails after
    // createUser() above already succeeded, the Auth user is orphaned (same
    // risk api/staff-create.ts already carries) — no automated cleanup.
    const batch = db.batch();
    batch.create(db.doc(`organizations/${orgSlug}`), {
        name: businessName,
        firstAdminClaimed: true,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
    batch.create(db.doc(`organizations/${orgSlug}/stores/main`), {
        name: businessName,
        address: '',
        businessMode,
        pairingCode,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
    batch.create(db.doc(`organizations/${orgSlug}/private/subscription`), {
        status: 'pending_verification',
        plan: PLAN_ID,
        amountCents: PLAN_AMOUNT_CENTS,
        gcashReference,
        submittedAt: firestore_1.FieldValue.serverTimestamp(),
        verifiedAt: null,
    });
    batch.create(db.doc(`users/${uid}`), {
        organizationId: orgSlug,
        fullName: ownerFullName,
        username,
        status: 'active',
        roleKey: 'admin',
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
    await batch.commit();
    return { organizationSlug: orgSlug, storeCode: 'main', pairingCode };
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
        const result = await signup((req.body ?? {}));
        res.status(200).json(result);
    }
    catch (err) {
        if (err instanceof admin_1.ApiError) {
            res.status(err.status).json({ error: err.message });
            return;
        }
        console.error('signup failed:', err);
        res.status(500).json({ error: 'Something went wrong creating your store.' });
    }
}

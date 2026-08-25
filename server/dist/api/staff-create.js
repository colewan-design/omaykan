"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = handler;
const firestore_1 = require("firebase-admin/firestore");
const admin_1 = require("./_lib/admin");
function syntheticEmail(username, orgSlug) {
    return `${username.trim().toLowerCase()}@${orgSlug}.pos`;
}
async function requireCallingAdmin(req) {
    const authHeader = req.headers.authorization;
    const token = typeof authHeader === 'string' ? authHeader.replace(/^Bearer\s+/i, '') : '';
    if (!token) {
        throw new admin_1.ApiError(401, 'Missing Authorization header.');
    }
    let uid;
    try {
        uid = (await (0, admin_1.getAdminAuth)().verifyIdToken(token)).uid;
    }
    catch {
        throw new admin_1.ApiError(401, 'Your session has expired — please sign in again.');
    }
    const callerSnap = await (0, admin_1.getDb)().doc(`users/${uid}`).get();
    if (!callerSnap.exists) {
        throw new admin_1.ApiError(403, 'Only an admin can add employees.');
    }
    const caller = callerSnap.data();
    if (caller.roleKey !== 'admin') {
        throw new admin_1.ApiError(403, 'Only an admin can add employees.');
    }
    return { organizationId: caller.organizationId };
}
async function createStaffAccount(organizationId, body) {
    const fullName = body.fullName?.trim() ?? '';
    const username = body.username?.trim().toLowerCase() ?? '';
    const password = body.password?.trim() ?? '';
    const roleId = body.roleId?.trim() ?? '';
    if (!fullName || !username || !password || !roleId) {
        throw new admin_1.ApiError(400, 'Full name, username, password, and role are required.');
    }
    if (password.length < 6) {
        throw new admin_1.ApiError(400, 'Password must be at least 6 characters.');
    }
    const db = (0, admin_1.getDb)();
    const existing = await db
        .collection('users')
        .where('organizationId', '==', organizationId)
        .where('username', '==', username)
        .limit(1)
        .get();
    if (!existing.empty) {
        throw new admin_1.ApiError(409, 'That username is already in use.');
    }
    const email = syntheticEmail(username, organizationId);
    let uid;
    try {
        const created = await (0, admin_1.getAdminAuth)().createUser({ email, password, displayName: fullName });
        uid = created.uid;
    }
    catch (err) {
        if (err && typeof err === 'object' && 'code' in err && err.code === 'auth/email-already-exists') {
            throw new admin_1.ApiError(409, 'That username is already in use.');
        }
        throw err;
    }
    const createdAt = new Date();
    await db.doc(`users/${uid}`).set({
        organizationId,
        fullName,
        username,
        status: 'active',
        roleKey: roleId,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
    return {
        id: uid,
        fullName,
        username,
        roleId,
        createdAt: createdAt.toISOString(),
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
        const { organizationId } = await requireCallingAdmin(req);
        const user = await createStaffAccount(organizationId, (req.body ?? {}));
        res.status(200).json({ user });
    }
    catch (err) {
        if (err instanceof admin_1.ApiError) {
            res.status(err.status).json({ error: err.message });
            return;
        }
        console.error('staff-create failed:', err);
        res.status(500).json({ error: 'Something went wrong creating that account.' });
    }
}

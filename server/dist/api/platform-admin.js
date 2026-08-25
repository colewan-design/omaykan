"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = handler;
const firestore_1 = require("firebase-admin/firestore");
const admin_1 = require("./_lib/admin");
// Excludes visually-ambiguous characters (0/O, 1/l/I) — the operator reads
// this back to an owner manually (chat/GCash message), same as the GCash
// reference workflow already does.
const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
function requireSecret(secret) {
    const expected = process.env.PLATFORM_ADMIN_SECRET;
    if (!expected) {
        throw new admin_1.ApiError(500, 'Platform admin is not configured.');
    }
    if (!secret || secret !== expected) {
        throw new admin_1.ApiError(401, 'Incorrect secret.');
    }
}
function toIso(ts) {
    return ts ? ts.toDate().toISOString() : null;
}
function generatePassword() {
    return Array.from({ length: 12 }, () => PASSWORD_ALPHABET[Math.floor(Math.random() * PASSWORD_ALPHABET.length)]).join('');
}
function auditLog(action, organizationSlug) {
    console.log('[platform-admin]', JSON.stringify({ action, organizationSlug, at: new Date().toISOString() }));
}
async function listOrgs() {
    const db = (0, admin_1.getDb)();
    const orgsSnap = await db.collection('organizations').get();
    // One query for every org's admin(s), grouped in memory, instead of one
    // query per org — and one batched Auth lookup for their disabled state,
    // instead of one Auth call per admin.
    const adminUsersSnap = await db.collection('users').where('roleKey', '==', 'admin').get();
    const adminsByOrg = new Map();
    for (const userDoc of adminUsersSnap.docs) {
        const data = userDoc.data();
        const list = adminsByOrg.get(data.organizationId) ?? [];
        list.push({ uid: userDoc.id, username: data.username, fullName: data.fullName });
        adminsByOrg.set(data.organizationId, list);
    }
    const disabledByUid = new Map();
    const adminUids = adminUsersSnap.docs.map((d) => d.id);
    for (let i = 0; i < adminUids.length; i += 100) {
        const chunk = adminUids.slice(i, i + 100);
        const result = await (0, admin_1.getAdminAuth)().getUsers(chunk.map((uid) => ({ uid })));
        for (const record of result.users) {
            disabledByUid.set(record.uid, record.disabled);
        }
    }
    const rows = await Promise.all(orgsSnap.docs.map(async (orgDoc) => {
        const orgData = orgDoc.data();
        const [storeSnap, subSnap] = await Promise.all([
            db.doc(`organizations/${orgDoc.id}/stores/main`).get(),
            db.doc(`organizations/${orgDoc.id}/private/subscription`).get(),
        ]);
        const storeData = storeSnap.exists
            ? storeSnap.data()
            : null;
        const subData = subSnap.exists
            ? subSnap.data()
            : null;
        const admins = (adminsByOrg.get(orgDoc.id) ?? []).map((admin) => ({
            ...admin,
            disabled: disabledByUid.get(admin.uid) ?? false,
        }));
        return {
            organizationSlug: orgDoc.id,
            organizationName: orgData.name ?? orgDoc.id,
            suspended: orgData.suspended === true,
            store: storeData
                ? { name: storeData.name, businessMode: storeData.businessMode, pairingCode: storeData.pairingCode }
                : null,
            subscription: subData
                ? {
                    status: subData.status,
                    plan: subData.plan,
                    amountCents: subData.amountCents,
                    gcashReference: subData.gcashReference,
                    submittedAt: toIso(subData.submittedAt),
                    verifiedAt: toIso(subData.verifiedAt),
                }
                : null,
            admins,
        };
    }));
    return rows.sort((a, b) => a.organizationName.localeCompare(b.organizationName));
}
async function verifySignup(organizationSlug) {
    if (!organizationSlug) {
        throw new admin_1.ApiError(400, 'organizationSlug is required.');
    }
    const subRef = (0, admin_1.getDb)().doc(`organizations/${organizationSlug}/private/subscription`);
    const subSnap = await subRef.get();
    if (!subSnap.exists) {
        throw new admin_1.ApiError(404, 'No subscription record for that store.');
    }
    await subRef.update({ status: 'active', verifiedAt: firestore_1.FieldValue.serverTimestamp() });
}
async function rejectSignup(organizationSlug) {
    if (!organizationSlug) {
        throw new admin_1.ApiError(400, 'organizationSlug is required.');
    }
    const subRef = (0, admin_1.getDb)().doc(`organizations/${organizationSlug}/private/subscription`);
    const subSnap = await subRef.get();
    if (!subSnap.exists) {
        throw new admin_1.ApiError(404, 'No subscription record for that store.');
    }
    await subRef.update({ status: 'rejected', rejectedAt: firestore_1.FieldValue.serverTimestamp() });
}
async function setOrgSuspended(organizationSlug, suspended) {
    if (!organizationSlug) {
        throw new admin_1.ApiError(400, 'organizationSlug is required.');
    }
    const orgRef = (0, admin_1.getDb)().doc(`organizations/${organizationSlug}`);
    const orgSnap = await orgRef.get();
    if (!orgSnap.exists) {
        throw new admin_1.ApiError(404, 'No such store.');
    }
    await orgRef.update({ suspended });
}
async function resetOwnerPassword(uid) {
    if (!uid) {
        throw new admin_1.ApiError(400, 'uid is required.');
    }
    const password = generatePassword();
    await (0, admin_1.getAdminAuth)().updateUser(uid, { password });
    return password;
}
async function setOwnerDisabled(uid, disabled) {
    if (!uid) {
        throw new admin_1.ApiError(400, 'uid is required.');
    }
    await (0, admin_1.getAdminAuth)().updateUser(uid, { disabled });
}
// Best-effort cascade, not transactional — same accepted risk already taken
// by api/signup.ts's batch-after-createUser sequencing. A partial failure
// here (e.g. mid-loop) can leave some staff accounts deleted and others not;
// re-running deleteOrg is safe (already-deleted users are skipped).
async function deleteOrg(organizationSlug, confirmSlug) {
    if (!organizationSlug) {
        throw new admin_1.ApiError(400, 'organizationSlug is required.');
    }
    if (confirmSlug !== organizationSlug) {
        throw new admin_1.ApiError(400, 'Confirmation did not match the store ID.');
    }
    const db = (0, admin_1.getDb)();
    const usersSnap = await db.collection('users').where('organizationId', '==', organizationSlug).get();
    for (const userDoc of usersSnap.docs) {
        try {
            await (0, admin_1.getAdminAuth)().deleteUser(userDoc.id);
        }
        catch {
            // Already gone from Auth (or some other transient issue) — don't let
            // one orphaned account block deleting the rest of the org.
        }
        await userDoc.ref.delete();
    }
    await db.recursiveDelete(db.doc(`organizations/${organizationSlug}`));
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
        requireSecret(body.secret);
        const organizationSlug = body.organizationSlug ?? '';
        switch (body.action) {
            case 'listOrgs': {
                const orgs = await listOrgs();
                res.status(200).json({ orgs });
                return;
            }
            case 'verify': {
                await verifySignup(organizationSlug);
                auditLog('verify', organizationSlug);
                res.status(200).json({ ok: true });
                return;
            }
            case 'reject': {
                await rejectSignup(organizationSlug);
                auditLog('reject', organizationSlug);
                res.status(200).json({ ok: true });
                return;
            }
            case 'suspendOrg': {
                await setOrgSuspended(organizationSlug, true);
                auditLog('suspendOrg', organizationSlug);
                res.status(200).json({ ok: true });
                return;
            }
            case 'reactivateOrg': {
                await setOrgSuspended(organizationSlug, false);
                auditLog('reactivateOrg', organizationSlug);
                res.status(200).json({ ok: true });
                return;
            }
            case 'resetOwnerPassword': {
                const password = await resetOwnerPassword(body.uid ?? '');
                auditLog('resetOwnerPassword', organizationSlug);
                res.status(200).json({ password });
                return;
            }
            case 'setOwnerDisabled': {
                await setOwnerDisabled(body.uid ?? '', body.disabled === true);
                auditLog('setOwnerDisabled', organizationSlug);
                res.status(200).json({ ok: true });
                return;
            }
            case 'deleteOrg': {
                await deleteOrg(organizationSlug, body.confirmSlug ?? '');
                auditLog('deleteOrg', organizationSlug);
                res.status(200).json({ ok: true });
                return;
            }
            default:
                res.status(400).json({ error: 'Unknown action.' });
        }
    }
    catch (err) {
        if (err instanceof admin_1.ApiError) {
            res.status(err.status).json({ error: err.message });
            return;
        }
        console.error('platform-admin failed:', err);
        res.status(500).json({ error: 'Something went wrong.' });
    }
}

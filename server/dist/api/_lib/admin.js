"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ApiError = void 0;
exports.getDb = getDb;
exports.getAdminAuth = getAdminAuth;
exports.setCorsHeaders = setCorsHeaders;
const app_1 = require("firebase-admin/app");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
// Vercel's Node runtime has no ambient Google Cloud credentials the way
// Firebase Cloud Functions does, so the Admin SDK is initialized from an
// explicit service account key set as Vercel project env vars. Generate the
// key from Firebase Console > Project Settings > Service Accounts (a free,
// Spark-plan-compatible action) and never commit it.
//
// Lazily initialized (not a top-level `export const db = ...`) so a missing
// env var throws inside a request's try/catch — caught and turned into a
// clean 500 JSON response — instead of throwing at module load time, which
// crashes the whole function process before it can respond at all.
let cachedApp = null;
let cachedDb = null;
let cachedAuth = null;
function getAdminApp() {
    if (cachedApp)
        return cachedApp;
    const existing = (0, app_1.getApps)();
    if (existing.length) {
        cachedApp = existing[0];
        return cachedApp;
    }
    const projectId = process.env.FIREBASE_PROJECT_ID;
    const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
    const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');
    if (!projectId || !clientEmail || !privateKey) {
        throw new Error('Missing Firebase Admin credentials — set FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, and FIREBASE_PRIVATE_KEY in the Vercel project env vars.');
    }
    cachedApp = (0, app_1.initializeApp)({ credential: (0, app_1.cert)({ projectId, clientEmail, privateKey }) });
    return cachedApp;
}
function getDb() {
    if (!cachedDb)
        cachedDb = (0, firestore_1.getFirestore)(getAdminApp());
    return cachedDb;
}
function getAdminAuth() {
    if (!cachedAuth)
        cachedAuth = (0, auth_1.getAuth)(getAdminApp());
    return cachedAuth;
}
class ApiError extends Error {
    status;
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}
exports.ApiError = ApiError;
function setCorsHeaders(res) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
}

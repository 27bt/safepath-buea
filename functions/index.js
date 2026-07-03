const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { geohashQueryBounds, distanceBetween } = require("geofire-common");

initializeApp();
const db = getFirestore();

const MERGE_RADIUS_METERS = 20;
const EXPIRY_HOURS = 48;

/**
 * When a new hazard is created, look for an existing active hazard of the
 * same type within MERGE_RADIUS_METERS. If one exists, fold the new report
 * into it (bump confidence_count, union confirmed_by, refresh
 * last_confirmed_at) and delete the just-created duplicate. This runs once
 * per new document and never touches the doc it merges into in a way that
 * would re-trigger this same onCreate handler, so there's no merge loop.
 */
exports.onHazardCreated = onDocumentCreated("hazards/{hazardId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const newHazard = snap.data();
  if (!newHazard.location || !newHazard.type) return;

  const center = [newHazard.location.latitude, newHazard.location.longitude];
  const bounds = geohashQueryBounds(center, MERGE_RADIUS_METERS);

  const candidateDocs = [];
  for (const [start, end] of bounds) {
    const rangeSnapshot = await db
      .collection("hazards")
      .orderBy("geohash")
      .startAt(start)
      .endAt(end)
      .get();
    candidateDocs.push(...rangeSnapshot.docs);
  }

  let closestMatch = null;
  let closestDistanceMeters = Infinity;

  for (const doc of candidateDocs) {
    if (doc.id === snap.id) continue;
    const data = doc.data();
    if (data.type !== newHazard.type || data.status !== "active" || !data.location) continue;

    const distanceMeters =
      distanceBetween([data.location.latitude, data.location.longitude], center) * 1000;

    if (distanceMeters <= MERGE_RADIUS_METERS && distanceMeters < closestDistanceMeters) {
      closestDistanceMeters = distanceMeters;
      closestMatch = doc;
    }
  }

  if (!closestMatch) return;

  const existing = closestMatch.data();
  const mergedConfirmedBy = Array.from(
    new Set([...(existing.confirmed_by || []), ...(newHazard.confirmed_by || [])])
  );

  await closestMatch.ref.update({
    confidence_count: (existing.confidence_count || 1) + (newHazard.confidence_count || 1),
    confirmed_by: mergedConfirmedBy,
    last_confirmed_at: FieldValue.serverTimestamp(),
  });

  await snap.ref.delete();
});

/**
 * Hourly sweep: any active hazard not reconfirmed in EXPIRY_HOURS is marked
 * expired so clients (which only read status == "active") stop seeing it.
 */
exports.expireStaleHazards = onSchedule("every 60 minutes", async () => {
  const cutoff = Timestamp.fromMillis(Date.now() - EXPIRY_HOURS * 60 * 60 * 1000);

  const staleSnapshot = await db
    .collection("hazards")
    .where("status", "==", "active")
    .where("last_confirmed_at", "<", cutoff)
    .get();

  if (staleSnapshot.empty) return;

  const batch = db.batch();
  staleSnapshot.docs.forEach((doc) => batch.update(doc.ref, { status: "expired" }));
  await batch.commit();
});

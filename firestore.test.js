const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CLIENT_UID = "client_alice_123";
const MOTARD_UID = "motard_koffi_456";
const STRANGER_UID = "stranger_eve_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validUserPayload(uid, role = "CLIENT") {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    role,
    phone: "+243812345678",
    nom: role === "CLIENT" ? "Alice M." : "Koffi B.",
    motoModele: role === "MOTARD" ? "TVS Apache 160" : "",
    plaque: role === "MOTARD" ? "KN-4821-AB" : "",
    documentTexte: "",
    note: 4.9,
    totalGains: 0,
    totalDepenses: 0,
    completedRides: 0,
    enLigne: role === "MOTARD",
    createdAt: now,
    updatedAt: now,
  };
}

function validRidePayload(status = "EN_NEGOCIATION") {
  const now = new Date(Date.now() - 2000);
  return {
    id_client: CLIENT_UID,
    id_motard: MOTARD_UID,
    nom_client: "Alice M.",
    nom_motard: "Koffi B.",
    moto_motard: "TVS Apache 160",
    depart: "Gombe",
    destination: "Bandalungwa",
    status,
    prix_actuel: 2500,
    dernier_proposeur_id: CLIENT_UID,
    participantUids: [CLIENT_UID, MOTARD_UID],
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write users, motards_public, rides, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(CLIENT_UID).get());
  await assertFails(unauthDb.collection("motards_public").where("enLigne", "==", true).get());
  await assertFails(unauthDb.collection("rides").where("participantUids", "array-contains", CLIENT_UID).get());
  await assertFails(unauthDb.collection("rides").doc("ride_1").collection("messages").get());
});

test("User PII isolation: owner can create/read/update own profile; stranger is denied; list is denied", async () => {
  const clientDb = testEnv.authenticatedContext(CLIENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  await assertSucceeds(
    clientDb.collection("users").doc(CLIENT_UID).set(validUserPayload(CLIENT_UID, "CLIENT"))
  );
  await assertSucceeds(clientDb.collection("users").doc(CLIENT_UID).get());
  await assertSucceeds(
    clientDb.collection("users").doc(CLIENT_UID).update({
      documentTexte: "PERMIS CAT A - KINSHASA",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Stranger cannot read or update Alice's PII profile
  await assertFails(strangerDb.collection("users").doc(CLIENT_UID).get());
  await assertFails(strangerDb.collection("users").doc(CLIENT_UID).update({ phone: "000" }));
  // Nobody can list /users
  await assertFails(clientDb.collection("users").get());
  // Reject ghost field / @DocumentId `id` field
  await assertFails(
    strangerDb.collection("users").doc(STRANGER_UID).set({
      ...validUserPayload(STRANGER_UID),
      id: STRANGER_UID,
    })
  );
});

test("Public motards directory: aligned online query succeeds; unfiltered list fails; only owner writes", async () => {
  const motardDb = testEnv.authenticatedContext(MOTARD_UID).firestore();
  const clientDb = testEnv.authenticatedContext(CLIENT_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    motardDb.collection("motards_public").doc(MOTARD_UID).set({
      motardId: MOTARD_UID,
      nom: "Koffi B.",
      motoModele: "TVS Apache 160",
      plaque: "KN-4821-AB",
      note: 4.9,
      distanceKm: 0.6,
      quartier: "Gombe",
      enLigne: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client filtered query succeeds
  await assertSucceeds(clientDb.collection("motards_public").where("enLigne", "==", true).get());
  // Unfiltered query fails
  await assertFails(clientDb.collection("motards_public").get());
  // Client cannot overwrite motard's listing
  await assertFails(clientDb.collection("motards_public").doc(MOTARD_UID).update({ enLigne: false }));
});

test("Ride state machine & payment: negotiation -> ACCORD_TROUVE -> Client ONLY pays -> EN_COURS -> TERMINEE", async () => {
  const clientDb = testEnv.authenticatedContext(CLIENT_UID).firestore();
  const motardDb = testEnv.authenticatedContext(MOTARD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const rideRef = clientDb.collection("rides").doc("ride_01");

  await assertSucceeds(rideRef.set(validRidePayload("EN_NEGOCIATION")));

  // Aligned participant query succeeds; unfiltered fails
  await assertSucceeds(
    clientDb.collection("rides").where("participantUids", "array-contains", CLIENT_UID).get()
  );
  await assertFails(clientDb.collection("rides").get());
  await assertFails(strangerDb.collection("rides").doc("ride_01").get());

  // State shortcutting EN_NEGOCIATION -> EN_COURS fails
  await assertFails(
    rideRef.update({ status: "EN_COURS", updatedAt: new Date(Date.now() - 500) })
  );

  // Motard accepts price -> ACCORD_TROUVE
  await assertSucceeds(
    motardDb.collection("rides").doc("ride_01").update({
      prix_actuel: 2500,
      dernier_proposeur_id: CLIENT_UID,
      status: "ACCORD_TROUVE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Motard CANNOT trigger payment (ACCORD_TROUVE -> EN_COURS)
  await assertFails(
    motardDb.collection("rides").doc("ride_01").update({
      status: "EN_COURS",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Client CAN pay (ACCORD_TROUVE -> EN_COURS)
  await assertSucceeds(
    rideRef.update({
      status: "EN_COURS",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Complete ride -> TERMINEE
  await assertSucceeds(
    rideRef.update({
      status: "TERMINEE",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Terminal state lock: no more updates once TERMINEE
  await assertFails(
    rideRef.update({
      status: "EN_NEGOCIATION",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Messages subcollection under active ride: participants read/write, proposals update, strangers denied", async () => {
  const clientDb = testEnv.authenticatedContext(CLIENT_UID).firestore();
  const motardDb = testEnv.authenticatedContext(MOTARD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  await assertSucceeds(clientDb.collection("rides").doc("ride_msg").set(validRidePayload("EN_NEGOCIATION")));

  const msgCol = clientDb.collection("rides").doc("ride_msg").collection("messages");
  const now = new Date(Date.now() - 1000);

  // Client sends PROPOSITION_PRIX
  await assertSucceeds(
    msgCol.doc("msg_1").set({
      type: "PROPOSITION_PRIX",
      texte: "Je propose 2 500 FC pour ce trajet.",
      montant: 2500,
      id_expediteur: CLIENT_UID,
      nom_expediteur: "Alice M.",
      statut_proposition: "EN_ATTENTE",
      participantUids: [CLIENT_UID, MOTARD_UID],
      createdAt: now,
    })
  );

  // Client sends TEXTE without optional montant
  await assertSucceeds(
    msgCol.doc("msg_2").set({
      type: "TEXTE",
      texte: "Je suis devant la station.",
      id_expediteur: CLIENT_UID,
      nom_expediteur: "Alice M.",
      statut_proposition: "EN_ATTENTE",
      participantUids: [CLIENT_UID, MOTARD_UID],
      createdAt: now,
    })
  );

  // Motard queries messages with aligned filter
  await assertSucceeds(
    motardDb
      .collection("rides")
      .doc("ride_msg")
      .collection("messages")
      .where("participantUids", "array-contains", MOTARD_UID)
      .get()
  );

  // Motard updates proposal status to ACCEPTEE
  await assertSucceeds(
    motardDb
      .collection("rides")
      .doc("ride_msg")
      .collection("messages")
      .doc("msg_1")
      .update({ statut_proposition: "ACCEPTEE" })
  );

  // Stranger cannot read or write messages
  await assertFails(
    strangerDb.collection("rides").doc("ride_msg").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    strangerDb.collection("rides").doc("ride_msg").collection("messages").doc("msg_3").set({
      type: "TEXTE",
      texte: "Intrusion",
      id_expediteur: STRANGER_UID,
      nom_expediteur: "Eve",
      statut_proposition: "EN_ATTENTE",
      participantUids: [CLIENT_UID, MOTARD_UID],
      createdAt: now,
    })
  );

  // Future timestamp rejected
  await assertFails(
    msgCol.doc("msg_future").set({
      type: "TEXTE",
      texte: "Future message",
      id_expediteur: CLIENT_UID,
      nom_expediteur: "Alice M.",
      statut_proposition: "EN_ATTENTE",
      participantUids: [CLIENT_UID, MOTARD_UID],
      createdAt: new Date(Date.now() + 60000),
    })
  );
});

test("Motard withdrawals: owner can create/list withdrawals; stranger denied; negative montant rejected", async () => {
  const motardDb = testEnv.authenticatedContext(MOTARD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    motardDb.collection("users").doc(MOTARD_UID).set({
      ...validUserPayload(MOTARD_UID, "MOTARD"),
      totalGains: 10000,
      totalRetraits: 0,
    })
  );

  await assertSucceeds(
    motardDb.collection("users").doc(MOTARD_UID).collection("withdrawals").doc("w_1").set({
      userId: MOTARD_UID,
      operateur: "M-Pesa",
      numero: "+243812345678",
      montant: 2500,
      statut: "EFFECTUE",
      createdAt: now,
    })
  );

  await assertSucceeds(
    motardDb.collection("users").doc(MOTARD_UID).collection("withdrawals").get()
  );

  await assertFails(
    strangerDb.collection("users").doc(MOTARD_UID).collection("withdrawals").get()
  );

  await assertFails(
    motardDb.collection("users").doc(MOTARD_UID).collection("withdrawals").doc("w_bad").set({
      userId: MOTARD_UID,
      operateur: "M-Pesa",
      numero: "+243812345678",
      montant: -500,
      statut: "EFFECTUE",
      createdAt: now,
    })
  );
});

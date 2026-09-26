const fs = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, test } = require("node:test");

const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const { get, ref, set, update } = require("firebase/database");


const PROJECT_ID = "demo-avora-alidogukan";
const DEVICE_ID = "avora-001";
const OWNER_UID = "owner-user-001";
const OTHER_UID = "other-user-001";
const FAMILY_UID = "family-user-identity-001";
const RULES_PATH = path.join(__dirname, "..", "firebase-database.rules.json");

let testEnvironment;

function authenticatedDatabase(uid, deviceId = DEVICE_ID) {
  return testEnvironment.authenticatedContext(uid, {
    avora_device_id: deviceId,
  }).database();
}

function unclaimedDatabase(uid) {
  return testEnvironment.authenticatedContext(uid).database();
}

function validFeedback(id, uid = OWNER_UID) {
  return {
    id,
    type: "problem",
    area: "general",
    area_label: "Genel uygulama",
    subject: "Deneme geri bildirimi",
    description: "Bu açıklama güvenlik kuralı testi için yeterince uzundur.",
    contact_email: "owner@example.com",
    diagnostics: {
      app_version: "2.10.0",
      android_version: "16",
      android_sdk: 36,
      manufacturer: "Google",
      model: "Pixel 7 Pro",
    },
    status: "new",
    created_at: Date.now(),
    device_id: DEVICE_ID,
    source: "android",
    user_id: uid,
  };
}

function validSuperadminCommand(id, overrides = {}) {
  return {
    id,
    operation: "preview_delete",
    category: "seasons",
    record_id: "zone-003-2026-1785445200",
    requested_by_uid: OWNER_UID,
    requested_at: Date.now(),
    expires_at: Date.now() + 240000,
    source: "android",
    status: "pending",
    ...overrides,
  };
}

function validGrowthPhoto(id, overrides = {}) {
  return {
    id,
    zone_id: "zone-001",
    season_id: "season-zone-001-2026",
    note: "Aynı açıdan gelişim takibi",
    related_application_id: "plant_assistant",
    analysis_title: "Gelişim dengeli görünüyor",
    analysis_meta: "Görsel güveni %87",
    analysis_context: "Yaprak yoğunluğu ve gövde dengesi değerlendirildi.",
    analysis_advice: "Üç gün sonra aynı açıdan tekrar fotoğraf çekin.",
    analysis_goal: "growth_status",
    analysis_confidence: 87,
    growth_score: 76,
    growth_stage: "Vejetatif gelişim",
    growth_trend: "FIRST_RECORD",
    growth_score_delta: 0,
    growth_signals: "Yeni yaprak oluşumu\nCanlı yaprak rengi",
    growth_previous_captured_at_epoch: 0,
    captured_at_epoch: 1788271200,
    photo_kept_on_owner_phone: true,
    metadata_updated_at_epoch: 1788271260,
    ...overrides,
  };
}

function validSeedlingBatch(id, overrides = {}) {
  return {
    batch_id: id,
    plant_type: "Domates",
    emoji: "🍅",
    variety: "H2274",
    area: "Fide rafı 1",
    node_id: "seedling-001",
    status: "ACTIVE",
    stage: "SOWN",
    sowing_date_epoch: 1788271200,
    estimated_emergence_epoch: 1788876000,
    estimated_transplant_epoch: 1791907200,
    seed_count: 100,
    tray_cell_count: 100,
    healthy_count: 100,
    created_at_epoch: 1788271200,
    updated_at_epoch: 1788271200,
    ...overrides,
  };
}

before(async () => {
  testEnvironment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    database: {
      rules: fs.readFileSync(RULES_PATH, "utf8"),
    },
  });
});

beforeEach(async () => {
  await testEnvironment.clearDatabase();
});

after(async () => {
  await testEnvironment.cleanup();
});

test("only the claimed device owner can read or write the device", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `devices/${DEVICE_ID}/status`), {
      online: true,
    });
  });

  const owner = authenticatedDatabase(OWNER_UID);
  const otherDevice = authenticatedDatabase(OWNER_UID, "avora-002");
  const otherUser = unclaimedDatabase(OTHER_UID);
  const anonymous = testEnvironment.unauthenticatedContext().database();

  await assertSucceeds(get(ref(owner, `devices/${DEVICE_ID}`)));
  await assertFails(get(ref(otherDevice, `devices/${DEVICE_ID}`)));
  await assertFails(get(ref(otherUser, `devices/${DEVICE_ID}`)));
  await assertFails(get(ref(anonymous, `devices/${DEVICE_ID}`)));
  await assertSucceeds(
    update(ref(owner, `devices/${DEVICE_ID}/commands`), { auto_mode: true }),
  );
  await assertFails(
    update(ref(otherUser, `devices/${DEVICE_ID}/commands`), {
      auto_mode: false,
    }),
  );
});

test("only the device owner can create bounded superadmin commands", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const family = unclaimedDatabase(FAMILY_UID);
  const outsider = unclaimedDatabase(OTHER_UID);
  const commandId = "11111111-1111-1111-1111-111111111111";
  const commandPath = `superadmin_devices/${DEVICE_ID}/commands/${commandId}`;

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `device_access/${DEVICE_ID}/${FAMILY_UID}`), {
      approved: true,
      firebase_uid: FAMILY_UID,
      nas_user_id: "11111111-1111-1111-1111-111111111111",
      email: "family@example.com",
      display_name: "Family",
      approved_by: OWNER_UID,
      approved_at: Date.now() - 1000,
    });
  });

  await assertSucceeds(set(ref(owner, commandPath), validSuperadminCommand(commandId)));
  await assertFails(update(ref(owner, commandPath), { status: "processing" }));
  await assertSucceeds(get(ref(owner, `superadmin_devices/${DEVICE_ID}`)));
  await assertFails(get(ref(family, `superadmin_devices/${DEVICE_ID}`)));
  await assertFails(set(
    ref(owner, `devices/${DEVICE_ID}/superadmin/commands/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb`),
    validSuperadminCommand("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
  ));
  await assertFails(set(
    ref(family, `superadmin_devices/${DEVICE_ID}/commands/22222222-2222-2222-2222-222222222222`),
    validSuperadminCommand("22222222-2222-2222-2222-222222222222", {
      requested_by_uid: FAMILY_UID,
    }),
  ));
  await assertFails(set(
    ref(outsider, `superadmin_devices/${DEVICE_ID}/commands/33333333-3333-3333-3333-333333333333`),
    validSuperadminCommand("33333333-3333-3333-3333-333333333333", {
      requested_by_uid: OTHER_UID,
    }),
  ));
  await assertFails(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/44444444-4444-4444-4444-444444444444`),
    validSuperadminCommand("44444444-4444-4444-4444-444444444444", {
      operation: "raw_database_access",
    }),
  ));
  await assertFails(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/55555555-5555-5555-5555-555555555555`),
    validSuperadminCommand("55555555-5555-5555-5555-555555555555", {
      operation: "update",
    }),
  ));
  await assertSucceeds(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/66666666-6666-6666-6666-666666666666`),
    validSuperadminCommand("66666666-6666-6666-6666-666666666666", {
      operation: "update",
      replacement_json: "{\"label\":\"Düzeltilmiş sezon\"}",
      expected_record_json: "{\"label\":\"Eski sezon\"}",
    }),
  ));
  await assertFails(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`),
    validSuperadminCommand("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", {
      operation: "update",
      replacement_json: "{\"label\":\"Düzeltilmiş sezon\"}",
    }),
  ));
  await assertFails(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/77777777-7777-7777-7777-777777777777`),
    validSuperadminCommand("77777777-7777-7777-7777-777777777777", {
      operation: "delete",
    }),
  ));
  await assertSucceeds(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/88888888-8888-8888-8888-888888888888`),
    validSuperadminCommand("88888888-8888-8888-8888-888888888888", {
      operation: "delete",
      preview_token: "a".repeat(64),
    }),
  ));
  await assertFails(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/99999999-9999-9999-9999-999999999999`),
    validSuperadminCommand("99999999-9999-9999-9999-999999999999", {
      operation: "delete",
      preview_token: "not-a-valid-token",
    }),
  ));
  await assertSucceeds(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/10101010-1010-1010-1010-101010101010`),
    validSuperadminCommand("10101010-1010-1010-1010-101010101010", {
      operation: "delete_feedback",
      category: "feedback",
      record_id: "123e4567-e89b-12d3-a456-426614174099",
    }),
  ));
  await assertFails(set(
    ref(owner, `superadmin_devices/${DEVICE_ID}/commands/11111111-1010-1010-1010-101010101010`),
    validSuperadminCommand("11111111-1010-1010-1010-101010101010", {
      operation: "delete_feedback",
      category: "seasons",
    }),
  ));
  await assertFails(set(
    ref(family, `superadmin_devices/${DEVICE_ID}/commands/12121212-1010-1010-1010-101010101010`),
    validSuperadminCommand("12121212-1010-1010-1010-101010101010", {
      operation: "delete_feedback", category: "feedback", requested_by_uid: FAMILY_UID,
    }),
  ));
});

test("owner approval grants only the selected Firebase user device access", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `devices/${DEVICE_ID}/status`), {
      online: true,
    });
  });

  const owner = authenticatedDatabase(OWNER_UID);
  const family = unclaimedDatabase(FAMILY_UID);
  const stranger = unclaimedDatabase("stranger-user-001");
  const grantPath = `device_access/${DEVICE_ID}/${FAMILY_UID}`;
  const grant = {
    approved: true,
    firebase_uid: FAMILY_UID,
    nas_user_id: "123e4567-e89b-12d3-a456-426614174000",
    email: "family@example.com",
    display_name: "Aile Üyesi",
    approved_by: OWNER_UID,
    approved_at: Date.now(),
  };

  await assertSucceeds(set(ref(owner, grantPath), grant));
  await assertSucceeds(get(ref(family, grantPath)));
  await assertFails(get(ref(stranger, grantPath)));
  await assertFails(get(ref(family,
    `device_access/${DEVICE_ID}/${OWNER_UID}`)));
  await assertSucceeds(get(ref(family, `devices/${DEVICE_ID}`)));
  await assertSucceeds(update(
    ref(family, `devices/${DEVICE_ID}/commands`),
    { auto_mode: true },
  ));
  await assertFails(get(ref(stranger, `devices/${DEVICE_ID}`)));
  await assertFails(set(
    ref(family, `device_access/${DEVICE_ID}/${FAMILY_UID}`),
    grant,
  ));
  await assertFails(set(ref(owner,
    `device_access/${DEVICE_ID}/different-user-001`), grant));
});

test("only the device owner can change the bounded manual watering safety limit", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `devices/${DEVICE_ID}`), {
      status: { online: true },
      weather: {
        irrigation_settings: {
          manual_watering_max_duration_seconds: 18000,
        },
      },
    });
    await set(ref(context.database(), `device_access/${DEVICE_ID}/${FAMILY_UID}`), {
      approved: true,
      firebase_uid: FAMILY_UID,
      nas_user_id: "123e4567-e89b-12d3-a456-426614174000",
      email: "family@example.com",
      display_name: "Aile Üyesi",
      approved_by: OWNER_UID,
      approved_at: Date.now(),
    });
  });

  const owner = authenticatedDatabase(OWNER_UID);
  const family = unclaimedDatabase(FAMILY_UID);
  const path = `devices/${DEVICE_ID}/weather/irrigation_settings/manual_watering_max_duration_seconds`;

  await assertSucceeds(set(ref(owner, path), 18000));
  await assertFails(set(ref(owner, path), 18001));
  await assertFails(set(ref(owner, path), 4));
  await assertFails(set(ref(family, path), 7200));
  await assertSucceeds(update(
    ref(family, `devices/${DEVICE_ID}/commands`),
    { auto_mode: false },
  ));
});

test("owner cannot create a malformed family access grant", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  await assertFails(set(ref(owner,
    `device_access/${DEVICE_ID}/${OTHER_UID}`), {
    approved: true,
    firebase_uid: OTHER_UID,
    display_name: "Eksik kayıt",
    approved_by: OWNER_UID,
    approved_at: Date.now(),
  }));
});

test("owner can submit only a bounded one-shot network configuration", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const otherUser = unclaimedDatabase(OTHER_UID);
  const path = `devices/${DEVICE_ID}/commands/network_configuration`;
  const valid = {
    requested: true,
    request_id: "123e4567-e89b-12d3-a456-426614174010",
    interface: "wlan0",
    mode: "STATIC",
    ip_address: "192.168.1.50",
    prefix_length: 24,
    gateway: "192.168.1.1",
    primary_dns: "1.1.1.1",
    secondary_dns: "8.8.8.8",
    requested_at: Date.now(),
    expires_at: Date.now() + 180000,
    source: "android",
  };

  await assertSucceeds(set(ref(owner, path), valid));
  await assertFails(set(ref(otherUser, path), valid));

  await assertFails(set(ref(owner, path), {
    ...valid,
    request_id: "not-a-uuid",
  }));
  await assertFails(set(ref(owner, path), {
    ...valid,
    interface: "wlan0;shutdown",
  }));
  await assertFails(set(ref(owner, path), {
    ...valid,
    mode: "SHELL",
  }));
  await assertFails(set(ref(owner, path), {
    ...valid,
    expires_at: Date.now() + 900000,
  }));
  await assertFails(set(ref(owner, path), {
    ...valid,
    arbitrary_command: "shutdown -h now",
  }));
});

test("owner can create only a bounded feedback schema", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const validId = "123e4567-e89b-12d3-a456-426614174000";
  const validPath = `feedback_devices/${DEVICE_ID}/user_feedback/${validId}`;

  await assertSucceeds(set(ref(owner, validPath), validFeedback(validId)));

  const extraId = "123e4567-e89b-12d3-a456-426614174001";
  const extra = validFeedback(extraId);
  extra.api_key = "must-not-be-accepted";
  await assertFails(
    set(
      ref(owner, `feedback_devices/${DEVICE_ID}/user_feedback/${extraId}`),
      extra,
    ),
  );

  const wrongUserId = "123e4567-e89b-12d3-a456-426614174002";
  await assertFails(
    set(
      ref(owner, `feedback_devices/${DEVICE_ID}/user_feedback/${wrongUserId}`),
      validFeedback(wrongUserId, OTHER_UID),
    ),
  );

  const deliveryId = "123e4567-e89b-12d3-a456-426614174003";
  const forgedDelivery = validFeedback(deliveryId);
  forgedDelivery.email_delivery = { status: "sent" };
  await assertFails(
    set(
      ref(owner, `feedback_devices/${DEVICE_ID}/user_feedback/${deliveryId}`),
      forgedDelivery,
    ),
  );
});

test("feedback cannot be edited or submitted by an unclaimed user", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const otherUser = unclaimedDatabase(OTHER_UID);
  const id = "123e4567-e89b-12d3-a456-426614174004";
  const feedbackPath = `feedback_devices/${DEVICE_ID}/user_feedback/${id}`;

  await assertSucceeds(set(ref(owner, feedbackPath), validFeedback(id)));
  await assertFails(update(ref(owner, feedbackPath), { subject: "Değiştirildi" }));
  await assertFails(
    set(
      ref(otherUser, `feedback_devices/${DEVICE_ID}/user_feedback/${id}-other`),
      validFeedback(id),
    ),
  );
  await assertFails(set(ref(owner, feedbackPath), null));
});

test("growth photo metadata accepts only the bounded owner schema", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const otherUser = unclaimedDatabase(OTHER_UID);
  const validId = "growth-photo-001";
  const basePath = `devices/${DEVICE_ID}/garden_journal/photo_metadata`;

  await assertSucceeds(
    set(ref(owner, `${basePath}/${validId}`), validGrowthPhoto(validId)),
  );

  const incompleteId = "growth-photo-incomplete";
  const incomplete = validGrowthPhoto(incompleteId, {
    growth_score: -1,
    growth_stage: "",
    growth_trend: "",
    growth_score_delta: 0,
    growth_signals: "",
    growth_previous_captured_at_epoch: 0,
  });
  await assertSucceeds(
    set(ref(owner, `${basePath}/${incompleteId}`), incomplete),
  );
  await assertFails(
    set(
      ref(otherUser, `${basePath}/growth-photo-other`),
      validGrowthPhoto("growth-photo-other"),
    ),
  );

  const outOfRange = validGrowthPhoto("growth-photo-score", {
    growth_score: 101,
  });
  await assertFails(set(ref(owner, `${basePath}/${outOfRange.id}`), outOfRange));

  const mismatchedGoal = validGrowthPhoto("growth-photo-health", {
    analysis_goal: "health_screening",
  });
  await assertFails(
    set(ref(owner, `${basePath}/${mismatchedGoal.id}`), mismatchedGoal),
  );

  const oversizedSignals = validGrowthPhoto("growth-photo-signals", {
    growth_signals: "x".repeat(1001),
  });
  await assertFails(
    set(ref(owner, `${basePath}/${oversizedSignals.id}`), oversizedSignals),
  );

  const unknownField = validGrowthPhoto("growth-photo-secret");
  unknownField.api_key = "must-not-be-accepted";
  await assertFails(
    set(ref(owner, `${basePath}/${unknownField.id}`), unknownField),
  );

  await assertSucceeds(set(ref(owner, `${basePath}/${validId}`), null));
});

test("portable restore skips legacy photo metadata that newer rules reject", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const devicePath = `devices/${DEVICE_ID}`;
  const legacyId = "legacy-photo-001";

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(
      ref(context.database(), `${devicePath}/garden_journal/photo_metadata/${legacyId}`),
      {
        id: legacyId,
        zone_id: "zone-001",
        note: "Kurallar sıkılaştırılmadan önce oluşturulmuş kayıt",
        captured_at_epoch: 1788271200,
      },
    );
  });

  await assertFails(update(ref(owner, devicePath), {
    "profile/name": "AVORA",
    [`garden_journal/photo_metadata/${legacyId}/note`]:
      "Kurallar sıkılaştırılmadan önce oluşturulmuş kayıt",
  }));

  await assertSucceeds(update(ref(owner, devicePath), {
    "profile/name": "AVORA",
    "commands/relay": false,
    "commands/auto_mode": false,
    "garden_journal/events/event-001/note": "Sulama kontrol edildi",
  }));
});

test("backend delivery state remains read-only to the Android owner", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const id = "123e4567-e89b-12d3-a456-426614174005";
  const feedbackPath = `feedback_devices/${DEVICE_ID}/user_feedback/${id}`;

  await assertSucceeds(set(ref(owner, feedbackPath), validFeedback(id)));
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const database = context.database();
    await set(ref(database, `${feedbackPath}/email_delivery`), {
      status: "sent",
      sender: "raspberry_pi",
    });
    await set(
      ref(database, `devices/${DEVICE_ID}/user_feedback_email_state`),
      { activation_epoch: 123456 },
    );
    await set(
      ref(database, `devices/${DEVICE_ID}/user_feedback/legacy-feedback`),
      {
        type: "problem",
        subject: "Eski kayıt",
        created_at: 1,
      },
    );
  });

  await assertFails(
    update(ref(owner, `${feedbackPath}/email_delivery`), { status: "failed" }),
  );
  await assertFails(
    update(ref(owner, `devices/${DEVICE_ID}/user_feedback_email_state`), {
      activation_epoch: 0,
    }),
  );

  // The immutable legacy branch temporarily blocks whole-device changes
  // until the Admin SDK migration removes the old feedback records.
  await assertFails(update(ref(owner, `devices/${DEVICE_ID}`), {
    "settings/language": "tr",
  }));
  await assertFails(set(ref(owner, `devices/${DEVICE_ID}/user_feedback/new-feedback`), validFeedback("new-feedback")));
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `devices/${DEVICE_ID}/user_feedback`), null);
  });
  await assertSucceeds(update(ref(owner, `devices/${DEVICE_ID}`), {
    "settings/language": "tr",
  }));
});

test("owner can manage bounded seedling batches and daily observations", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const id = "batch-seedling-001";
  const devicePath = `devices/${DEVICE_ID}`;
  const batchPath = `${devicePath}/seedling/batches/${id}`;
  await assertSucceeds(set(ref(owner, batchPath), validSeedlingBatch(id)));
  const directSowId = "batch-direct-sow-001";
  await assertSucceeds(set(
    ref(owner, `${devicePath}/seedling/batches/${directSowId}`),
    validSeedlingBatch(directSowId, {
      crop_id: "carrot",
      plant_type: "Havuç",
      tray_cell_count: 0,
    }),
  ));
  await assertSucceeds(update(ref(owner, batchPath), {
    stage: "GERMINATING",
    germination_date_epoch: 1788357600,
    updated_at_epoch: 1788357600,
  }));

  await assertFails(update(ref(owner, batchPath), {
    first_leaf_date_epoch: 1788000000,
  }));
  await assertSucceeds(update(ref(owner, batchPath), {
    stage: "COTYLEDON",
    first_leaf_date_epoch: 1788357600,
    updated_at_epoch: 1788357600,
  }));
  await assertSucceeds(update(ref(owner, batchPath), {
    stage: "TRUE_LEAVES",
    true_leaves_date_epoch: 1788357600,
    updated_at_epoch: 1788357600,
  }));
  await assertFails(update(ref(owner, batchPath), {
    true_leaves_date_epoch: 1788357599,
  }));
  await assertSucceeds(update(ref(owner, batchPath), {
    stage: "HARDENING",
    hardening_date_epoch: 1788357600,
    updated_at_epoch: 1788357600,
  }));
  await assertFails(update(ref(owner, batchPath), {
    hardening_date_epoch: 1788357599,
  }));

  const logPath = `devices/${DEVICE_ID}/seedling/daily_logs/${id}/log-001`;
  await assertSucceeds(set(ref(owner, logPath), {
    log_id: "log-001",
    batch_id: id,
    height_cm: 3.5,
    leaf_count: 2,
    healthy_count: 96,
    watered: true,
    note: "Gelişim dengeli.",
    created_at_epoch: 1788357600,
    sensor_snapshot_available: true,
    sensor_snapshot_fresh: true,
    sensor_node_id: "seedling-001",
    sensor_air_temperature_c: 24.5,
    sensor_air_humidity_pct: 71,
    sensor_root_temperature_c: 23,
    sensor_soil_moisture_available: true,
    sensor_soil_moisture_pct: 62,
    sensor_soil_raw: 12000,
    sensor_light_lux: 8400,
    sensor_received_at_epoch: 1788357595,
    sensor_captured_at_epoch: 1788357600,
  }));
  await assertFails(update(ref(owner, logPath), {
    sensor_air_humidity_pct: 140,
  }));

  const photoId = "550e8400-e29b-41d4-a716-446655440000";
  const photoPath = `devices/${DEVICE_ID}/seedling/daily_logs/${id}/${photoId}.jpg`;
  await assertSucceeds(update(ref(owner, logPath), {
    photo_id: photoId,
    photo_storage_path: "",
  }));
  await assertSucceeds(update(ref(owner, logPath), {
    photo_storage_path: photoPath,
  }));
  await assertFails(update(ref(owner, logPath), {
    photo_storage_path: `devices/${DEVICE_ID}/seedling/daily_logs/other/${photoId}.jpg`,
  }));
  await assertFails(update(ref(owner, logPath), {
    photo_id: "",
  }));

  await assertSucceeds(update(ref(owner, batchPath), {
    status: "ARCHIVED",
    archive_reason: "MANUAL",
    archived_at_epoch: 1788357601,
    updated_at_epoch: 1788357601,
  }));
  await assertSucceeds(update(ref(owner, logPath), {
    note: "Arşivde düzeltilen günlük notu.",
    healthy_count: 95,
  }));
  await assertFails(update(ref(owner, logPath), {
    created_at_epoch: 1788357601,
  }));
  await assertFails(set(
    ref(owner, `devices/${DEVICE_ID}/seedling/daily_logs/${id}/log-archived`),
    {
      log_id: "log-archived",
      batch_id: id,
      height_cm: 4,
      leaf_count: 3,
      healthy_count: 95,
      watered: false,
      note: "Arşiv sonrası yeni kayıt",
      created_at_epoch: 1788357601,
    },
  ));
  await assertFails(update(ref(owner, batchPath), {
    archive_reason: "TRANSFERRED",
  }));
  await assertSucceeds(update(ref(owner, batchPath), {
    status: "ACTIVE",
    archive_reason: null,
    archived_at_epoch: 0,
    updated_at_epoch: 1788357602,
  }));

  const seasonId = "zone-001-seedling-batch-seedling-001";
  await assertSucceeds(update(ref(owner, batchPath), {
    stage: "READY",
    status: "ARCHIVED",
    archive_reason: "TRANSFERRED",
    archived_at_epoch: 1788357603,
    transferred_season_id: seasonId,
    transferred_zone_id: "zone-001",
    transferred_at_epoch: 1788357603,
    updated_at_epoch: 1788357603,
  }));
  const claimPath = `${devicePath}/seedling/transfer_claims/${id}`;
  await assertSucceeds(set(ref(owner, claimPath), {
    batch_id: id,
    zone_id: "zone-001",
    season_id: seasonId,
  }));
  await assertFails(update(ref(owner, claimPath), {
    zone_id: "zone-002",
  }));
  await assertSucceeds(set(
    ref(owner, `${devicePath}/garden_journal/seasons/${seasonId}`),
    {
      season_id: seasonId,
      zone_id: "zone-001",
      status: "ACTIVE",
      source_seedling_batch_id: id,
    },
  ));
  await assertSucceeds(update(ref(owner, devicePath), {
    [`seedling/batches/${id}/status`]: "ACTIVE",
    [`seedling/batches/${id}/archive_reason`]: null,
    [`seedling/batches/${id}/archived_at_epoch`]: 0,
    [`seedling/batches/${id}/transferred_season_id`]: null,
    [`seedling/batches/${id}/transferred_zone_id`]: null,
    [`seedling/batches/${id}/transferred_at_epoch`]: null,
    [`seedling/batches/${id}/updated_at_epoch`]: 1788357604,
    [`seedling/transfer_claims/${id}`]: null,
    [`garden_journal/seasons/${seasonId}`]: null,
  }));

  const unknown = validSeedlingBatch("batch-secret");
  unknown.api_key = "must-not-be-accepted";
  await assertFails(set(ref(owner,
    `devices/${DEVICE_ID}/seedling/batches/${unknown.batch_id}`), unknown));
  await assertFails(set(ref(owner,
    `devices/${DEVICE_ID}/seedling/batches/batch-invalid`),
    validSeedlingBatch("batch-invalid", { healthy_count: 101 })));
});

test("sensor and AI seedling snapshots are read-only to Android", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const nodePath = `devices/${DEVICE_ID}/seedling/nodes/seedling-001`;
  await assertFails(set(ref(owner, nodePath), {
    latest: { soil_moisture_pct: 50 },
    recommendation: { title: "forged" },
  }));

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), nodePath), {
      latest: {
        node_id: "seedling-001",
        soil_moisture_pct: 58,
        received_at_epoch: 1788271200,
        online: true,
      },
      recommendation: {
        score: 100,
        severity: "GOOD",
        advisory_only: true,
      },
    });
  });
  const snapshot = await assertSucceeds(get(ref(owner, nodePath)));
  await assertFails(update(ref(owner, `${nodePath}/recommendation`), { score: 0 }));
  if (snapshot.val().recommendation.score !== 100) {
    throw new Error("Backend recommendation was not readable.");
  }
});


test("approved family may submit feedback but cannot read the private inbox", async () => {
  const owner = authenticatedDatabase(OWNER_UID);
  const family = unclaimedDatabase(FAMILY_UID);
  const id = "123e4567-e89b-12d3-a456-426614174099";
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `device_access/${DEVICE_ID}/${FAMILY_UID}`), { approved: true });
  });
  const path = `feedback_devices/${DEVICE_ID}/user_feedback/${id}`;
  await assertSucceeds(set(ref(family, path), validFeedback(id, FAMILY_UID)));
  await assertFails(get(ref(family, path)));
  await assertFails(get(ref(family, `feedback_devices/${DEVICE_ID}`)));
  await assertSucceeds(get(ref(owner, path)));
});

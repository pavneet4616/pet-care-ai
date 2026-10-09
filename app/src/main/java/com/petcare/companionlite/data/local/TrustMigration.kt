package com.petcare.companionlite.data.local

/** SQL is shared with a real SQLite regression test. Never drop dependent care/chat records. */
object TrustMigration {
    private val children = listOf("activities", "reminders", "health_logs", "water_logs", "notes", "expenses", "chat_sessions", "chat_messages", "chat_drafts")
    val statements: List<String> = buildList {
        // Dropping the parent invokes CASCADE even with deferred foreign keys. Snapshot in SQLite,
        // restore parents before children, then discard the snapshots inside the migration transaction.
        children.forEach { add("CREATE TEMP TABLE saved_$it AS SELECT * FROM $it") }
        add("CREATE TABLE pets_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, breed TEXT, weightKg REAL, ageYears INTEGER, photoUri TEXT, birthDate TEXT)")
        add("INSERT INTO pets_new (id,name,type,breed,weightKg,ageYears,photoUri) SELECT id,name,type,breed,CASE WHEN weightKg > 0 THEN weightKg ELSE NULL END,CASE WHEN ageYears > 0 THEN ageYears ELSE NULL END,photoUri FROM pets")
        add("DROP TABLE pets")
        add("ALTER TABLE pets_new RENAME TO pets")
        children.forEach { add("INSERT OR IGNORE INTO $it SELECT * FROM saved_$it") }
        children.forEach { add("DROP TABLE saved_$it") }
        add("ALTER TABLE reminders ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        add("ALTER TABLE activities ADD COLUMN reminderId INTEGER")
        add("ALTER TABLE activities ADD COLUMN scheduledAt INTEGER")
        add("CREATE UNIQUE INDEX index_activities_reminderId_scheduledAt ON activities (reminderId, scheduledAt)")
        add("CREATE INDEX index_activities_timestamp ON activities (timestamp)")
        add("CREATE INDEX index_water_logs_timestamp ON water_logs (timestamp)")
    }
}

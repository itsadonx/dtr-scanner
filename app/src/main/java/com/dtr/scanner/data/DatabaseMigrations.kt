package com.dtr.scanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE scan_records ADD COLUMN scanType TEXT NOT NULL DEFAULT 'QR'",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS face_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                displayName TEXT NOT NULL,
                signature TEXT NOT NULL,
                enrolledAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

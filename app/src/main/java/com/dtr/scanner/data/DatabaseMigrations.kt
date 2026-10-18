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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE face_profiles ADD COLUMN area TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL(
            "ALTER TABLE face_profiles ADD COLUMN businessUnit TEXT NOT NULL DEFAULT ''",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE face_profiles ADD COLUMN templateImagePaths TEXT NOT NULL DEFAULT ''",
        )
    }
}

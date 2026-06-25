package com.ecodrive.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations for EcoDrive.
 *
 * GUIDELINES for future contributors:
 * - Add a new MIGRATION_X_Y object for every database version bump.
 * - Never use ALTER TABLE to rename or drop columns (unsupported on older SQLite).
 *   Instead, CREATE new table → copy data → DROP old → rename new.
 * - Reference the entity DDL in the Room-generated schema JSON files
 *   (enable exportSchema = true in EcoDriveDatabase).
 */
object EcoDriveMigrations {

    /**
     * Bootstrap migration from any version ≤ 6 to version 7.
     * Safely adds tables and columns introduced between v1 and v7
     * using "CREATE TABLE IF NOT EXISTS" and "ADD COLUMN IF NOT EXISTS" patterns
     * so no existing data is lost.
     */
    val MIGRATION_1_7 = object : Migration(1, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // --- Anomalies table (added in v5) ---
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `anomalies` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `tripId` INTEGER NOT NULL,
                    `timestampEpochMs` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `aiDiagnosis` TEXT,
                    FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // --- AI Insights table (added in v4) ---
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_insights` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `tripId` INTEGER,
                    `timestamp` INTEGER NOT NULL,
                    `category` TEXT NOT NULL,
                    `insight` TEXT NOT NULL,
                    `actionable` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )

            // --- Challenges table (added in v6) ---
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `challenges` (
                    `id` TEXT PRIMARY KEY NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `targetValue` REAL NOT NULL,
                    `currentValue` REAL NOT NULL DEFAULT 0.0,
                    `isCompleted` INTEGER NOT NULL DEFAULT 0,
                    `expiresAt` INTEGER,
                    `rewardPoints` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            // --- Badges table (added in v6) ---
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `badges` (
                    `id` TEXT PRIMARY KEY NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `iconName` TEXT NOT NULL,
                    `earnedAt` INTEGER NOT NULL,
                    `category` TEXT NOT NULL
                )
                """.trimIndent()
            )

            // --- isDefault column on vehicles (added in v7) ---
            // SQLite does not support "ADD COLUMN IF NOT EXISTS" pre-3.35,
            // so we use a try/catch approach via a pragma check.
            val cursor = db.query("PRAGMA table_info(vehicles)")
            var hasIsDefault = false
            while (cursor.moveToNext()) {
                val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (colName == "isDefault") {
                    hasIsDefault = true
                    break
                }
            }
            cursor.close()
            if (!hasIsDefault) {
                db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    /**
     * Incremental migration from v6 → v7.
     * Only adds the `isDefault` column to the vehicles table.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

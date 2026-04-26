# Database Migration Safety Strategy

This document explains the migration safety strategy used in Otaku Reader to prevent data loss in production while maintaining developer convenience.

## Overview

Otaku Reader uses a **two-tier migration safety approach**:

1. **Production Builds**: Strict migration enforcement - crashes if migration is missing
2. **Debug Builds**: Permissive fallback - allows destructive migration for developer convenience

This approach is implemented using `BuildConfig.DEBUG` to gate destructive migration behavior.

## Implementation

### Core Database Module

**File**: `core/database/src/main/java/app/otakureader/core/database/di/DatabaseModule.kt`

```kotlin
val builder = Room.databaseBuilder(
    context,
    OtakuReaderDatabase::class.java,
    OtakuReaderDatabase.DATABASE_NAME
)
    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

// Only allow destructive migration in debug builds to avoid silently wiping
// user data (including notes) in production if a migration is missing.
if (BuildConfig.DEBUG) {
    builder.fallbackToDestructiveMigration(dropAllTables = true)
}
return builder.build()
```

### Extension Database Module

**File**: `core/extension/src/main/java/app/otakureader/core/extension/di/ExtensionModule.kt`

```kotlin
val builder = Room.databaseBuilder(
    context,
    ExtensionDatabase::class.java,
    "extension_database"
)

// Only allow destructive migration in debug builds to avoid silently wiping
// extension metadata in production if a migration is missing.
if (BuildConfig.DEBUG) {
    builder.fallbackToDestructiveMigration(dropAllTables = true)
}
return builder.build()
```

### BuildConfig Generation

Both modules enable BuildConfig generation in their `build.gradle.kts`:

```kotlin
android {
    namespace = "app.otakureader.core.{database|extension}"

    buildFeatures {
        buildConfig = true
    }
}
```

## How It Works

### Debug Builds (`assembleDebug`)

- `BuildConfig.DEBUG = true`
- `fallbackToDestructiveMigration(dropAllTables = true)` is enabled
- If a migration is missing, Room **drops all tables** and recreates the schema
- **Use case**: Developer convenience during rapid iteration
- **Risk**: Local database is wiped, but acceptable in development

### Release Builds (`assembleRelease`)

- `BuildConfig.DEBUG = false`
- `fallbackToDestructiveMigration()` is **NOT** called
- If a migration is missing, Room **throws an IllegalStateException**
- **Use case**: Production safety - prevents silent data loss
- **Risk**: None - crashes force developers to add missing migrations before release

### Code Optimization

In release builds, R8/ProGuard performs dead code elimination:

```kotlin
// Source code
if (BuildConfig.DEBUG) {  // BuildConfig.DEBUG = false in release
    builder.fallbackToDestructiveMigration(dropAllTables = true)
}

// After R8/ProGuard optimization (release APK)
// (dead code removed entirely)
```

The `if (BuildConfig.DEBUG)` block is completely removed from release APKs, ensuring **zero risk** of destructive migration in production.

## Migration History

### OtakuReaderDatabase (Version 9)

Complete migration chain from v2 → v9:

| Migration | Schema Change | Data Impact | Introduced |
|-----------|--------------|-------------|------------|
| 2 → 3 | Add `reading_history` table | None - new table | Database v3 |
| 3 → 4 | Add `manga.autoDownload` column | None - defaults to 0 | Database v4 |
| 4 → 5 | Add `manga.notes` column | None - defaults to NULL | Database v5 |
| 5 → 6 | Add `manga.notifyNewChapters` column | None - defaults to 1 | Database v6 |
| 6 → 7 | Add index on `chapters.dateFetch` | None - optimization only | Database v7 |
| 7 → 8 | Add per-manga reader settings (7 columns) | None - defaults to NULL | Database v8 |
| 8 → 9 | Add `opds_servers` table | None - new table | Database v9 |

**All migrations are additive** - no destructive changes, no data loss.

### ExtensionDatabase (Version 3)

- **No migrations defined** (version 3 is the current version)
- `exportSchema = false` (extension metadata is cache-like, not critical)
- Destructive migration gating is still applied for consistency

## Testing

### Migration Tests

**File**: `core/database/src/test/java/app/otakureader/core/database/migration/DatabaseMigrationTest.kt`

Comprehensive migration tests using `MigrationTestHelper`:

1. **Individual migration tests**: Each migration v2→3, v3→4, ..., v8→9 is tested
2. **Data preservation**: Validates that existing data survives migration
3. **Schema validation**: Verifies new columns/tables/indexes are created correctly
4. **Complete migration path**: Tests full v2→v9 migration to validate upgrade experience

### BuildConfig Tests

**Files**:
- `core/database/src/test/java/app/otakureader/core/database/BuildConfigTest.kt`
- `core/extension/src/test/java/app/otakureader/core/extension/BuildConfigTest.kt`

Validates:
- BuildConfig.DEBUG exists and is accessible
- In unit tests, DEBUG is false (release-like behavior)
- Package names are correct (prevents wrong BuildConfig import)
- Documents expected behavior across build types

### CI Integration

**File**: `.github/workflows/ci.yml`

```yaml
- name: 🔨 Build
  run: ./gradlew assembleDebug

- name: 🧪 Run Tests
  run: ./gradlew test
```

- All migrations are tested on every PR
- Tests run in release-like mode (BuildConfig.DEBUG = false)
- Validates production safety guarantees

## Security Considerations

### No Secrets in BuildConfig

**Risk**: BuildConfig fields are baked into generated classes and can be decompiled

**Mitigation**:
- No API keys, tokens, or credentials in BuildConfig
- Secrets stored in:
  - EncryptedSharedPreferences (for OAuth tokens, AI API keys)
  - Keystore (for encryption keys)
  - Gradle properties (for build-time secrets, not in BuildConfig)

**Validation**: BuildConfig tests verify no sensitive fields exist

### BuildConfig Package Safety

**Risk**: Wrong BuildConfig import could bypass DEBUG check

**Mitigation**:
- Modules use fully-qualified imports: `app.otakureader.core.{module}.BuildConfig`
- BuildConfig tests validate correct package name
- Gradle `namespace` matches BuildConfig package

## Best Practices

### Adding New Migrations

1. **Increment database version**:
   ```kotlin
   @Database(
       entities = [...],
       version = 10,  // Increment
       exportSchema = true
   )
   ```

2. **Create migration object**:
   ```kotlin
   private val MIGRATION_9_10 = object : Migration(9, 10) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // Prefer additive changes (ADD COLUMN, CREATE TABLE, CREATE INDEX)
           db.execSQL("ALTER TABLE manga ADD COLUMN newColumn TEXT")
       }
   }
   ```

3. **Add migration to builder**:
   ```kotlin
   .addMigrations(MIGRATION_2_3, ..., MIGRATION_9_10)
   ```

4. **Write migration test**:
   ```kotlin
   @Test
   fun migrate9To10_addsNewColumn() {
       helper.createDatabase(TEST_DB_NAME, 9).apply {
           // Insert test data at v9
           close()
       }

       helper.runMigrationsAndValidate(TEST_DB_NAME, 10, true).apply {
           // Verify v10 schema and data preservation
           close()
       }
   }
   ```

5. **Export schema** (automatic on build):
   - Schema JSON written to `core/database/schemas/.../10.json`
   - Commit schema file to git for reference

### Destructive Changes (Avoid!)

If you **must** make a destructive change:

1. **Create staging column**:
   ```sql
   ALTER TABLE manga ADD COLUMN new_column TEXT
   ```

2. **Migrate data**:
   ```sql
   UPDATE manga SET new_column = (transform old_column)
   ```

3. **Remove old column** (NOT SUPPORTED in SQLite):
   - SQLite doesn't support DROP COLUMN (before Android S)
   - Workaround: CREATE new table, copy data, drop old, rename

4. **Consider backward compatibility**:
   - Can older app versions still read the database?
   - Is the change worth breaking old versions?

## Production Readiness Checklist

Before releasing a new database version:

- [ ] All migrations from previous version are defined
- [ ] Migration tests pass (`./gradlew :core:database:test`)
- [ ] Schema file exported and committed
- [ ] BuildConfig.DEBUG is false in release builds
- [ ] No secrets in BuildConfig fields
- [ ] Documentation updated with new migration
- [ ] Tested on device with existing database (manual QA)

## Troubleshooting

### "Migration path not found" in production

**Cause**: Missing migration for version upgrade

**Solution**:
1. Check DatabaseModule - is migration added to `.addMigrations()`?
2. Verify migration version range matches database version jump
3. Run migration tests to validate

### Database wiped in debug build

**Cause**: Missing migration + destructive fallback enabled

**Solution**:
- This is expected behavior in debug builds (BuildConfig.DEBUG = true)
- Add the missing migration to prevent future wipes
- For critical local data, backup/restore from backup feature

### Wrong BuildConfig imported

**Symptoms**: Destructive migration enabled in release build

**Solution**:
1. Verify import: `import app.otakureader.core.{module}.BuildConfig`
2. Run BuildConfigTest to validate package name
3. Check Gradle `namespace` matches expected package

## References

- [Room Migration Guide](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [MigrationTestHelper Documentation](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper)
- [BuildConfig Best Practices](https://developer.android.com/studio/build/gradle-tips#share-custom-fields-and-resource-values-with-your-app-code)
- [Database Persistence Audit](../audits/database-persistence.md)

## Summary

The BuildConfig.DEBUG-gated migration strategy provides:

✅ **Developer convenience**: Debug builds allow schema iteration without manual migration
✅ **Production safety**: Release builds crash on missing migrations (no silent data loss)
✅ **Code optimization**: R8/ProGuard removes DEBUG blocks from release APKs
✅ **Comprehensive testing**: Migration tests validate upgrade paths
✅ **Security**: No secrets in BuildConfig, proper package isolation

This approach has been battle-tested in production manga reader apps (e.g., Komikku, Tachiyomi) and provides the right balance of safety and developer experience.

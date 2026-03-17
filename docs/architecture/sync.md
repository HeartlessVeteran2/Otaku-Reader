# Cloud Sync Architecture

**Implementation Status:** Core Complete, OAuth Integration Pending

This document describes the cloud sync architecture in Otaku Reader. The core sync engine is production-ready with comprehensive testing, but Google Drive OAuth authentication is not yet implemented and is required for end-to-end functionality.

## Overview

The cloud sync architecture enables cross-device synchronization of library data (favorites, categories, read progress) while keeping the system flexible and extensible.

## Design Principles

1. **Provider Abstraction**: `SyncProvider` interface allows multiple cloud storage backends
2. **Lightweight Snapshots**: `SyncSnapshot` contains only essential sync data (smaller than full backups)
3. **Conflict Resolution**: Multiple strategies for handling concurrent modifications
4. **Incremental Sync**: Metadata tracking for version-based conflict detection
5. **Device Tracking**: Each snapshot includes device ID for debugging and conflict resolution

## Architecture Layers

### Domain Layer (`domain/src/main/java/app/otakureader/domain/sync/`)

#### `SyncManager` Interface
Central coordinator for all sync operations. Responsibilities:
- Enable/disable sync for a specific provider
- Trigger manual or automatic sync
- Create and apply sync snapshots
- Manage sync status (idle, syncing, success, error)
- Handle conflict resolution strategies

#### `SyncProvider` Interface
Abstract cloud storage provider. Responsibilities:
- Authenticate with cloud service
- Upload/download sync snapshots
- Query last snapshot timestamp
- Manage cloud storage lifecycle

#### Sync Models (`domain/model/SyncModels.kt`)

**`SyncSnapshot`**: Lightweight sync data structure
- Version tracking for format evolution
- Device identification for conflict resolution
- Timestamp-based modification tracking
- Metadata for incremental sync

**Key differences from `BackupData`**:
- ✅ Excludes user preferences (device-specific)
- ✅ Excludes reading history (too large, can sync separately)
- ✅ Includes `lastModified` timestamps on all entities
- ✅ Includes device ID and sync version
- ✅ Focuses on library state only

**`SyncResult`**: Detailed sync operation statistics
- Counts for additions, updates, deletions
- Conflict count
- Success/error status

### Data Layer (`data/src/main/java/app/otakureader/data/sync/`)

#### `GoogleDriveSyncProvider` (Prototype - OAuth Not Implemented)
Reference implementation using Google Drive REST API v3.

**Implemented Features**:
- ✅ Drive REST API v3 integration
- ✅ Single-file storage in app data folder (hidden from user)
- ✅ JSON serialization of snapshots
- ✅ CRUD operations on Drive files

**Not Implemented (Critical Gap)**:
- ❌ OAuth 2.0 authentication - `GoogleDriveAuthenticator.authenticate()` throws `NotImplementedError`
- ❌ Automatic token refresh
- ❌ Token storage encryption

**Production Requirements**:
- Implement OAuth using Google Sign-In SDK (`com.google.android.gms:play-services-auth`)
- Add automatic token refresh mechanism
- Implement retry logic with exponential backoff
- Add compression for large snapshots (gzip)
- Parse modification timestamps properly
- Handle rate limiting
- Add progress callbacks for large uploads

#### `GoogleDriveAuthenticator`
Manages OAuth 2.0 authentication flow.

**Current state**: Stub implementation - throws `NotImplementedError` at line 80
**Critical Gap**: OAuth flow is not implemented; users cannot authenticate

**Required for production**:
- Integrate Google Sign-In SDK (`com.google.android.gms:play-services-auth`)
- Request `drive.appdata` scope (user can't see/delete sync file)
- Implement token refresh mechanism
- Use EncryptedSharedPreferences for token storage
- Handle re-authentication on token expiry

## Data Flow

### Push Flow
```
Local DB → SyncManager.createSnapshot()
         → SyncSnapshot (JSON)
         → SyncProvider.uploadSnapshot()
         → Google Drive appDataFolder
```

### Pull Flow
```
Google Drive → SyncProvider.downloadSnapshot()
            → SyncSnapshot (JSON)
            → SyncManager.applySnapshot()
            → Conflict Resolution
            → Local DB updates
```

### Full Sync Flow
```
1. Create local snapshot
2. Upload to cloud
3. Download latest cloud snapshot
4. Merge with conflict resolution
5. Apply merged state to local DB
6. Report statistics
```

## Conflict Resolution

Supported strategies:

1. **PREFER_NEWER** (default): Use modification timestamp to pick most recent
2. **PREFER_LOCAL**: Keep local changes, discard remote
3. **PREFER_REMOTE**: Accept all remote changes
4. **MERGE**: Intelligent merge (e.g., union of favorites, max progress)

### Resolution Logic

For each entity type:

**Manga**:
- Compare `lastModified` timestamps
- Merge `favorite` status (OR operation for MERGE strategy)
- Merge `categoryIds` (union for MERGE strategy)
- Prefer most recent `notes` field

**Chapters**:
- Compare `lastModified` timestamps
- Merge `read` status (prefer true)
- Take max of `lastPageRead`
- Merge `bookmark` (prefer true)

**Categories**:
- Match by `id` (stable identifier)
- Compare `lastModified` timestamps
- Prefer newer name and order

## Security Considerations

1. **OAuth Scope**: Use `drive.appdata` not `drive.file`
   - Files in appDataFolder are hidden from user
   - Deleted when app is uninstalled
   - Can't be accessed by other apps

2. **Token Storage**: Use EncryptedSharedPreferences
   - Android Keystore backing
   - Prevent token theft from rooted devices

3. **Data Privacy**:
   - No user preferences synced (locale, theme)
   - No reading history (privacy-sensitive)
   - Only library structure and progress

4. **Validation**:
   - Version checks on snapshot format
   - Hash verification (future: `previousSnapshotHash`)
   - Size limits to prevent abuse

## Size Optimization

Target: Keep snapshots under 1MB for fast sync.

**Techniques**:
1. Exclude reading history (grows unbounded)
2. Exclude preferences (device-specific)
3. Minimal chapter data (URL + read state only)
4. No full chapter metadata (name, scanlator, etc.)
5. Future: gzip compression (typical 10x reduction for JSON)

**Estimates** (for 1000 favorite manga, 10 chapters each):
- Manga: ~500 bytes each = 500KB
- Chapters: ~100 bytes each × 10,000 = 1MB
- Categories: negligible
- **Total**: ~1.5MB uncompressed, ~150KB with gzip

## Future Enhancements

### Phase 1: Complete Core Functionality (Critical)
- [ ] **Google Sign-In SDK integration** - Required for OAuth authentication
- [ ] **Sync Settings UI** - Connect UI to settings screen
- [ ] **Token refresh mechanism** - Automatic token renewal
- [ ] **Error handling improvements** - Retry logic, user-friendly messages

### Phase 2: Advanced Sync
- [ ] Automatic background sync (WorkManager) - ✅ Infrastructure complete, needs OAuth
- [ ] Differential sync (only changed entities since last sync)
- [ ] Three-way merge for complex conflicts
- [ ] Sync history/audit log
- [ ] Multiple device management UI

### Phase 3: Additional Providers
- [ ] Dropbox implementation
- [ ] WebDAV (Nextcloud, ownCloud)
- [ ] Custom backend server
- [ ] Self-hosted solution

### Phase 4: Extended Data
- [ ] Optional reading history sync (with size limits)
- [ ] Tracking list sync
- [ ] Extension list sync
- [ ] Source login credentials (encrypted)

## Testing Strategy

### Unit Tests (Completed ✅)
- [x] Conflict resolution logic - 11 tests in SyncManagerImplTest
- [x] Timestamp comparison
- [x] Merge strategies
- [x] Serialization/deserialization - 11 tests in SyncModelsTest
- [x] Use case tests - 9 tests in SyncUseCasesTest

**Total: 31 tests passing**

### Integration Tests (Pending)
- [ ] Mock Google Drive API responses
- [ ] Full sync flow end-to-end
- [ ] OAuth flow (UI tests)
- [ ] Multi-device simulation

### Manual Testing (Pending OAuth Implementation)
- [ ] Actual Google Drive integration
- [ ] Token refresh
- [ ] Network error handling
- [ ] Large library sync performance

## Implementation Checklist

### Completed ✅
- [x] `SyncManager` interface
- [x] `SyncProvider` interface
- [x] `SyncSnapshot` data structures
- [x] `GoogleDriveSyncProvider` prototype (REST API calls)
- [x] `SyncManagerImpl` implementation
- [x] Sync preferences (enable/disable, provider selection)
- [x] Conflict resolution implementation
- [x] Snapshot creation from DB
- [x] Snapshot application to DB
- [x] Unit tests (31 tests passing)
- [x] Background sync worker (SyncWorker)
- [x] Sync notifications (SyncNotifier)

### Pending ❌
- [ ] `GoogleDriveAuthenticator` OAuth implementation (currently throws `NotImplementedError`)
- [ ] UI for sync settings (MVI events exist, UI not connected)
- [ ] Google Sign-In SDK integration
- [ ] Token refresh mechanism
- [ ] Integration tests
- [ ] Dropbox provider implementation
- [ ] WebDAV provider implementation

## References

- [Google Drive REST API v3](https://developers.google.com/drive/api/v3/reference)
- [OAuth 2.0 for Mobile Apps](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Google Sign-In SDK](https://developers.google.com/identity/sign-in/android/start)
- [AppAuth for Android](https://github.com/openid/AppAuth-Android) (alternative OAuth library)

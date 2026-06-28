# User-as-Technician Refactor

**Status:** Partially landed
**Date:** 2026-05-22

---

> **Implementation status.** The `UserInfo` proto (`core/model/.../proto/userinfo/user_info.proto`) and the
> `CollectionKind.UserInfo` synced kind have landed, so user-profile data now rides the local-first sync path as
> a top-level kind. The legacy `feature/userprofile/` module (`database/`, `sharedassets/`, `userprofilecard/`)
> still exists; full consolidation of the profile/technician identity is ongoing.

---

## Overview

UserProfile and Technician are two separate features that store nearly identical data. UserProfile holds `LicenseInfo` (license type, number, expiration) for the signed-in user. Technician holds `Technician` (name, cert type, cert number, cert expiration) for arbitrary shop personnel. The maintenance log form already synthesises the two by constructing a fake "Me" technician from UserProfile + Firebase display name at query time — a clear sign that the two concepts should be unified.

**Goal:** Eliminate UserProfile as a data concept. The signed-in user is simply a Technician with a well-known, fixed document ID (`"self"`). All credential editing goes through Manage Technicians. The UserProfile screen becomes a minimal read-only view — avatar, display name, credentials — with a link into the technician editor.

---

## Current State

### Data stores
| Concept | Firestore path | Proto |
|---------|---------------|-------|
| User credentials | `users/{uid}/LicenseInfo/main` | `LicenseInfo` (license_type, license_number, expiration_date, expireLimit) |
| Technicians | `users/{uid}/Technician/{random-uuid}` | `Technician` (id, name, cert_type: string, cert_number, cert_expiration) |

`Technician.cert_type` stores the `LicenseType` enum value as a **string name** (e.g. `"AMT"`), which is fragile. `LicenseInfo` encodes the same concept as a proper proto enum.

### Feature modules
- `feature/userprofile/database/` — `UserProfileManager` interface + impl, Koin module
- `feature/userprofile/` — `EditProfileScreen`, `EditProfileViewModel`, `EditProfileUiState`
- `feature/userprofile/userprofilecard/` — `UserProfileCard` composable, `LicenseHelpers`
- `feature/userprofile/sharedassets/` — `ic_anonymous_user` drawable, shared strings
- `feature/technician/datamanager/` — `TechnicianManager` interface + impl, Koin module
- `feature/technician/manage/` — list + edit screens, two ViewModels
- `feature/technician/sharedassets/` — `CertificateInputFields`, `TechnicianPickerSheet`, strings

### Pain points
1. `MaintenanceLogFormViewModel.observeTechnicians()` manually synthesises a "Me" technician from `UserProfileManager` + Firebase display name. This logic is duplicated in `SettingsViewModel` which reads license info for display.
2. `LicenseType` enum lives in `userprofile/license_type.proto`; `Technician.cert_type` stores the same enum as a raw string.
3. Users must visit two separate screens (Edit Profile for credentials, Manage Technicians for others) even though the data shape is identical.
4. `UserProfileCard` is shown in Settings only; a full "Edit Profile" screen exists solely to update data already covered by the technician editor.

---

## Proposed Changes

### 1. Proto changes

#### 1a. Add `CertificateType` enum to technician proto

Replace the stringly-typed `cert_type: string` field with a proper enum, co-located with the Technician message:

```proto
// aircraft/technician.proto
syntax = "proto3";

message Technician {
  string id          = 1;
  string name        = 2;
  CertificateType cert_type     = 3;
  string          cert_number   = 4;
  google.protobuf.Timestamp cert_expiration = 5;
  CertExpireLimit cert_expire_limit = 6;  // NEW — currently missing, forced caller to infer from null expiration
}

enum CertificateType {
  CERTIFICATE_TYPE_NONE      = 0;
  CERTIFICATE_TYPE_REPAIRMAN = 1;
  CERTIFICATE_TYPE_AMT       = 2;
}

enum CertExpireLimit {
  CERT_EXPIRE_LIMIT_UNKNOWN       = 0;
  CERT_EXPIRE_LIMIT_EXPIRES       = 1;
  CERT_EXPIRE_LIMIT_NEVER_EXPIRES = 2;
}
```

Adding `cert_expire_limit` closes the current gap where callers infer "never expires" by checking `cert_expiration == null`, which is ambiguous with "not yet set".

#### 1b. Deprecate userprofile protos

Mark deprecated (Wire's `@Deprecated` option) and stop writing to Firestore:

- `userprofile/license_info.proto` → deprecated
- `userprofile/license_type.proto` → deprecated (replaced by `CertificateType` above)

The Firestore documents at `users/{uid}/LicenseInfo/main` can be left in place; they will simply stop being read or written. No migration is needed.

#### 1c. Remove `CollectionKind.LicenseInfo`

Delete the `CollectionKind.LicenseInfo` entry and its codec registration from `EntityCodecRegistry`. No backward-compatibility concern.

---

### 2. Self-technician convention

The signed-in user's technician record uses **a normally-generated UUID**, identical to any other technician document. The ID is not magic or predictable — it is generated once on bootstrap and stored in a new `UserInfo` document in Firestore so it can be looked up cheaply on every subsequent launch.

```
users/{uid}/Technician/{generated-uuid}   ← the user (indistinguishable at storage level)
users/{uid}/Technician/{generated-uuid}   ← other technicians (unchanged)
users/{uid}/UserInfo/main                 ← stores self_technician_id
```

This avoids any coupling between identity and document ID, and keeps the Technician collection uniform.

---

### 3. UserInfo proto

A new minimal proto stores the pointer to the self-technician:

```proto
// userinfo/user_info.proto
syntax = "proto3";
option java_package = "dev.fanfly.wingslog.core.model.userinfo";
option java_multiple_files = true;

message UserInfo {
  string self_technician_id = 1;
}
```

Stored at `users/{uid}/UserInfo/main` via a new `CollectionKind.UserInfo` entry and `WireCodec(UserInfo.ADAPTER)` in `EntityCodecRegistry`.

`UserInfo` replaces `LicenseInfo` as the only document written under `users/{uid}/` outside the Technician collection.

---

### 4. Self-technician bootstrap

#### 4a. The hydration race condition

A naive bootstrap that reads `UserInfo` from the local `EntityStore` has a critical flaw on restore and re-login. SquawkIt wipes local data on logout; after the next sign-in, hydration (pulling data from Firestore into local storage) runs concurrently with the rest of app initialisation. If bootstrap checks the local store before hydration has pulled `UserInfo`, it sees an empty result, concludes no self-technician exists, and creates a duplicate. Hydration then pulls the old `UserInfo` (pointing to the original technician), leaving two conflicting writes in Firestore and an orphaned technician document. The user's backed-up credentials are silently lost.

#### 4b. Fix: gate bootstrap on sync state

With sync **enabled**, bootstrap must bypass the local `EntityStore` and perform a **direct Firestore document read** for `UserInfo/main` — a single `.get()` call that goes to the server and sees the true state regardless of whether hydration has run.

With sync **disabled**, all data lives only in the local `EntityStore`; there is no Firestore data to restore from and no hydration running concurrently. The local store is the ground truth, so the direct Firestore read is both unnecessary and problematic (it blocks on a network call the user has explicitly opted out of). The local store can be checked directly without any race risk.

```kotlin
// TechnicianManagerImpl.kt (init coroutine)
// Requires SyncPreferences injected alongside firebaseAuth and storeFactory.
firebaseAuth.authStateChanged.collect { user ->
  if (user == null || user.isAnonymous) return@collect
  val scope = EntityScope.userRoot(user.uid)

  val existingSelfId = if (syncPreferences.state.value.cloudSyncEnabled) {
    // Sync on: read Firestore directly to avoid hydration race on restore/re-login
    userInfoDocRef(user.uid).get().await()
      ?.let { UserInfo.ADAPTER.decode(it.getBlobAsBytes(BLOB_FIELD)) }
      ?.self_technician_id
  } else {
    // Sync off: local store is ground truth, no hydration race
    userInfoStore.load("main", scope).firstOrNull()?.self_technician_id
  }

  if (existingSelfId.isNullOrBlank()) {
    val newId = generateRandomId()
    technicianStore.save(
      Technician(
        id = newId,
        name = user.displayName.orEmpty().ifBlank { user.email.orEmpty() },
        cert_type = CertificateType.CERTIFICATE_TYPE_NONE,
        cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
      ),
      scope,
    )
    userInfoStore.save(UserInfo(self_technician_id = newId), "main", scope)
  }
  // If self_technician_id is already set, hydration (when sync is on) will pull both
  // UserInfo and the referenced Technician document naturally — no action needed here.
}
```

Bootstrap runs once per account. After that, `UserInfo` is kept in sync by the normal EntityStore / hydration pipeline.

#### 4d. New dependency: SyncPreferences

`TechnicianManagerImpl` gains `SyncPreferences` as a constructor dependency. This is the same `SyncPreferences` already used by `SyncSettingsViewModel` and `SyncEngine`. The `TechnicianDataManagerModule` Koin binding must be updated to inject it via `get()`.

#### 4c. Dangling pointer edge case

If `UserInfo.self_technician_id` is set but the referenced `Technician` document has been deleted (e.g. a partially failed migration), `observeSelf()` emits `null`. The UI should treat this the same as "no self yet" — surface a prompt to open Manage Technicians and set up a profile. The bootstrap coroutine does not re-run in this case; recovery is manual.

---

### 5. TechnicianManager interface additions

```kotlin
interface TechnicianManager {
  fun observeTechnicians(): Flow<List<Technician>>      // unchanged
  fun loadTechnician(id: String): Flow<Technician?>    // unchanged
  fun observeSelf(): Flow<Technician?>                 // NEW — resolves via UserInfo.self_technician_id
  fun observeSelfId(): Flow<String?>                   // NEW — the raw ID, for UI to identify which row is "self"
  suspend fun updateTechnician(technician: Technician): Result<Boolean>  // unchanged
  suspend fun deleteTechnician(id: String): Result<Boolean>              // unchanged; self cannot be deleted (enforced in UI)
}
```

Implementation of `observeSelf()`:

```kotlin
override fun observeSelf(): Flow<Technician?> =
  observeSelfId().flatMapLatest { id ->
    if (id.isNullOrBlank()) flowOf(null)
    else loadTechnician(id)
  }

override fun observeSelfId(): Flow<String?> =
  userInfoStore.observe("main", scope)
    .map { it?.self_technician_id?.takeIf { id -> id.isNotBlank() } }
```

`observeSelfId()` is exposed separately so `TechnicianListViewModel` can mark which list item is "self" without loading the full object twice.

---

### 5. feature/userprofile changes

#### 5a. Delete

- `feature/userprofile/database/` — entire submodule deleted
  - `UserProfileManager`, `UserProfileManagerImpl`, `UserProfileManagerModule`, `build.gradle.kts`
- `feature/userprofile/` (update submodule) — `EditProfileScreen`, `EditProfileViewModel`, `EditProfileUiState`, `UserProfileModule`, `build.gradle.kts`

The `Screen.EditProfile` nav route and its composable in `AppEntry.kt` are removed.

#### 5b. Rename and slim down

`feature/userprofile/userprofilecard/` is renamed to `feature/userprofile/viewing/` to match the canonical submodule layout. It becomes the **read-only profile view** surfaced in Settings.

The composable is renamed `ProfileCard` (the existing `UserProfileCard`) — it is purely read-only with no edit action. Credential editing is done exclusively through Manage Technicians.

The new `viewing/` module depends only on `:feature:technician:datamanager` (to read the self-Technician) and `:core:ui`. No `UserProfileManager` dependency.

```kotlin
// ProfileCard.kt (replaces UserProfileCard.kt)
@Composable
fun ProfileCard(
  self: Technician?,   // observed from TechnicianManager.observeSelf()
  photoUri: String?,   // still from Firebase Auth — not stored in proto
)
```

The card shows: circular avatar (photo or anonymous icon), display name, cert type + number + expiration. No edit button.

#### 5c. Remove sharedassets strings that duplicate technician/sharedassets

Strings like `license_type_none / repairman / amt` become redundant once `CertificateInputFields` is the single source of truth. Delete from userprofile sharedassets; reference technician sharedassets instead (or consolidate into one place).

---

### 6. feature/technician changes

#### 6a. TechnicianListScreen — self-record UX

The self-record appears first in the list, above other technicians, with a **"You" badge** (a small `SuggestionChip` or tinted label). `TechnicianListViewModel` observes `TechnicianManager.observeSelfId()` alongside the technician list to know which entry is self. The delete action is hidden for the self-record.

Sorting: self first, then others alphabetically by name (current alphabetical sort applies only to the non-self slice).

#### 6b. EditTechnicianScreen — self-record UX

When editing the self-record:
- The screen title is **"My Profile"** (or just "Edit Profile") rather than "Edit Technician"
- A small note explains: "This is how you appear on maintenance logs."
- The name field is editable (it controls how the user is stamped on logs, separate from Firebase display name)
- No delete button is shown

For non-self technicians the screen is unchanged.

#### 6c. Technician proto — cert_type migration

`TechnicianManagerImpl` reads legacy `cert_type: string` and maps it to the new `CertificateType` enum on read (one-way, opportunistic). On write, always uses the typed enum field. Old string field can be ignored after sufficient time.

---

### 7. SettingsViewModel changes

Remove `UserProfileManager` injection. Replace with `TechnicianManager.observeSelf()`:

```kotlin
// Before
userProfileManager.observeLicenseInfo().collect { licenseInfo ->
  _user.value = _user.value.copy(licenseInfo = licenseInfo)
}

// After
technicianManager.observeSelf().collect { self ->
  _user.value = _user.value.copy(self = self)
}
```

`SettingsUiState` drops `licenseInfo: LicenseInfo` and `displayName: String?` as separate fields; the self `Technician?` carries everything needed for `ProfileCard`.

---

### 8. MaintenanceLogFormViewModel changes

The "Me" technician synthesis is removed entirely:

```kotlin
// Before: manually built from UserProfileManager + Firebase display name
val meTechnician = Technician(
  id = ME_TECHNICIAN_ID,
  name = displayName ?: "Me",
  cert_type = licenseInfo?.license_type?.name ?: "",
  cert_number = licenseInfo?.license_number ?: "",
  cert_expiration = licenseInfo?.expiration_date,
)

// After: just read the self-record from TechnicianManager
technicianManager.observeSelf().collect { self ->
  // self is already in the list returned by observeTechnicians()
}
```

`observeTechnicians()` naturally includes the `"self"` document (it queries the whole collection). The ViewModel no longer needs to merge two data sources. `UserProfileManager` dependency is dropped.

The self-technician is presented first in the picker (same ordering as `TechnicianListScreen`).

---

### 9. Modules to delete

| Module | Action |
|--------|--------|
| `feature/userprofile/database/` | **Delete** — replaced by `TechnicianManager.observeSelf()` |
| `feature/userprofile/` (update/viewmodel) | **Delete** — `EditProfileScreen` and `EditProfileViewModel` removed |
| `feature/userprofile/sharedassets/` | **Delete** (after verifying nothing references `ic_anonymous_user` except `ProfileCard`, which can inline it or move it to `userprofile/viewing/`) |

`feature/userprofile/userprofilecard/` → **Renamed** to `feature/userprofile/viewing/` and kept as a thin read-only composable.

---

### 10. Koin / DI changes

| Module | Change |
|--------|--------|
| `userProfileDatabaseModule` | **Removed** from `initKoin.kt` |
| `userProfileModule` (ViewModel) | **Removed** from `initKoin.kt` |
| `SettingsModule` | Remove `UserProfileManager` arg from `SettingsViewModel` binding; add `TechnicianManager` |
| `MaintenanceLogFormModule` | Remove `UserProfileManager` arg |
| `technicianDataManagerModule` | Add self-bootstrap coroutine to `TechnicianManagerImpl.init` |

---

### 11. Navigation changes

| Route | Change |
|-------|--------|
| `Screen.EditProfile` | **Removed** |
| `Screen.ManageTechnicians` | Unchanged |
| `Screen.EditTechnician` | Unchanged; self-record uses `createRoute(SELF_TECHNICIAN_ID)` |

No edit action on the profile card. Users edit their credentials via Settings → Manage Technicians → self-record.

---

### 12. Summary of data flow after refactor

```
Firebase Auth
  → display name / photo URI (read-only, not stored in proto)

Firestore: users/{uid}/UserInfo/main
  → self_technician_id: String
      → TechnicianManager.observeSelfId()
          → TechnicianListViewModel (to badge the self-row and suppress delete)

Firestore: users/{uid}/Technician/{generated-uuid}  ← the user
  → TechnicianManager.observeSelf()  (resolves via self_technician_id)
      → ProfileCard (read-only, in Settings)
      → MaintenanceLogFormViewModel (default selection in picker)
      → SettingsUiState.self: Technician?

Firestore: users/{uid}/Technician/{generated-uuid}  ← other technicians
  → TechnicianManager.observeTechnicians()
      → TechnicianListScreen (self floated to top)
      → TechnicianPickerSheet (in maintenance log form)

EditTechnicianScreen(id = selfId)   ← only write path for own credentials
EditTechnicianScreen(id = {uuid})   ← write path for other technicians
```

---

### 13. Implementation phases

**Phase 1 — Proto + data layer**
1. Add `CertificateType` and `CertExpireLimit` enums to `aircraft/technician.proto`
2. Update `TechnicianManagerImpl` to read/write new typed fields; migrate legacy `cert_type: string` on read
3. Add `userinfo/user_info.proto` with `self_technician_id`; register `CollectionKind.UserInfo` + codec
4. Add `observeSelf()`, `observeSelfId()` to `TechnicianManager`; implement via `UserInfo` lookup
5. Add self-bootstrap logic to `TechnicianManagerImpl.init` (generate UUID, write Technician + UserInfo); inject `SyncPreferences` to gate direct-Firestore vs. local-store check; update `TechnicianDataManagerModule`
6. Delete `LicenseInfo` and `LicenseType` protos; remove `CollectionKind.LicenseInfo` and its codec registration

**Phase 2 — Technician UI updates**
1. Add "You" badge + top-sort for self-record in `TechnicianListScreen`
2. Hide delete button, change title to "My Profile" in `EditTechnicianScreen` for self-record
3. Update `CertificateInputFields` to use `CertificateType` instead of `LicenseType`

**Phase 3 — Drop UserProfileManager**
1. Update `MaintenanceLogFormViewModel` — remove UserProfileManager, use `observeSelf()` for default selection
2. Update `SettingsViewModel` — replace `UserProfileManager` with `TechnicianManager.observeSelf()`
3. Update `SettingsUiState` — replace `licenseInfo` with `self: Technician?`

**Phase 4 — Slim userprofile**
1. Rename `userprofilecard/` → `viewing/`; update `ProfileCard` to take `Technician?` instead of `UserProfileCardData`
2. Wire "Edit" button → `Screen.EditTechnician.createRoute(SELF_TECHNICIAN_ID)`
3. Remove `EditProfileScreen`, `EditProfileViewModel`, `Screen.EditProfile` route
4. Delete `feature/userprofile/database/`, `feature/userprofile/` (update submodule)
5. Remove `userProfileDatabaseModule` and `userProfileModule` from `initKoin.kt`

**Phase 5 — Cleanup**
1. Delete deprecated proto files (or leave annotated if Wire codegen keeps them harmless)
2. Consolidate duplicate strings between userprofile/sharedassets and technician/sharedassets
3. Remove `UserProfileManager` from any remaining build.gradle.kts dependency declarations

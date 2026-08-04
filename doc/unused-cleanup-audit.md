# Unused Cleanup Audit

## Scope

- Scope is source, tests, resources, obsolete local data, obsolete work, and obsolete
  request payload branches. No dependency, module, source-set, or unrelated build
  configuration was changed.
- The cleanup assumes development does not need old installed data, persistent legacy
  work, or historical server payload compatibility. Android API 25-28 compatibility
  code was retained.
- `git diff --name-status` records 243 removed files: 101 source/test files and 142
  resource files. Resource-file removals are 129 drawables, 3 `drawable-nodpi` PNGs,
  8 layouts, and 2 color files. Obsolete entries were also removed from shared values
  files and lint baselines.
- The generated Room schema outputs `data/schemas/.../SyncDatabase/2.json` and
  `data/schemas/.../WordBookDatabase/3.json` are intentional untracked additions and
  should be committed with the database source changes.

## Detection And Root Checks

| Detector | Result | Follow-up |
| --- | --- | --- |
| Global Kotlin/Java/XML reference scan | 0 ordinary zero-reference functions; 0 unclassified zero-reference main types | Hilt, Room, Retrofit, Android component, navigation, Data Binding, XML custom-view, Compose Preview, and test roots were retained. |
| Resource reference scan | No source resource has dynamic name lookup | `getIdentifier` occurs once in `core-ui/.../PhoneOverlayViewport.kt` and resolves Android system dimens only. |
| Android Lint | No `UnusedResources` result and no deleted-resource reference | Full lint has pre-existing `ResourceName` baseline drift; documented below. |
| Release R8 `usage.txt` | No deleted FQNs remain | Old outbox, worker, CPU sprite, picker, reducer, DTO, and marker FQNs were checked. |
| Release R8 `resources.txt` | 149 project declarations marked unreachable | 147 have an external static source/XML reference. The remaining two bottom-navigation styles are reached through `BottomNavigationTheme`, which is used by `module_home_activity_home.xml`. |
| Framework roots | Manifest, Hilt, Room, Retrofit, Data Binding, navigation, WorkManager, reflection checked | No candidate was removed from one of these roots. |

Android Studio's offline inspection launcher could not execute a full project
inspection in this environment. The candidate set therefore uses the static scans,
Lint, compiler, generated Hilt/Room/Data Binding output, R8 usage output, resource
shrink output, and Safe Delete-equivalent reference checks above.

## Candidate Ledger

Every group below was first a candidate group. Its exact file-level deletion range is
available in `git diff --name-status`; paths and evidence are listed here so the
review is reproducible.

### Removed: Legacy Sync And Local Persistence

- **Declaration range:** `data-sync/.../SyncOutbox*`, `DataSyncOutbox*`,
  `LearningOutboxProcessor`, `UnifiedSync*`, old outbox DAO/entity/mappings, related
  tests; `data-wordbook/.../learning/outbox/*`; stale blocked-delete cleanup.
- **Detection and references:** zero application references after the active failed
  sync path was traced. Exact old FQNs are absent from source and final R8 `usage.txt`.
- **Framework check:** no remaining Manifest service, Hilt binding, Room entity/DAO,
  WorkManager registration, or reflection entry references these types.
- **Removal:** old local outbox persistence, retry work, migration paths, and their
  tests were deleted. The current failed-sync/replay path remains.

### Removed: Obsolete Floating Compatibility

- **Declaration range:** `FloatingLegacyStorageKeys`, old MMKV migration/backup
  fields, character catalog/download-state migration, `FloatingDatabase` migration
  1->2, old floating sync DTO fields and mapping, and obsolete floating action path.
- **Detection and references:** static reference search plus release R8 candidates.
- **Framework check:** current `FloatingDockConfig`, `FloatingDockState`, and
  `FloatingDockEdge` were not deleted; they are reached from the active floating
  service and device-preferences flow. Existing API 25+ window behavior remains.
- **Removal:** legacy local compatibility only. The obsolete DTO-specific R8 rules
  were removed from `app/proguard-rules.pro`; WeChat, QQ, and uCrop rules remain.

### Removed: Dead Domain, UI, And Rendering Branches

- **Declaration range:** old practice reducer/actions/plugin/question engine,
  uncalled practice/statistics/use-case wrappers, empty marker objects in core/data
  modules, obsolete wordbook facade/download/onboarding/update contracts, old CPU
  sprite playback (`SpriteAnimationView`, `SpriteFrameProvider`, scheduler, default
  session), old study-mode picker, home calendar pager/grid, feedback tab UI, and
  uncalled helpers/imports/properties.
- **Detection and references:** each group had no non-framework reference after
  reference search. The final scan found no ordinary function or type candidate.
- **Framework check:** the GPU floating sprite session, Compose previews, RecyclerView
  and service callbacks, current navigation fragments, and XML custom Views are kept.
  For example, `SoftShadow*`, `AppendOnlyEditText`, `LockableNestedScrollView`, and
  `ElasticNestedScrollView` are still XML-inflated.
- **Removal:** obsolete code and only-associated tests were deleted. Relevant module
  compilation and final application/test builds pass.
- **Post-cleanup rescan:** the private test fixture
  `CharacterPackLocalStoreTest.persistedStateJson` had exactly one textual occurrence
  (its declaration), so it was removed. `:data:testDebugUnitTest` passes after the
  removal.

### Removed: Resources And Strings

- **Declaration range:** only unreferenced resource files/values from feedback, home,
  learning, onboarding, and wordbook, including calendar/picker/feedback assets,
  legacy practice/home strings, unused dimensions/token values, styles, IDs, layouts,
  and three unused home PNGs.
- **Detection and references:** global `R.*`, XML, navigation, Data Binding, and
  values-reference scans, then release resource shrink output. No application resource
  is dynamically resolved by name.
- **Framework check:** 11 Data Binding layout roots were retained. Navigation layouts,
  manifest resources, XML custom View usage, and active profile/logout bindings were
  retained. The profile logout confirmation strings were deliberately restored after a
  compile check proved the direct Fragment/Data Binding route is active.
- **Removal:** all qualifier/translation variants only associated with deleted targets
  were removed with their base resource. Lint baseline entries were removed only when
  they pointed directly to a deleted resource or class. `feature-floating-review`
  baseline was not edited.

### Retained Candidate Roots

- Hilt modules/bindings, `@AndroidEntryPoint` components, and `@HiltViewModel` types.
- Room databases, DAOs, entities, and type converters discovered by KSP.
- Retrofit service interfaces and Moshi/ViewBinding reflection support.
- Navigation destinations, Data Binding variables/callbacks, WorkManager current
  entries, Android Manifest components, Compose previews, tests, and XML custom views.
- The only `getIdentifier` call uses package `android`, not an application resource.
- API 25-28 compatibility paths and external SDK callback/keep rules.

## Final Validation

| Check | Final result |
| --- | --- |
| `:app:assembleDebug test testDebugUnitTest --continue` | Passed. |
| Exact `assembleDebug test testDebugUnitTest --continue` | Test work completed; the aggregate build fails only at pre-existing `:feature-user:bundleDebugAar` because AGP rejects its three direct local AARs (`fusionauth`, `umeng-common`, `umeng-asms`). Application debug assembly passes. |
| All 20 `verify*` tasks | 18 pass. Existing failures remain in `verifyDataModuleImportBoundaries` (cross-data imports) and `verifyNewArchitectureProjectDependencies` (`core-session`/`core-ui` -> `domain`). No module/dependency configuration was changed. |
| `:app:assembleRelease -Pmemorize.releaseApiBaseUrl=https://cleanup.invalid/` | Passed after final cleanup. R8 only reports the existing third-party `umeng-common` stack-map warning. |
| `lint --continue` | No cleanup-introduced issue or `UnusedResources` report. It reproduces the same pre-existing ResourceName baseline drift as `HEAD`: floating-review 49 errors/10 warnings, learning 17/2, user 5/1, wordbook 13/18. A detached `HEAD` worktree reproduced all four before it was removed. |
| Post-cleanup zero-reference rescan | The one remaining private-function candidate was removed; the final private function/property/type candidate lists are empty. Room DAO methods and Gradle convention plugins reported by broader scans were retained after their generated-code and `gradlePlugin` registrations were verified. |
| Device smoke test | Not run: `adb devices` returned no attached emulator/device. This is the only outstanding manual validation (cold start and main navigation). |

## Final Candidate State

- Normal source candidate list: empty after framework-root classification.
- Resource candidate list: empty after static-reference and Data Binding/style-chain
  validation; release shrink false positives are recorded above rather than deleted.
- Deleted symbol residual scan: no deleted fully-qualified symbol remains.

## Android Resource Deduplication (2026-08-04)

### Scope And Changes

- Rechecked every tracked and newly added `src/*/(res|assets)` resource across all
  source sets. Application resources are not dynamically resolved by name; the only
  `getIdentifier` call remains scoped to Android system dimensions.
- Removed 25 obsolete duplicate files and added 6 canonical shared files, for a net
  reduction of 19 files. This includes the planned 24 removals plus
  `feature_home_v2_task_inset_bg`: after its inner background was mapped to the
  existing surface background, it became an exact duplicate of
  `feature_home_v2_surface_inset_bg`.
- Kept the density-qualified launcher assets intentionally. Only the duplicate
  unqualified `mipmap/ic_launcher.png` was removed.
- Removed 141 duplicate scalar declarations with zero graph and textual references:
  Feedback 8, Floating Review 1, Home 45, Learning 34, Onboarding 25, User 4, and
  Wordbook 24. Referenced equal-value tokens were retained.
- Consolidated seven Audio Loop styles into three settings styles (net minus four),
  resolved the cross-module `string/profile` collision with separate Home and User
  title resources, and removed only the 33 matching deleted-resource lint-baseline
  entries.

### Verification

| Check | Final result |
| --- | --- |
| Resource file hashing | 713 source `res/assets` files, 644 file resources; 0 byte, XML-structure, and PNG-pixel duplicate groups. |
| Cross-module resource names | 2,784 non-ID declarations; 0 duplicate `type/name` conflicts. |
| Duplicate scalar references | 2,108 scalar declarations; all 493 declarations in 188 equal-value groups have a graph reference and a `git grep -w` reference. |
| Deleted-name scan | No old resource or style name remains in Kotlin, Java, or XML. |
| Debug and unit build | `:app:assembleDebug` and `:feature-home:testDebugUnitTest` passed with `-Pksp.incremental=false`; this avoids an existing corrupt incremental KSP cache without deleting local caches. |
| Release resource shrink | `:app:assembleRelease -Pmemorize.releaseApiBaseUrl=https://cleanup.invalid/ -Pksp.incremental=false` passed. Existing `umeng-common` stack-map warnings remain the only R8 warnings. |
| Final APK resource table | All six canonical shared IDs are present; 173 removed IDs are absent (the retained launcher density variants are excluded); Home and User titles resolve to `个人中心` and `个人信息` respectively. |
| Affected lint tasks | No `IconDuplicatesConfig` remains. Floating Review 49 errors/10 warnings, Learning 17/2, User 5/1, and Wordbook 13/18 reproduce their existing counts. Feedback has one unrelated `UseKtx` warning. |

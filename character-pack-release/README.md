# Green Pet character package

Upload these two files to the backend or object storage:

- `green_pet_v1.zip`: character package used by `packageUrl`
- `green_pet_preview.png`: lightweight preview used by `previewUrl`

Only these two binary files are intended for object-storage upload. Do not upload the Android
signing keystore, mapping file, APK, or AAB together with the character resources.

The ZIP is intentionally flat and contains only:

- `manifest.json`
- `sprite.webp`

Package metadata:

- Pack ID: `green_pet`
- Pack version: `1`
- Package size: `1421233` bytes
- SHA-256: `cacdb4558f610053978fcb9ea597119bcaec6e1c9f4ce5c197ace8578c078276`
- Manifest schema version: `1`

Upload through the backend admin API; do not manually build the public catalog:

1. Send a multipart request to `POST /api/admin/character-packs` with:
   `metadata=@green_pet_upload_metadata.json`, `packageFile=@green_pet_v1.zip`,
   and `previewFile=@green_pet_preview.png`.
2. Use the returned release ID to call `POST /api/admin/character-packs/{id}/publish`.
3. Set the first-use default with `POST /api/admin/character-packs/{id}/default`.

The backend owns `GET /api/app/character-packs`. The two catalog JSON files here are reference
examples only; they are not upload requests.

Production requirements:

- Both URLs must be stable HTTPS URLs. Do not use login pages, temporary browser redirects, or HTTP.
- The catalog endpoint itself must also use HTTPS before production rollout. An HTTP catalog can be
  modified in transit together with `packageUrl` and `packageSha256`, so HTTPS asset URLs alone are
  not a sufficient integrity boundary.
- Serve the ZIP as `application/zip` (or `application/octet-stream`) without modifying/repacking it.
- Serve the PNG as `image/png` without image optimization or recompression.
- Keep every published `(packId, packVersion)` immutable. Any byte change requires a higher `packVersion`.
- Before publishing, download the uploaded ZIP once and verify its byte size and SHA-256 against
  `SHA256SUMS.txt`.
- `green_pet` is an ordinary online role, not an Android fallback. The default flag affects only
  users who have never applied a role; set it through the admin endpoint above.
- The catalog response example assumes this release has been set as the current default.

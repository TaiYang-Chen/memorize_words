# Word Book Update Acceptance

Use this checklist when verifying the workbook update flow:

1. Android build succeeds without any legacy vendor-specific Gradle properties.
2. Backend publish succeeds from the current migration baseline.
3. Test account logs in normally on Android.
4. Publish a workbook that generates a new content version.
5. Confirm backend behavior:
   - `word_book_publish_log` inserted
   - `word_book_version` inserted
   - `wordbook_update_action_log` is available when update actions are reported
6. Confirm Android behavior after returning to foreground:
   - app checks for an update candidate successfully
   - in-app update prompt or local notification appears when applicable
   - update can complete successfully
   - local content version matches server
7. Confirm Android behavior after entering the word book page:
   - app checks for an update candidate successfully
   - no removed device registration APIs are called
8. Confirm settings behavior:
   - the word book page only shows current update settings
   - foreground alert and silent update settings still work

## Acceptance Record

- Date:
- Environment:
- Workbook id:
- Workbook title:
- Publish scope:
- New content version:
- Test account:
- Foreground check result:
- Word book page check result:
- Local prompt result:
- Android log excerpt:
- Backend log excerpt:
- Notes:

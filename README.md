# TaskDav

Kotlin Android app for tasks and notes that syncs over CalDAV with your own server (including Radicale). No Google Play Services. Works alongside DAVx⁵ on the same account.

**License:** GNU General Public License v3.0 (GPLv3)

## Features

- Offline-first tasks and notes (Room) with pull/push CalDAV sync (WorkManager)
- Recursive subtasks via `RELATED-TO;RELTYPE=PARENT`
- Collection colors from Apple `calendar-color`
- Due date/time pickers; optional calendar event link with start/end pickers
- Tags on tasks and notes (`CATEGORIES`)
- Notes synced as `VJOURNAL`
- Animated task/note list inserts and removals
- Appearance themes (Forest, Ocean, Sand, Slate, High contrast)
- Export selected calendars as ICS via the share sheet
- Collections list is pruned when calendars are removed on the server
- Bottom tabs: Home / Calendar / Tasks / Notes / Settings; Home is a hub with stats, today, overdue, upcoming, and recent notes
- Categories are nestable VTODO parents (`X-TASKDAV-KIND:CATEGORY`) for grouping tasks
- Settings hub: Account, Collections, Appearance, Export, Privacy & license, Credits

## Requirements

- Android 8.0+ (API 26)
- A CalDAV server with collections advertising `VTODO` (and optionally `VEVENT` / `VJOURNAL`)
- JDK 17+ to build

## Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # or your JDK 17+
export ANDROID_HOME=~/Library/Android/sdk

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Server setup (Radicale example)

Create a collection that supports tasks, events, and notes:

```bash
curl -u USER:PASS -X MKCOL 'https://caldav.example/USER/tasks/' --data \
'<?xml version="1.0" encoding="UTF-8" ?>
<create xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:I="http://apple.com/ns/ical/">
  <set>
    <prop>
      <resourcetype>
        <collection />
        <C:calendar />
      </resourcetype>
      <C:supported-calendar-component-set>
        <C:comp name="VEVENT" />
        <C:comp name="VTODO" />
        <C:comp name="VJOURNAL" />
      </C:supported-calendar-component-set>
      <displayname>Tasks</displayname>
      <I:calendar-color>#2B6A4FFF</I:calendar-color>
    </prop>
  </set>
</create>'
```

In TaskDav: **Settings → Account** → enter base URL (e.g. `https://host:5232/USER/`), username, password → **Save & discover** → enable collections → sync.

## Coexistence with DAVx⁵

- DAVx⁵ can keep syncing calendars (and another tasks app) to the same account.
- TaskDav talks to the server itself; it does not replace DAVx⁵’s task provider.
- Nested tasks use standard parent links so other clients that support subtasks can see the same hierarchy.
- Colors come from the collection’s `calendar-color`; refresh collections after changing color on the server.

## Architecture (short)

| Piece | Role |
|-------|------|
| `caldav/` | OkHttp + dav4jvm auth, PROPFIND/REPORT/PUT/DELETE, ical4j mappers |
| `data/` | Room entities, encrypted account prefs |
| `domain/` | Task tree, repository |
| `sync/` | WorkManager periodic + manual sync |
| `ui/` | Home, Tasks, Notes, editor, settings hub |

## Non-goals

- DAVx⁵ ContentProvider integration
- Recurring-task editing (existing RRULEs may be preserved on rewrite when possible)

## License

TaskDav is licensed under the **GNU General Public License v3.0**. Dependencies retain their own licenses (see in-app Credits).

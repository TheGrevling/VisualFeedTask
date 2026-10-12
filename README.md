# Hacker News Visual AI Feed

Android take-home project in progress. The feed loads Hacker News Top stories,
caches their metadata in Room, and displays saved stories when connectivity fails.
On-device image generation is not implemented yet.

## Stack

- Kotlin, single activity, Jetpack Compose, and Material 3.
- Hilt constructor injection, singleton database/repository/API, and qualified
  injectable IO/Default coroutine dispatchers.
- OkHttp with Gson and suspend Hacker News Top/New IDs and item endpoints.
- Main-safe `HttpClient`/`HttpClientImpl` HTTP GET requests on background threads,
  propagating HTTP/transport failures and cancelling requests with their coroutines.
- Debug-only OkHttp BODY logging in Logcat under `HTTPLOGGER`: URLs, methods,
  status codes, timing, headers, bodies and failures. Credential/cookie headers
  are redacted and long messages are split to avoid Logcat truncation. Release
  builds disable HTTP logging; debug response bodies may still contain user data.
- Room with exported schemas and persistent story metadata/thumbnail file paths.
- Lifecycle-aware state collection and screen-level ViewModel `StateFlow` with
  loading, empty, error/retry and offline states.
- JUnit, coroutine test utilities, Room instrumentation, and Compose UI testing.
- R8 optimization and resource shrinking for release builds.

## Architecture

One app module is intentional for the 4-6 hour scope. Packages separate concerns:

```text
core/di          Injectable coroutine dispatchers
core/network     Main-safe HTTP request execution
data/di          Hilt bindings and providers
data/local       Room database, entities, and DAO
data/model       Models exposed by repositories
data/remote      Hacker News API and nullable remote item DTO
data/repository  Network story loading, persistence and offline fallback
data/thumbnail   On-device thumbnail generation contract
ui/feed          Screen state, ViewModel, lifecycle-aware route, stateless screen
ui/theme         Material theme
```

UI and ViewModels only access the repository, never HTTP clients, Room, Context, or
ML runtimes. `FeedViewModel` calls `StoryRepository.getStories()` on initialization.
The repository calls `HackerNewsApiImpl`, which supplies URLs to `HttpClient.get(url)` and decodes
responses on the injectable IO dispatcher. For example, `getTopStoryIds()` fetches
`https://hacker-news.firebaseio.com/v0/topstories.json` and returns its JSON array
as `List<Long>` in API order. `print=pretty` is unnecessary for JSON decoding.
`HttpClientImpl` prepares requests on IO and uses cancellable asynchronous OkHttp
calls, reading and closing response bodies on OkHttp's background threads.
Failures propagate to callers for UI/retry handling rather than
returning success-shaped defaults. No fake generator is bound or presented as AI.

The repository fetches the Top ID list once and requests item details in batches
of 20 IDs, with at most eight concurrent requests within each batch. Each result
includes a `StoryPageKey` containing the ID snapshot and next offset. It filters
missing, deleted, dead and non-story items, preserves API order, and persists
metadata before returning it. Existing thumbnail paths are retained when titles
have not changed.

When the last loaded story becomes visible, `FeedScreen` sends
`FeedIntent.ReachedBottom` through `onIntent` to the ViewModel. It requests the
next page and appends the results without replacing existing cards or resetting
scroll position. Duplicate in-flight requests are suppressed, fetching stops at
the end, and fully filtered empty batches are skipped to keep pagination moving.
Page errors retain the loaded stories and cursor; `FeedIntent.Retry` retries the
same page rather than skipping it.

A transport failure on the first page falls back to saved stories, also in
batches of 20, and sets an offline flag. Later online pages can fall back to
matching cached items only if the entire requested batch is saved; otherwise
the feed displays a load-more error with Retry. Without any saved stories, an
initial failure becomes an error with Retry. HTTP errors, invalid JSON and
cancellation are not treated as offline success. Offline cache order is
newest-first, not the last fetched Top ranking; cache entries from earlier
fetches are retained.

The ML threading requirement remains part of the real `ThumbnailGenerator`
implementation: use the injectable Default dispatcher for CPU-bound inference
and IO for model loading and image file writes. Do not run inference through the
HTTP client.

## Build

Open in Android Studio with Android SDK 37 installed and use the checked-in
Gradle wrapper. The template's Gradle daemon toolchain configuration selects JDK 25.
Minimum Android version is API 24.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # Requires an emulator or device
./gradlew :app:assembleRelease
```

Commit generated `app/schemas/` files when changing the database, increment the
database version, and add migrations; do not use destructive migration fallbacks.

## Remaining assignment work

- Add refresh for a loaded feed. Persist Top ranking
  if offline feeds must retain that order rather than showing newest saved stories.
- Select and integrate a real on-device text-to-image model, including licensing,
  model provisioning, device requirements and failure handling. Implement
  `ThumbnailGenerator` with serialized/background inference and durable image
  files in app-private storage (not disposable cache storage).
- Persist generated paths before exposing them to the UI. Define generation
  progress and retry behavior and verify offline app restarts on a device.
- Add images and submission time to the feed cards and an image loader when needed.
- Expand fake-based repository/ViewModel tests and UI regression coverage as those
  behaviors land. Add Navigation 3 only if there is more than one screen.
- Add Macrobenchmark and generate real Baseline/Startup Profiles once the actual
  feed and inference flows exist; fabricated profiles would not measure the app.


All items have some of the following properties, with required properties in bold:

| Field       | Description                                                                        |
|-------------|------------------------------------------------------------------------------------|
| **id**      | The item's unique id.                                                              |
| deleted     | `true` if the item is deleted.                                                     |
| type        | The type of item. One of "job", "story", "comment", "poll", or "pollopt".          |
| by          | The username of the item's author.                                                 |
| time        | Creation date of the item, in [Unix Time](http://en.wikipedia.org/wiki/Unix_time). |
| text        | The comment, story or poll text. HTML.                                             |
| dead        | `true` if the item is dead.                                                        |
| parent      | The comment's parent: either another comment or the relevant story.                |
| poll        | The pollopt's associated poll.                                                     |
| kids        | The ids of the item's comments, in ranked display order.                           |
| url         | The URL of the story.                                                              |
| score       | The story's score, or the votes for a pollopt.                                     |
| title       | The title of the story, poll or job. HTML.                                         |
| parts       | A list of related pollopts, in display order.                                      |
| descendants | In the case of stories or polls, the total comment count.                          |

Two types of requests:
example ParentEntry:
```javascript

{
"by" : "dhouston",
"descendants" : 71,
"id" : 8863,
"kids" : [ 9224, 8917, 8884, 8887, 8952, 8869, 8873, 8958, 8940, 8908, 9005, 9671, 9067, 9055, 8865, 8881, 8872, 8955, 10403, 8903, 8928, 9125, 8998, 8901, 8902, 8907, 8894, 8870, 8878, 8980, 8934, 8943, 8876 ],
"score" : 104,
"time" : 1175714200,
"title" : "My YC app: Dropbox - Throw away your USB drive",
"type" : "story",
"url" : "http://www.getdropbox.com/u/2/screencast.html"
}
```


example ChildEntry:
```javascript

{
"by" : "BrandonM",
"id" : 9224,
"kids" : [ 9272 ],
"parent" : 8863,
"text" : "I have a few qualms with this app:<p>1. For a Linux user, you can already build such a system yourself quite trivially by getting an FTP account, mounting it locally with curlftpfs, and then using SVN or CVS on the mounted filesystem.  From Windows or Mac, this FTP account could be accessed through built-in software.<p>2. It doesn't actually replace a USB drive.  Most people I know e-mail files to themselves or host them somewhere online to be able to perform presentations, but they still carry a USB drive in case there are connectivity problems.  This does not solve the connectivity issue.<p>3. It does not seem very \"viral\" or income-generating.  I know this is premature at this point, but without charging users for the service, is it reasonable to expect to make money off of this?",
"time" : 1175786214,
"type" : "comment"
}
```

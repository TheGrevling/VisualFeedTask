# Hacker News Visual AI Feed

Android take-home project foundation. This is not yet the completed assignment:
the app displays a setup screen backed by a local Room stream.

## Stack

- Kotlin, single activity, Jetpack Compose, and Material 3.
- Hilt constructor injection, singleton database/repository/API, and qualified
  injectable IO/Default coroutine dispatchers.
- Retrofit with Gson and suspend Hacker News Top/New IDs and item endpoints.
- Room with exported schemas and persistent story metadata/thumbnail file paths.
- Lifecycle-aware state collection and screen-level ViewModel `StateFlow` using
  `SharingStarted.WhileSubscribed(5_000)`.
- JUnit, coroutine test utilities, Room instrumentation, and Compose UI testing.
- R8 optimization and resource shrinking for release builds.

## Architecture

One app module is intentional for the 4-6 hour scope. Packages separate concerns:

```text
core/di          Injectable coroutine dispatchers
data/di          Hilt bindings and providers
data/local       Room database, entities, and DAO
data/model       Models exposed by repositories
data/remote      Hacker News API and nullable remote item DTO
data/repository  Local single source of truth exposed as Flow
data/thumbnail   On-device thumbnail generation contract
ui/feed          Screen state, ViewModel, lifecycle-aware route, stateless screen
ui/theme         Material theme
```

UI and ViewModels only access the repository, never Retrofit, Room, Context, or
ML runtimes. Room exposes cached stories ordered newest-first; this is **not**
Hacker News Top ranking. The API is provided by Hilt but is deliberately not yet
called. No fake generator is bound or presented as AI.

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

- Add repository refresh/load-more actions, pagination state and retries. Preserve
  remote ID order for a Top feed, and filter missing/deleted/dead/non-story items.
- Select and integrate a real on-device text-to-image model, including licensing,
  model provisioning, device requirements and failure handling. Implement
  `ThumbnailGenerator` with serialized/background inference and durable image
  files in app-private storage (not disposable cache storage).
- Persist fetched stories and generated paths before exposing them to the UI.
  Define generation progress and retry behavior and verify offline app restarts.
- Build paginated image cards with title, author, score and submission time, plus
  loading, empty, error and offline states. Add an image loader when needed.
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

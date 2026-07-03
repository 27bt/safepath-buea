# SafePath Buea

A voice-first Android app that helps visually impaired pedestrians navigate
streets in Buea, Cameroon. Every phone running the app acts as a sensor
node: it detects obstacles via the camera, lets users report and hear about
street hazards, and shares that data with nearby users through Firebase.

The entire app is built to be operated by ear and by touch. There is no
camera preview, no screen deeper than one tap from Home, and every element
is TalkBack-accessible.

## What it does

- **Continuous obstacle detection** - CameraX samples the back camera at
  roughly 1 frame/second and runs an on-device ML Kit object detector.
  Objects that are large and centered in frame are spoken as urgent
  "obstacle ahead" alerts; smaller or off-center ones are lower priority.
- **Voice commands** - hold anywhere on the Home screen and say one of:
  "what's ahead", "where am I", "report hazard", "nearby hazards",
  "repeat", "help", "stop", "resume", "call for help".
- **Hazard reporting** - speaks the 8 fixed hazard types (pothole, open
  gutter, steep slope, construction, blocked path, traffic, crowd, other),
  attaches GPS location, and writes it to Firestore.
- **Nearby hazards** - runs a geohash radius query against Firestore and
  reads results aloud closest-first, with distance, relative direction
  (based on device heading vs. bearing to the hazard), and how many people
  have confirmed it.
- **Offline support** - camera and TTS keep working with no connectivity;
  hazard reports queue locally (Room) and sync automatically on reconnect.
- **Settings** - language (English/French), speech rate, alert radius,
  auto-scan toggle, and emergency contact, all persisted via DataStore.

## Tech stack

Kotlin, Jetpack Compose, CameraX, Google ML Kit Object Detection
(on-device), Android `SpeechRecognizer` + `TextToSpeech`,
`FusedLocationProviderClient`, Firebase (Firestore + Anonymous Auth),
`geofire-common` for geohash radius queries, Room + DataStore for offline
persistence. Reverse geocoding tries OpenStreetMap's Nominatim first (much
better neighborhood coverage for Buea than Google's geocoder), falling back
to Android's `Geocoder` and a small local neighborhood table.

Two Cloud Functions (Node.js, in `functions/`) run server-side:
`onHazardCreated` merges duplicate reports of the same hazard type within
20 meters, and `expireStaleHazards` marks anything unconfirmed for 48 hours
as expired.

## Project structure

```
app/src/main/java/com/safepathbuea/app/
  ui/screens/     Home, Report Hazard, Nearby Hazards, Settings
  ui/navigation/  flat nav graph - every screen returns to Home
  vision/         CameraX + ML Kit detection pipeline
  alert/          priority queue that turns detections/hazards into speech
  speech/         TextToSpeech wrapper
  voice/          SpeechRecognizer wrapper + voice command parsing
  data/           Firestore repository, offline queue (Room), settings (DataStore)
  location/       GPS, compass heading, reverse geocoding
functions/        Cloud Functions (hazard merge + expiry)
firestore.rules   Firestore security rules
```

## Setup

1. **Toolchain**: JDK 17, Android SDK (platform 34, build-tools 34.0.0),
   and the Gradle wrapper (`./gradlew`) - no separate Gradle install needed.
2. **Firebase**: create a project, enable **Anonymous** sign-in under
   Authentication, add an Android app with package name
   `com.safepathbuea.app`, and download `google-services.json` into
   `app/google-services.json` (gitignored - not included in this repo).
3. **Build**: `./gradlew assembleDebug`
4. **Deploy backend** (optional - the app works without this, just without
   automatic duplicate-merging and expiry):
   ```
   cd functions && npm install && cd ..
   firebase deploy --only firestore:rules,firestore:indexes,functions
   ```
   Deploying Cloud Functions requires Google Cloud's Blaze (pay-as-you-go)
   billing plan; Firestore rules/indexes deploy fine on the free Spark plan.

## Known limitations

- ML Kit's built-in Object Detection API only classifies into five broad
  categories (Fashion good, Food, Home good, Place, Plant) - it cannot
  recognize specific hazard types like potholes or open gutters from the
  camera. That's why hazard *type* comes from the voice/manual report flow;
  the camera's job is generic "something's close and in your path" alerting.
  Real automatic recognition of Buea-specific hazards would need a
  custom-trained model and labeled dataset.
- The object detector runs in `SINGLE_IMAGE_MODE` rather than `STREAM_MODE`:
  `STREAM_MODE`'s cross-frame tracking assumes a continuous ~30fps stream,
  which breaks down at the app's deliberately throttled ~1fps sampling.

# ShareBy

A no-nonsense Android app for sharing files and text directly from device A to device B — no internet, no cloud, no accounts. Just two phones near each other and a file that needs to move.

Uses Google's [Nearby Connections API](https://developers.google.com/nearby/connections/overview) under the hood, so it works over Bluetooth and WiFi without either device needing to be on the same network.

---

## ⚠️ Honest disclaimer

This project was **mostly AI-generated**. The code probably looks like shit. There are likely bad patterns, unnecessary complexity, or embarrassingly simple things done the hard way. I genuinely do not care, because the app does what it needs to do: share a file from A to B.

If something bugs you — **PRs and issues are always welcome and appreciated.** Clean it up, fix the bug, refactor the mess. Go for it.

---

## Features

- Share files and text directly to nearby devices
- No internet connection required — uses Bluetooth / WiFi P2P
- Share *from* any app via the standard Android share sheet
- Real-time transfer progress
- Received files saved to local storage
- Set a custom device name so people know who they're connecting to

## Requirements

- Android 8.0+ (API 26)
- Bluetooth and/or WiFi
- Location permission (required by Android for Nearby Connections)

## How it works

1. The **receiver** opens the app and waits (advertising their device)
2. The **sender** opens the app, picks a nearby device, and sends a file or text
3. That's it

## Building

Open in Android Studio and hit Run. You'll need Google Play Services on the device.

```
./gradlew assembleDebug
```

## Contributing

Found a bug? Code making your eyes bleed? Open an issue or send a PR. All contributions welcome.

## License

Do whatever you want with it.

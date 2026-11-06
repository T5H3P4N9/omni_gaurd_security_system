# OMNI_GUARD

**An experimental, modular smart-security and perimeter-monitoring platform built around embedded controllers, sensor telemetry, and an Android command centre.**

> **Project status:** Prototype / active development  
> **Repository documentation:** Living document  
> **Current firmware references:** Arduino House Unit V7.1 and ESP32-C6 Sensor Unit V8.1  
> **Important:** This README distinguishes the firmware that is present in the supplied source files from features described as planned or target architecture. It does not claim that every item in the design specification is already implemented.

---

## Table of contents

- [Overview](#overview)
- [Project goals](#project-goals)
- [System architecture](#system-architecture)
- [Repository structure](#repository-structure)
- [Hardware](#hardware)
- [Firmware features](#firmware-features)
- [Communication interfaces](#communication-interfaces)
- [Getting started](#getting-started)
- [Configuration and secrets](#configuration-and-secrets)
- [API and telemetry contract](#api-and-telemetry-contract)
- [System states and threat levels](#system-states-and-threat-levels)
- [Known integration gaps](#known-integration-gaps)
- [Safety and security](#safety-and-security)
- [Development roadmap](#development-roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

OMNI_GUARD is a modular embedded-security prototype intended to monitor a defined area, detect nearby objects, estimate their distance and direction, and communicate system events to a central interface.

The system is designed around three main components:

1. **Sensor Unit — ESP32-C6:** scans with ultrasonic sensors, manages basic target tracking, estimates threat levels, controls servos, and transmits JSON telemetry over Wi-Fi.
2. **House Unit — Arduino UNO R3:** manages local power, armed/disarmed state, physical buttons, LCD feedback, LEDs, buzzer patterns, and Bluetooth command/status messages.
3. **Command Centre — Android application / backend:** intended to provide the operator dashboard, radar display, event history, voice interface, and coordination between hardware units.

A Flask/Python dashboard and AI-chat backend are also included in the current software prototype. The supplied Flask implementation is a separate integration path from the Android Ktor architecture described in the system design specification. See [Known integration gaps](#known-integration-gaps).

OMNI_GUARD is a development project, not a certified alarm, access-control, or life-safety product.

## Project goals

- Build a modular system that separates sensing, local hardware authority, and user-interface responsibilities.
- Provide responsive local alerts even when the mobile application or network is unavailable.
- Filter noisy ultrasonic measurements and maintain short-lived target tracks.
- Transmit structured telemetry for visualisation and diagnostics.
- Record system events and provide clear operator feedback.
- Create a foundation for future sensor fusion and additional monitoring nodes.

## System architecture

```text
                  Wi-Fi / HTTP POST
  +-----------------------------+       +-----------------------------+
  | ESP32-C6 SENSOR UNIT        | ----> | ANDROID COMMAND CENTRE       |
  |                             |       |                             |
  | 2x ultrasonic sensors       |       | Ktor server (target design)  |
  | Radar scan servo            |       | SecurityRepository           |
  | Target tracking             |       | MainViewModel / StateFlow    |
  | Threat estimation           |       | Jetpack Compose dashboard    |
  | Turret-position servo       |       | Mission log / voice UI       |
  +-----------------------------+       +---------------+-------------+
                                                        |
                                                        | Bluetooth Classic
                                                        | HC-05 / HC-06
                                                        v
                                        +-----------------------------+
                                        | ARDUINO UNO HOUSE UNIT      |
                                        |                             |
                                        | Physical buttons            |
                                        | 16x2 I2C LCD                |
                                        | LEDs and buzzer             |
                                        | Local state machine         |
                                        | Watchdog                    |
                                        +-----------------------------+

  Separate prototype integration:
  Browser dashboard <---- HTTP ----> Flask/Python backend
                                  |
                                  +---- HTTPS ----> OpenAI API
```

### Responsibility boundaries

| Component | Primary responsibility |
|---|---|
| ESP32-C6 Sensor Unit | Sensor acquisition, scan movement, basic target tracking, threat estimation, Wi-Fi telemetry |
| Arduino House Unit | Local power/security state, physical controls, display, LEDs, buzzer, Bluetooth protocol |
| Android Command Centre | Intended central UI, state coordination, command queue, persistent event log, voice interaction |
| Flask prototype | Browser-based dashboard, polling endpoints, in-memory event history, optional AI chat |

The House Unit is intended to retain local feedback and state handling rather than depending entirely on the phone. The Android app is the intended operator command centre in the target architecture.

## Repository structure

The following is a suggested repository layout. Adjust paths to match the actual files in your repository.

```text
OMNI_GUARD/
├── README.md
├── firmware/
│   ├── house_unit/
│   │   └── house_unit_v7_1.ino
│   └── sensor_unit/
│       └── sensor_unit_v8_1.ino
├── Dashboard/
│   └── flask_prototype/
│       ├── app.py
│       ├── requirements.txt
│       └── templates/
├── android/
│   └── ... Android Studio project ...
├── docs/
│   ├── OMNI_GUARD_SDS.md
│   └── wiring/
└── .gitignore
```

Do not commit generated build folders, local environment files, Wi-Fi credentials, API keys, signing keys, or other secrets.

## Hardware

### Current firmware hardware

| Subsystem | Component | Purpose |
|---|---|---|
| Sensor controller | ESP32-C6 development board | Sensor processing and Wi-Fi communication |
| House controller | Arduino UNO R3 | Local control and feedback |
| Distance sensing | 2 × HC-SR04 ultrasonic modules | Distance measurements |
| Scan mechanism | Servo motor | Moves the sensor assembly across its scan range |
| Position mechanism | Servo motor | Positions the demonstration pointer/indicator |
| Bluetooth | HC-05 / HC-06 compatible module | Arduino-to-host serial wireless link |
| Display | 16×2 I2C LCD, address `0x27` in firmware | Local status and distance |
| Indicators | Red LED and green LED | Local state indication |
| Audible feedback | Buzzer | Local alert patterns |
| Controls | Four momentary push-buttons | Power, arm, disarm, silence |

### Arduino UNO pin assignments

| Function | Arduino pin |
|---|---:|
| Bluetooth software-serial RX | D10 |
| Bluetooth software-serial TX | D11 |
| Red LED | D6 |
| Green LED | D7 |
| Buzzer | D8 |
| Power button | D2 |
| Arm button | D3 |
| Disarm button | D4 |
| Silence button | D5 |
| I2C LCD | SDA/SCL (UNO A4/A5) |

The Bluetooth module's TX/RX connections must be crossed appropriately (module TX to Arduino RX, module RX to Arduino TX). Check the module's logic-level requirements; do not assume every HC-05 breakout accepts 5 V logic on RX.

### ESP32-C6 pin assignments

| Function | ESP32-C6 GPIO |
|---|---:|
| Ultrasonic sensor 1 TRIG | GPIO5 |
| Ultrasonic sensor 1 ECHO | GPIO18 |
| Ultrasonic sensor 2 TRIG | GPIO2 |
| Ultrasonic sensor 2 ECHO | GPIO20 |
| Wi-Fi/status LED | GPIO4 |
| Buzzer | GPIO19 |
| Target indicator LED | GPIO22 |
| Scan servo | GPIO13 |
| Position servo | GPIO15 |

**Electrical warning:** HC-SR04 ECHO commonly outputs a 5 V signal. ESP32-C6 GPIOs are not 5 V tolerant. Use a correctly calculated resistor divider or a suitable level shifter on each ECHO line. Power servos from an appropriately rated supply, connect grounds together, and avoid drawing servo current from the ESP32 board's 3.3 V regulator. Confirm the exact pinout and capabilities of your particular ESP32-C6 board before wiring.

## Firmware features

### 1. Arduino House Unit — V7.1

The supplied `house_unit_v7_1.ino` firmware includes:

- Arduino UNO R3 target.
- HC-05/HC-06-compatible Bluetooth serial at 9600 baud.
- Line-based command parsing, including optional `CMD:` prefix.
- Power, arm, disarm, emergency, silence, sound, auto-scan, stop-scan, ping, version, reset, and state commands.
- 16×2 I2C LCD with cached line updates to reduce unnecessary redraws.
- Physical power, arm, disarm, and silence buttons.
- Red/green LED and buzzer feedback.
- Periodic status packets while powered on.
- Two-second AVR watchdog configuration.
- Non-blocking timing for the main LED/buzzer patterns.

The firmware sends startup messages including `HOUSE_UNIT_READY` and `HOUSE_UNIT_ONLINE`. It sends status packets in the `STATUS:...` format.

### 2. ESP32-C6 Sensor Unit — V8.1

The supplied sensor source file is named `sensor_unit_v8_0.ino`, while its internal firmware version is `8.1`. The source header and transmitted version identify it as V8.1.

Implemented in the supplied source:

- Two HC-SR04 ultrasonic inputs.
- Three-sample median selection for each sensor.
- Servo-driven scan between approximately 10° and 170°.
- Basic target association using distance and angle proximity.
- Target IDs, confidence values, last-seen timestamps, and a basic velocity estimate.
- Removal of targets after a timeout.
- Threat estimation from distance thresholds.
- Smoothed servo positioning toward a selected target.
- Wi-Fi connection and periodic reconnection attempts.
- HTTP JSON telemetry to the configured host.
- Local HTTP endpoints for command handling and online status.
- Basic communication counters and device diagnostics.

**Implementation note:** the supplied code currently selects the nearer of the two sensor readings for its main scan/target update. It does not yet implement a fully independent multi-target association pipeline for each sensor, a Kalman filter, or the complete confidence-based sensor-fusion design described in the SDS.

### 3. Android Command Centre — target architecture

The SDS describes an Android application built with:

- Kotlin
- Jetpack Compose
- Hilt
- MVVM
- `MainViewModel` and `StateFlow<SystemStatus>`
- `SecurityRepository`
- Room database for persistent event history
- Ktor server for incoming sensor telemetry
- Bluetooth communication with the House Unit
- Speech recognition and text-to-speech

These are the documented target components. Confirm each feature against the current Android source before describing it as released or fully integrated.

### 4. Flask/Python dashboard — prototype

The supplied Flask implementation includes:

- A browser dashboard with a cyber-style theme.
- System health/status cards.
- Canvas radar visualisation.
- Browser speech recognition and text-to-speech.
- Event history stored in memory (up to 100 events).
- Sensor polling and Bluetooth-command HTTP routes.
- Sensor AI and event-voice toggles.
- An optional OpenAI chat-completions request.

The Flask dashboard's current routes and data model are not the same as the Android Ktor `/sensor_v8` integration contract. Treat it as a prototype until the interfaces are reconciled.

## Communication interfaces

### House Unit Bluetooth serial

- **Transport:** Bluetooth Classic serial profile through an HC-05/HC-06-compatible module.
- **Baud rate:** 9600.
- **Framing:** newline-terminated text commands and responses.
- **Command input:** accepts either `ARM` or `CMD:ARM` style commands.

Examples of commands implemented in the supplied Arduino firmware:

```text
POWER
ARM
DISARM
EMERGENCY
SILENCE
SOUND
AUTO_SCAN
STOP_SCAN
PING
VERSION
RESET
STATE:READY,35
```

Representative responses/events:

```text
HOUSE_UNIT_READY
HOUSE_UNIT_ONLINE
ACK:ARM
ACK:BUTTON_DISARM
PONG
VERSION:7.1
MODEL:Arduino UNO R3
DEVICE:HOUSE_UNIT
STATUS:POWER=1,ARMED=1,MUTED=0,AUTOSCAN=0,STATE=READY,DISTANCE=35,...
EVENT:BUTTON_ARM
```

The exact status packet is generated by the firmware and includes power, armed, muted, auto-scan, state, distance, LED, buzzer, and version fields.

### ESP32-C6 Wi-Fi telemetry

The sensor firmware sends HTTP POST requests to:

```text
http://<ANDROID_HOST>:8000/sensor_v8
```

The destination is configured in the ESP32 source. The supplied Flask code does **not** implement `/sensor_v8`; it implements `/sensor` using query parameters. The Android Ktor server described by the SDS is the intended receiver for `/sensor_v8`.

A representative telemetry structure described by the SDS is:

```json
{
  "type": "SENSOR_UPDATE",
  "version": "8.1",
  "telemetry": {
    "heap": 124000,
    "rssi": -65,
    "threat": 3,
    "state": 2
  },
  "sensor": {
    "dist": 35,
    "angle": 45,
    "turret": 48,
    "target_id": 102
  },
  "targets": [
    {
      "id": 102,
      "d": 35,
      "a": 45,
      "c": 85
    }
  ]
}
```

The supplied ESP32 firmware also adds fields such as `device`, `uptime`, `cpu_mhz`, `mode`, `packets`, `errors`, `s1`, and `s2`. The receiving server should tolerate these additional fields.

### Flask prototype routes

The supplied Flask code defines these routes:

| Method | Route | Purpose |
|---|---|---|
| GET | `/` | Serve the dashboard |
| POST | `/chat` | Send a user message to the AI function |
| GET | `/sensor_data` | Return current in-memory system state |
| GET | `/get_history` | Return recent event history |
| POST | `/toggle_sensor_ai` | Enable/disable sensor event processing |
| POST | `/toggle_event_voice` | Enable/disable event voice announcements |
| POST | `/bluetooth` | Accept a hardware command |
| GET | `/sensor` | Accept sensor distance/state/angle query parameters |

These endpoints describe the Flask prototype only; they are not a substitute for the Android Ktor API.

## Getting started

### Requirements

**Arduino House Unit**

- Arduino IDE 2.x or compatible PlatformIO setup.
- Arduino UNO R3 board support.
- `LiquidCrystal_I2C` library.
- `SoftwareSerial` and AVR watchdog support (provided by the AVR core).

**ESP32-C6 Sensor Unit**

- Arduino IDE 2.x or PlatformIO.
- ESP32 Arduino core with ESP32-C6 board support.
- `ESP32Servo` library.
- `ArduinoJson` library.
- Wi-Fi and HTTPClient libraries supplied by the ESP32 core.

**Flask prototype**

- Python 3.10+ recommended.
- Flask.
- Requests.

### 1. Flash the House Unit

1. Open `house_unit_v7_1.ino` in Arduino IDE.
2. Select the correct Arduino UNO board and port.
3. Install the `LiquidCrystal_I2C` library if it is not already installed.
4. Check the LCD I2C address and wiring.
5. Upload the sketch.
6. Open a serial monitor at 9600 baud if you need to inspect the hardware serial output. Bluetooth itself is handled through `SoftwareSerial` on D10/D11.

### 2. Configure and flash the Sensor Unit

1. Open the ESP32 sensor sketch.
2. Set the Wi-Fi SSID and password using a local, uncommitted configuration file or build-time secrets.
3. Set the Android/Ktor host IP and port.
4. Confirm the ESP32-C6 board selection and GPIO mapping.
5. Wire the ultrasonic ECHO signals through appropriate 5 V-to-3.3 V level conversion.
6. Attach the servos to a suitable external supply with a shared ground.
7. Compile and upload.
8. Use the serial monitor at 115200 baud for startup and Wi-Fi diagnostics.

### 3. Run the Flask prototype

Create and activate a virtual environment, then install dependencies:

```bash
python -m venv .venv
```

Windows PowerShell:

```powershell
.venv\Scripts\Activate.ps1
pip install flask requests
```

Linux/macOS:

```bash
source .venv/bin/activate
pip install flask requests
```

Set the OpenAI API key as an environment variable if AI chat is enabled:

Windows PowerShell:

```powershell
$env:OPENAI_API_KEY = "your-new-key"
```

Linux/macOS:

```bash
export OPENAI_API_KEY="your-new-key"
```

Then start the application:

```bash
python app.py
```

The supplied Flask code runs on port `8000` and binds to `0.0.0.0`. Do not expose this development server directly to the public internet. Use a production WSGI server and appropriate authentication, TLS, and network controls before any real deployment.

## Configuration and secrets

Never hard-code credentials in firmware or source files.

Use local configuration that is excluded from version control. At minimum, externalise:

- Wi-Fi SSID and password.
- OpenAI API key.
- Backend host/IP and port.
- Any Bluetooth pairing PIN or other device credentials.

Add local secret/configuration files to `.gitignore`, for example:

```gitignore
.env
.env.*
secrets.h
secrets.local.h
config.local.h
.venv/
__pycache__/
*.pyc
```

If a credential has ever been committed, pasted into a public repository, or shared in an exposed source file, assume it is compromised: revoke/rotate it and remove it from repository history where appropriate. Merely deleting it from the latest commit is not sufficient.

## API and telemetry contract

The project currently has more than one integration interface. Before connecting all components, agree on one canonical contract and version it.

Recommended integration checklist:

- Define the authoritative receiver for `/sensor_v8`.
- Confirm whether the receiver expects the SDS example schema or the exact extended schema emitted by the current ESP32 firmware.
- Define how Arduino status packets are parsed and mapped into the app's `SystemStatus`.
- Define command acknowledgements, timeouts, retries, and duplicate-command handling.
- Define what happens when the Android app is offline.
- Add authentication and integrity protection before connecting over untrusted networks.
- Document units, enum values, and timestamps for every telemetry field.

## System states and threat levels

### House Unit states

The supplied Arduino firmware uses:

| State | Meaning |
|---|---|
| `OFF` | House Unit power is off |
| `READY` | Powered and ready; armed state is reported separately |
| `WARNING` | Warning indication |
| `INTRUDER` | Intruder-level alert indication |
| `FINAL` | Highest local alert state |

### Sensor Unit threat thresholds

The supplied ESP32 firmware defines these distance thresholds:

| Distance condition | Sensor threat classification |
|---|---|
| `< 20 cm` | `FINAL` |
| `20–39 cm` | `WARNING` |
| `40–59 cm` | `INTRUDER` |
| `≥ 60 cm` | `READY` |

These are prototype thresholds, not validated security classifications. The firmware's numeric enum values and the House Unit's text states are different representations; the Android/backend layer must map them explicitly. The sensor code also uses `CLEAR` when no target is being detected.

## Known integration gaps

The following items should be resolved before calling the complete ecosystem production-ready:

1. **Flask vs Android backend:** the ESP32 posts to `/sensor_v8`, but the supplied Flask app exposes `/sensor`. The SDS describes a Ktor receiver. Choose and document the canonical receiver.
2. **Bluetooth command differences:** the Arduino firmware supports commands such as `EMERGENCY`, `SOUND`, and `AUTO_SCAN`; the Flask `/bluetooth` route handles a smaller and different command set (`POWER`, `ARM`, `DISARM`, `SILENCE`, `READY`, `WARNING`, `INTRUDER`, `FINAL`, `SYSTEM_OFF`).
3. **Acknowledgement handling:** the SDS requires voice confirmation only after a hardware ACK. The Flask prototype currently returns a command receipt without implementing a full Bluetooth ACK lifecycle.
4. **State authority:** the SDS assigns the House Unit authority over power and armed state, while the ESP32 maintains its own mode. A documented reconciliation policy is needed when these states disagree.
5. **Sensor fusion:** the current ESP32 sketch uses two ultrasonic sensors and basic target association. The planned mmWave/ToF fusion and confidence-based degraded mode are future work.
6. **Persistence:** the Flask event history is in-memory and is lost when the process restarts. The SDS describes persistent Room storage in Android.
7. **Reliability claims:** uptime, false-positive, latency, and multi-target performance targets require repeatable test evidence before they can be stated as achieved.
8. **Security:** local HTTP and Bluetooth traffic are not encrypted or authenticated in the supplied prototype. Wi-Fi credentials and API keys must be externalised.

## Safety and security

- This project is an experimental monitoring prototype. Do not rely on it as the only protection for people, property, or critical infrastructure.
- Do not use the system to target, injure, or physically confront people. Any pointer/laser component must be low-power, appropriately enclosed, and never directed at eyes, aircraft, vehicles, or public areas.
- Verify servo travel limits and secure all moving parts before powering the assembly.
- Use suitable power supplies, fuses/current limits where appropriate, and common grounding for separately powered electronics.
- Protect ESP32 GPIOs from 5 V signals.
- Do not expose unauthenticated Flask, Ktor, or ESP32 control endpoints to the public internet.
- Add authentication, authorisation, input validation, rate limiting, TLS, and secure credential storage before deployment.
- Keep an independent manual power-off method available during testing.

## Development roadmap

The roadmap below reflects the direction described in the project design specification. Items are planned unless implemented and verified in source.

| Version / phase | Planned focus |
|---|---|
| V7.x | House Unit command parser, local state machine, LCD, buttons, watchdog, Bluetooth status |
| V8.x | ESP32-C6 dual-ultrasonic scanning, target tracking, servo control, structured Wi-Fi telemetry |
| V9.0 | Sensor fusion with mmWave and time-of-flight sensing, confidence scoring, degraded operation |
| V10.0 | Authentication improvements and early mobile robotics/drone integration research |
| V12.0 | Multi-device sensor networking |
| V15.0 | Fleet management and broader commercialisation research |

### Suggested immediate engineering priorities

1. Revoke exposed credentials and move all secrets out of source code.
2. Choose one backend integration contract and implement `/sensor_v8` consistently.
3. Align command names and state enums across ESP32, Arduino, Android, and Flask.
4. Add explicit command ACK correlation, timeout handling, and safe retry rules.
5. Add persistent event storage and structured diagnostics.
6. Test sensor accuracy, servo limits, Wi-Fi recovery, Bluetooth reliability, and watchdog recovery.
7. Document wiring diagrams and verified board-specific pin compatibility.

## Contributing

Contributions should preserve the modular separation between sensing, local hardware control, and the command centre.

When submitting changes:

- Explain which subsystem is affected.
- Include board and library versions for firmware changes.
- Document protocol changes and update examples.
- Include test steps and observed results.
- Do not include credentials, personal data, or private network details.
- Avoid describing planned functionality as implemented functionality.


## Project Gallery

### Hardware & Physical Demonstrations

![OMNI_GUARD Overview](images/20260710_143320.jpg)

![Hardware Demonstration](images/20260711_113136.jpg)

![Project Demonstration](images/20260716_161938.jpg)

![OMNI_GUARD](images/IMG-20260717-WA0033.jpg)

![OMNI_GUARD](images/IMG-20260717-WA0037.jpg)

![OMNI_GUARD](images/IMG-20260717-WA0035.jpg)

![OMNI_GUARD](images/IMG-20260717-WA0077.jpg)

### Android Application Interface

![Dashboard](images/Screenshot_20261106_095013_OMNI_GUARD.jpg)

![Security Interface](images/Screenshot_20261106_095021_OMNI_GUARD.jpg)

![Radar Interface](images/Screenshot_20261106_095028_OMNI_GUARD.jpg)

![System Interface](images/Screenshot_20261106_095036_OMNI_GUARD.jpg)

### Additional Demonstrations

![OMNI_GUARD](images/20260721_132208.jpg)

## Video Demonstrations

Watch OMNI_GUARD in operation:

- [Hardware Demonstration – July 16](images/20260716_162552.mp4)
- [System Demonstration – July 9](images/20260709_145622.mp4)
- [Radar Demonstration – July 10](images/20260710_143358.mp4)
- [System Testing – July 10](images/20260710_181942.mp4)



## License

No license has been specified yet. Until a license is added to the repository, all rights remain with the copyright holder and reuse should not be assumed to be permitted.

---

**OMNI_GUARD — Modular sensing. Local authority. Unified monitoring.**

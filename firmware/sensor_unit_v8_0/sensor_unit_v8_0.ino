// ==============================================================================
// OMNI_GUARD SENSOR UNIT V8.1 - FINAL PRODUCTION INTEGRATED FIRMWARE
// Target Hardware: ESP32-C6 Dev Board WROOM (GPIO20 Supported)
// Features: Multi-Target Tracking, Voice Sync, Intelligent Radar, Web API
// ==============================================================================

#include <WiFi.h>
#include <HTTPClient.h>
#include <WebServer.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>
#include <vector>
#include <algorithm>
#include <nvs_flash.h>

// ================= CONFIGURATION =================
const char* ssid     = "WIFI ROUTER NAME";
const char* password = "WIFI PASSWORD";
const char* serverIP = "192.168.X.X"; // Android App Ktor Server IP
#define APP_PORT     8000
#define LOCAL_PORT   80     // ESP32 Web Server Port

#define VERSION      "8.1"
#define BUILD        "2026.08.02-PROD"
#define DEVICE_NAME  "OMNI_SENSOR_C6"

// ================= PINS (ESP32-C6 WROOM) =================
#define TRIG1 5
#define ECHO1 18
#define TRIG2 2
#define ECHO2 20  // C6 Native Support

#define LED_STATUS_PIN  4
#define BUZZER_PIN      19
#define LED_TARGET_PIN  22

#define SCANNER_SERVO_PIN 13
#define LASER_SERVO_PIN   15

// ================= SETTINGS =================
#define THRESH_FINAL    20
#define THRESH_WARNING  40
#define THRESH_INTRUDER 60
#define MAX_RANGE       150   // Extended range for C6
#define SCAN_STEP       1
#define SCAN_INT_IDLE   15    // Slow scan when clear
#define SCAN_INT_TRACK  8     // Fast sweep when tracking
#define HEARTBEAT_INT   5000
#define TARGET_TIMEOUT  4000  // Persistence in ms

// ================= DATA STRUCTURES =================
enum SystemMode    { MODE_OFF, MODE_READY, MODE_ARMED, MODE_EMERGENCY };
enum TrackingState { IDLE, SEARCHING, TRACKING, LOCKED, LOST };
enum ThreatLevel   { CLEAR, READY, WARNING, INTRUDER, FINAL };

struct Target {
    int id;
    int distance;
    int angle;
    unsigned long lastSeen;
    float confidence;
    bool active;
    float velocity; // cm/ms estimation
};

// ================= GLOBAL OBJECTS =================
WebServer server(LOCAL_PORT);
Servo scannerServo;
Servo laserServo;

SystemMode globalMode = MODE_READY;
TrackingState globalTrackingState = SEARCHING;
ThreatLevel currentThreat = CLEAR;

std::vector<Target> targetList;
int nextTargetId = 100;
int lockedTargetId = -1;

// System Variables
unsigned long ms = 0;
unsigned long lastScanTime = 0;
unsigned long lastCommsTime = 0;
unsigned long lastHeartbeatTime = 0;
unsigned long lastWifiRetry = 0;
uint32_t restartCount = 0;

int scanAngle = 90;
int scanDir = 1;
float turretPos = 90.0;
int sensor1Dist = 400;
int sensor2Dist = 400;

// Optimized State Change Tracking
int lastReportedDist = 0;
int lastReportedAngle = 0;
ThreatLevel lastReportedThreat = CLEAR;

// Diagnostics
uint32_t packetsSent = 0;
uint32_t packetFailures = 0;
uint32_t wifiReconnects = 0;

// ================= SENSOR MODULE =================

int pingSensor(int trig, int echo) {
    digitalWrite(trig, LOW);
    delayMicroseconds(2);
    digitalWrite(trig, HIGH);
    delayMicroseconds(10);
    digitalWrite(trig, LOW);
    // ESP32-C6 high precision pulseIn
    long duration = pulseIn(echo, HIGH, 20000); // ~3.4m range timeout
    if (duration == 0) return 400;
    return duration * 0.034 / 2;
}

void updateSensors() {
    static int s1_avg[3], s2_avg[3];
    static int idx = 0;

    s1_avg[idx] = pingSensor(TRIG1, ECHO1);
    s2_avg[idx] = pingSensor(TRIG2, ECHO2);
    idx = (idx + 1) % 3;

    // Median of 3 for noise rejection
    int temp1[3], temp2[3];
    memcpy(temp1, s1_avg, 12); memcpy(temp2, s2_avg, 12);
    std::sort(temp1, temp1+3); std::sort(temp2, temp2+3);

    sensor1Dist = temp1[1];
    sensor2Dist = temp2[1];
}

// ================= TRACKING MODULE =================

void manageTargets(int dist, int angle) {
    bool foundExisting = false;

    for (auto &t : targetList) {
        // Spatial association (15 deg, 20 cm)
        if (abs(t.angle - angle) < 15 && abs(t.distance - dist) < 25) {
            float dt = (float)(ms - t.lastSeen);
            if (dt > 0) t.velocity = (float)(dist - t.distance) / dt;

            t.distance = dist;
            t.angle = angle;
            t.lastSeen = ms;
            t.active = true;
            t.confidence = std::min(1.0f, t.confidence + 0.1f);
            foundExisting = true;
            break;
        }
    }

    if (!foundExisting && dist < MAX_RANGE) {
        Target newTarget;
        newTarget.id = nextTargetId++;
        newTarget.distance = dist;
        newTarget.angle = angle;
        newTarget.lastSeen = ms;
        newTarget.active = true;
        newTarget.confidence = 0.4f;
        newTarget.velocity = 0;
        targetList.push_back(newTarget);
    }

    // Dynamic Cleanup
    targetList.erase(std::remove_if(targetList.begin(), targetList.end(),
        [](const Target& t) { return (millis() - t.lastSeen > TARGET_TIMEOUT); }),
        targetList.end());
}

void updateThreatLevel(int dist) {
    if (globalMode == MODE_OFF) { currentThreat = CLEAR; return; }

    if (dist < THRESH_FINAL) currentThreat = FINAL;
    else if (dist < THRESH_WARNING) currentThreat = WARNING;
    else if (dist < THRESH_INTRUDER) currentThreat = INTRUDER;
    else currentThreat = READY;
}

// ================= RADAR & SERVO MODULE =================

void handleRadar() {
    if (globalMode == MODE_OFF) return;

    unsigned long interval = (targetList.empty()) ? SCAN_INT_IDLE : SCAN_INT_TRACK;

    if (ms - lastScanTime >= interval) {
        lastScanTime = ms;

        if (globalTrackingState != LOCKED) {
            scanAngle += scanDir;
            if (scanAngle >= 170 || scanAngle <= 10) scanDir *= -1;
            scannerServo.write(scanAngle);
        }

        updateSensors();
        int bestDist = std::min(sensor1Dist, sensor2Dist);

        if (bestDist < MAX_RANGE) {
            manageTargets(bestDist, scanAngle);
            updateThreatLevel(bestDist);
            globalTrackingState = TRACKING;
        } else {
            if (targetList.empty()) {
                globalTrackingState = SEARCHING;
                currentThreat = CLEAR;
            } else {
                globalTrackingState = LOST;
            }
        }
    }
}

void handleTurret() {
    float targetAngle = 90.0;

    if (!targetList.empty()) {
        // Selection Logic: Closest target with highest confidence
        auto best = std::min_element(targetList.begin(), targetList.end(),
            [](const Target& a, const Target& b) {
                return (a.distance * (2.0 - a.confidence)) < (b.distance * (2.0 - b.confidence));
            });

        // Predictive lead (basic)
        targetAngle = best->angle + (best->velocity * 50.0f);
        lockedTargetId = best->id;
        globalTrackingState = LOCKED;
        digitalWrite(LED_TARGET_PIN, HIGH);
    } else {
        lockedTargetId = -1;
        digitalWrite(LED_TARGET_PIN, LOW);
    }

    // Proportional smoothing (Non-blocking)
    float diff = targetAngle - turretPos;
    float step = (globalTrackingState == LOCKED) ? 0.25f : 0.10f; // Snappier when locked
    turretPos += diff * step;
    laserServo.write(constrain((int)turretPos, 0, 180));
}

// ================= COMMUNICATION MODULE =================

void sendStructuredPacket(const char* type) {
    if (WiFi.status() != WL_CONNECTED) return;

    StaticJsonDocument<2048> doc;
    doc["type"] = type;
    doc["version"] = VERSION;
    doc["device"] = DEVICE_NAME;
    doc["uptime"] = ms / 1000;

    JsonObject telemetry = doc.createNestedObject("telemetry");
    telemetry["heap"] = ESP.getFreeHeap();
    telemetry["rssi"] = WiFi.RSSI();
    telemetry["cpu_mhz"] = getCpuFrequencyMhz();
    telemetry["threat"] = (int)currentThreat;
    telemetry["state"] = (int)globalTrackingState;
    telemetry["mode"] = (int)globalMode;
    telemetry["packets"] = packetsSent;
    telemetry["errors"] = packetFailures;

    JsonObject sensor = doc.createNestedObject("sensor");
    sensor["s1"] = sensor1Dist;
    sensor["s2"] = sensor2Dist;
    sensor["dist"] = std::min(sensor1Dist, sensor2Dist);
    sensor["angle"] = scanAngle;
    sensor["turret"] = (int)turretPos;
    sensor["target_id"] = lockedTargetId;

    JsonArray targets = doc.createNestedArray("targets");
    for (const auto &t : targetList) {
        JsonObject obj = targets.add<JsonObject>();
        obj["id"] = t.id;
        obj["d"] = t.distance;
        obj["a"] = t.angle;
        obj["c"] = (int)(t.confidence * 100);
    }

    String output;
    serializeJson(doc, output);

    WiFiClient client;
    HTTPClient http;
    String url = "http://" + String(serverIP) + ":" + String(APP_PORT) + "/sensor_v8";

    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(1500); // Safety timeout

    int httpCode = http.POST(output);
    if (httpCode > 0) {
        packetsSent++;
        lastCommsTime = ms;
        lastReportedDist = std::min(sensor1Dist, sensor2Dist);
        lastReportedAngle = scanAngle;
        lastReportedThreat = currentThreat;
    } else {
        packetFailures++;
    }
    http.end();
}

// ================= API HANDLERS (VOICE & APP SYNC) =================

void handleCommand() {
    if (!server.hasArg("cmd")) { server.send(400, "text/plain", "Missing cmd"); return; }
    String cmd = server.arg("cmd");
    String response = "ACK:VOICE_";

    if (cmd == "ARM") { globalMode = MODE_ARMED; response += "ARM"; }
    else if (cmd == "DISARM") { globalMode = MODE_READY; response += "DISARM"; }
    else if (cmd == "POWER_OFF") { globalMode = MODE_OFF; response += "POWER"; }
    else if (cmd == "POWER_ON") { globalMode = MODE_READY; response += "POWER"; }
    else if (cmd == "EMERGENCY") { globalMode = MODE_EMERGENCY; currentThreat = FINAL; response += "EMERGENCY"; }
    else if (cmd == "RESET") { response += "RESET"; server.send(200, "text/plain", response); delay(100); ESP.restart(); }
    else { response = "ERROR:UNKNOWN"; }

    server.send(200, "text/plain", response);
    sendStructuredPacket("COMMAND_ACK");
}

void setupServer() {
    server.on("/cmd", HTTP_GET, handleCommand);
    server.on("/status", HTTP_GET, [](){
        server.send(200, "text/plain", "OMNI_SENSOR_ONLINE");
    });
    server.begin();
}

// ================= CORE SYSTEM =================

void handleWifi() {
    if (WiFi.status() != WL_CONNECTED) {
        digitalWrite(LED_STATUS_PIN, ms % 500 < 250);
        if (ms - lastWifiRetry > 10000) {
            Serial.println("WiFi Link Lost. Reconnecting...");
            WiFi.begin(ssid, password);
            lastWifiRetry = ms;
            wifiReconnects++;
        }
    } else {
        digitalWrite(LED_STATUS_PIN, HIGH);
    }
}

void setup() {
    Serial.begin(115200);

    pinMode(TRIG1, OUTPUT); pinMode(ECHO1, INPUT);
    pinMode(TRIG2, OUTPUT); pinMode(ECHO2, INPUT);
    pinMode(LED_STATUS_PIN, OUTPUT); pinMode(LED_TARGET_PIN, OUTPUT);
    pinMode(BUZZER_PIN, OUTPUT);

    scannerServo.attach(SCANNER_SERVO_PIN);
    laserServo.attach(LASER_SERVO_PIN);

    WiFi.setSleep(false); // Higher performance for C6
    WiFi.begin(ssid, password);

    setupServer();
    Serial.println("OMNI_GUARD ESP32-C6 V8.1 INITIALIZED");
}

void loop() {
    ms = millis();

    server.handleClient();
    handleWifi();
    handleRadar();
    handleTurret();

    // Smart Communications: Report on change or interval
    bool distJump = abs(std::min(sensor1Dist, sensor2Dist) - lastReportedDist) > 5;
    bool angleJump = abs(scanAngle - lastReportedAngle) > 10;
    bool threatChanged = currentThreat != lastReportedThreat;

    if (threatChanged || (globalTrackingState == LOCKED && (distJump || angleJump))) {
        if (ms - lastCommsTime > 150) { // Throttling
            sendStructuredPacket("SENSOR_UPDATE");
        }
    }

    if (ms - lastHeartbeatTime >= HEARTBEAT_INT) {
        sendStructuredPacket("HEARTBEAT");
        lastHeartbeatTime = ms;
    }

    // Adaptive Buzzer Driver
    if (globalMode == MODE_ARMED || globalMode == MODE_EMERGENCY) {
        if (currentThreat == FINAL) digitalWrite(BUZZER_PIN, ms % 100 < 50);
        else if (currentThreat == INTRUDER) digitalWrite(BUZZER_PIN, ms % 400 < 50);
        else digitalWrite(BUZZER_PIN, LOW);
    } else {
        digitalWrite(BUZZER_PIN, LOW);
    }

    delay(1);
}

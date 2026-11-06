from flask import Flask, request, render_template_string, jsonify
import requests
import time
import math
from datetime import datetime

app = Flask(__name__)

# ================= CONFIG =================
# OpenAI API Key implementation preserved exactly as requested
OPENAI_API_KEY = "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"

# ================= GLOBAL STATE (OMNI_GUARD V2) =================
# Combined existing states and new system requirements
system_state = {
    "power": "OFF",
    "security": "DISARMED",
    "threat": "CLEAR",
    "bluetooth": "DISCONNECTED",
    "backend": "ONLINE",
    "arduino_cmd": "N/A",
    "last_event_time": "N/A",
    "distance": 0,
    "angle": 0,
    "x": 0,
    "y": 0,
    "last_message": "",
    "alarm_active": False,
    "silence_mode": False,
    "sensor_ai_enabled": True,
    "event_voice_enabled": True,
    "buzzer": False  # [ADDED] Backend-controlled buzzer state for Arduino House Unit
}

# Event history storage (Last 100 events)
event_history = []
MAX_HISTORY = 100

# Cooldown tracking for repeated alerts
last_trigger_time = {
    "INTRUDER": 0,
    "WARNING": 0,
    "FINAL_WARNING": 0
}
COOLDOWN = 6

# ================= UTILS =================
def log_event(event_type, description, threat_level="CLEAR"):
    global event_history
    timestamp = datetime.now().strftime("%H:%M:%S")
    entry = {
        "time": timestamp,
        "event": event_type,
        "description": description,
        "threat": threat_level,
        "distance": system_state["distance"]
    }
    event_history.insert(0, entry)
    if len(event_history) > MAX_HISTORY:
        event_history.pop()

    # Update system state summary
    system_state["last_event_time"] = timestamp
    system_state["threat"] = threat_level
    system_state["last_message"] = description

# ================= UI (OMNI_GUARD CYBER THEME) =================
HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>OMNI_GUARD SYSTEM V2</title>

<style>
:root {
    --primary: #00ffee;
    --bg-dark: #050b12;
    --bg-panel: #111927;
    --accent-yellow: #ffcc00;
    --accent-orange: #ff6600;
    --accent-red: #ff0033;
    --text-dim: #a0aab8;
}

body {
    margin: 0;
    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    background: var(--bg-dark);
    color: var(--primary);
    overflow-x: hidden;
}

/* ANIMATIONS */
@keyframes pulse-red {
    0% { background: var(--bg-dark); }
    50% { background: #440000; }
    100% { background: var(--bg-dark); }
}
.critical-alarm {
    animation: pulse-red 0.8s infinite;
}

.container {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
    gap: 20px;
    padding: 20px;
}

header {
    background: #0a111a;
    padding: 15px 30px;
    border-bottom: 2px solid var(--primary);
    display: flex;
    justify-content: space-between;
    align-items: center;
}

h1, h2 {
    margin: 0;
    text-transform: uppercase;
    letter-spacing: 2px;
}

h1 { font-size: 22px; }
h2 { font-size: 16px; margin-bottom: 15px; color: var(--text-dim); }

.panel {
    background: var(--bg-panel);
    border: 1px solid #00ffee33;
    border-radius: 12px;
    padding: 20px;
    box-shadow: 0 4px 15px rgba(0,0,0,0.5);
}

/* HEALTH & STATUS PANEL */
.status-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}

.status-item {
    background: rgba(0,0,0,0.3);
    padding: 10px;
    border-radius: 8px;
    border-left: 3px solid var(--primary);
}

.status-label { font-size: 10px; color: var(--text-dim); text-transform: uppercase; }
.status-value { font-size: 14px; font-weight: bold; margin-top: 4px; }

/* CHAT AREA */
#chat {
    height: 350px;
    overflow-y: auto;
    margin-bottom: 15px;
    scrollbar-width: thin;
    scrollbar-color: var(--primary) transparent;
}

.msg {
    margin: 10px 0;
    padding: 12px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.4;
}

.user { background: #2a3441; color: white; border-left: 4px solid #fff; }
.ai { background: #00334d; color: var(--primary); border-left: 4px solid var(--primary); }
.sensor-msg { background: #331100; color: #ff9900; border-left: 4px solid #ff9900; }

/* RADAR AREA */
#radar-container {
    position: relative;
    text-align: center;
}
canvas {
    width: 100%;
    max-width: 400px;
    height: auto;
    background: #000;
    border-radius: 50% 50% 0 0;
    border: 1px solid #00ffee22;
}

.radar-data {
    display: flex;
    justify-content: space-around;
    margin-top: 15px;
    font-size: 13px;
}

/* BUTTONS */
button {
    background: transparent;
    border: 1px solid var(--primary);
    color: var(--primary);
    padding: 12px;
    border-radius: 6px;
    cursor: pointer;
    font-weight: bold;
    text-transform: uppercase;
    transition: 0.3s;
    width: 100%;
    margin-bottom: 10px;
}

button:hover {
    background: var(--primary);
    color: #000;
}

/* HISTORY TABLE */
.history-container {
    height: 400px;
    overflow-y: auto;
}
table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
}
th { text-align: left; padding: 8px; border-bottom: 1px solid var(--primary); color: var(--text-dim); }
td { padding: 8px; border-bottom: 1px solid #ffffff11; }

.threat-CLEAR { color: #00ff00; }
.threat-WARNING { color: var(--accent-yellow); }
.threat-INTRUDER { color: var(--accent-orange); }
.threat-FINAL { color: var(--accent-red); font-weight: bold; }

@media (max-width: 900px) {
    .container { grid-template-columns: 1fr; }
}
</style>
</head>

<body id="main-body">

<header>
    <h1>OMNI_GUARD SYSTEM <span style="font-size: 12px; color: var(--text-dim);">V2.0.4</span></h1>
    <div id="sys-clock" style="font-family: monospace;">00:00:00</div>
</header>

<div class="container">

    <!-- SYSTEM HEALTH PANEL -->
    <div class="panel">
        <h2>📊 SYSTEM HEALTH</h2>
        <div class="status-grid">
            <div class="status-item"><div class="status-label">Power</div><div class="status-value" id="val-power">OFF</div></div>
            <div class="status-item"><div class="status-label">Security Mode</div><div class="status-value" id="val-security">DISARMED</div></div>
            <div class="status-item"><div class="status-label">Threat Level</div><div class="status-value" id="val-threat">CLEAR</div></div>
            <div class="status-item"><div class="status-label">Bluetooth</div><div class="status-value" id="val-bluetooth">DISCONNECTED</div></div>
            <div class="status-item"><div class="status-label">Backend</div><div class="status-value" id="val-backend">ONLINE</div></div>
            <div class="status-item"><div class="status-label">Arduino Cmd</div><div class="status-value" id="val-arduino">N/A</div></div>
            <div class="status-item"><div class="status-label">Sensor AI</div><div class="status-value" id="val-sensor-ai">ENABLED</div></div>
            <div class="status-item"><div class="status-label">Voice AI</div><div class="status-value" id="val-voice-ai">ENABLED</div></div>
        </div>

        <div style="margin-top: 20px;">
            <button onclick="toggleSensorAI()" id="btn-sensor-ai">Toggle Sensor AI</button>
            <button onclick="toggleEventVoice()" id="btn-event-voice">Toggle Event Voice</button>
        </div>
    </div>

    <!-- RADAR & TARGETING -->
    <div class="panel">
        <h2>📡 RADAR & TARGETING</h2>
        <div id="radar-container">
            <canvas id="radar-canvas"></canvas>
            <div id="threat-overlay" style="position: absolute; top: 10px; right: 10px; font-weight: bold; font-size: 20px;"></div>
        </div>
        <div class="radar-data">
            <div>DIST: <span id="val-dist">0</span> cm</div>
            <div>ANG: <span id="val-angle">0</span>°</div>
            <div>X: <span id="val-x">0</span></div>
            <div>Y: <span id="val-y">0</span></div>
        </div>
    </div>

    <!-- VOICE AI CHAT -->
    <div class="panel">
        <h2>🎤 OMNI INTERFACE</h2>
        <div id="chat"></div>
        <button onclick="startListening()">ACTIVATE VOICE COMMAND</button>
    </div>

    <!-- EVENT HISTORY -->
    <div class="panel">
        <h2>📜 MISSION LOG</h2>
        <div class="history-container">
            <table>
                <thead>
                    <tr><th>TIME</th><th>EVENT</th><th>THREAT</th><th>DIST</th></tr>
                </thead>
                <tbody id="history-body"></tbody>
            </table>
        </div>
    </div>

</div>

<!-- ALARM AUDIO (Continuous) -->
<audio id="alarm-audio" loop>
    <source src="https://www.soundjay.com/buttons/beep-01a.mp3" type="audio/mpeg">
</audio>

<script>
let recognition;
let sensorAI = true;
let eventVoice = true;
let lastMsg = "";
let alarmActive = false;

const chat = document.getElementById("chat");
const historyBody = document.getElementById("history-body");
const alarm = document.getElementById("alarm-audio");

// ================= CLOCK =================
setInterval(() => {
    document.getElementById("sys-clock").innerText = new Date().toLocaleTimeString();
}, 1000);

// ================= UI UPDATES =================
function addChatMsg(text, cls) {
    let div = document.createElement("div");
    div.className = "msg " + cls;
    div.innerText = text;
    chat.appendChild(div);
    chat.scrollTop = chat.scrollHeight;
}

// ================= SPEECH (OMNI Voice) =================
function speak(text) {
    if(recognition) recognition.stop();
    speechSynthesis.cancel();

    let s = new SpeechSynthesisUtterance(text);
    let voices = speechSynthesis.getVoices();

    // OMNI is a man's voice - look for David or similar
    let maleVoice = voices.find(v =>
        v.name.toLowerCase().includes("david") ||
        v.name.toLowerCase().includes("mark") ||
        v.name.toLowerCase().includes("google uk english male")
    );

    if(maleVoice) s.voice = maleVoice;
    s.pitch = 0.85;
    s.rate = 0.95;

    s.onend = () => { if(recognition) recognition.start(); };
    speechSynthesis.speak(s);
}

// ================= SENSOR POLLING =================
async function pollState() {
    try {
        const res = await fetch("/sensor_data");
        const data = await res.json();
        const historyRes = await fetch("/get_history");
        const history = await historyRes.json();

        // Update Health Panel
        document.getElementById("val-power").innerText = data.power;
        document.getElementById("val-security").innerText = data.security;
        document.getElementById("val-threat").innerText = data.threat;
        document.getElementById("val-bluetooth").innerText = data.bluetooth;
        document.getElementById("val-arduino").innerText = data.arduino_cmd;
        document.getElementById("val-dist").innerText = data.distance;
        document.getElementById("val-angle").innerText = data.angle;
        document.getElementById("val-x").innerText = data.x;
        document.getElementById("val-y").innerText = data.y;
        document.getElementById("val-sensor-ai").innerText = data.sensor_ai_enabled ? "ENABLED" : "DISABLED";
        document.getElementById("val-voice-ai").innerText = data.event_voice_enabled ? "ENABLED" : "DISABLED";

        // Update Radar
        drawRadar(data.distance, data.angle, data.threat);

        // Handle Alarm & Critical UI
        if(data.alarm_active) {
            document.getElementById("main-body").classList.add("critical-alarm");
            document.getElementById("threat-overlay").innerText = "CRITICAL ALERT";
            document.getElementById("threat-overlay").style.color = "red";
            if(alarm.paused) alarm.play().catch(e => {});
        } else {
            document.getElementById("main-body").classList.remove("critical-alarm");
            document.getElementById("threat-overlay").innerText = data.threat !== "CLEAR" ? data.threat : "";
            document.getElementById("threat-overlay").style.color = data.threat === "WARNING" ? "yellow" : "orange";
            alarm.pause();
            alarm.currentTime = 0;
        }

        // Voice Announcement for Events
        if(data.last_message && data.last_message !== lastMsg) {
            lastMsg = data.last_message;
            addChatMsg("SYSTEM: " + data.last_message, "sensor-msg");
            if(data.event_voice_enabled) {
                speak(data.last_message);
            }
        }

        // Update History Table
        historyBody.innerHTML = history.map(h => `
            <tr>
                <td>${h.time}</td>
                <td>${h.event}</td>
                <td class="threat-${h.threat}">${h.threat}</td>
                <td>${h.distance} cm</td>
            </tr>
        `).join("");

    } catch (e) { console.error("Polling error", e); }
}

setInterval(pollState, 500);

// ================= RADAR ENGINE =================
const canvas = document.getElementById("radar-canvas");
const ctx = canvas.getContext("2d");
canvas.width = 400;
canvas.height = 300;
let sweep = 0;

function drawRadar(dist, angle, threat) {
    ctx.clearRect(0,0,400,300);

    // Background rings
    ctx.strokeStyle = "rgba(0, 255, 238, 0.2)";
    for(let r=50; r<=200; r+=50) {
        ctx.beginPath();
        ctx.arc(200, 280, r, Math.PI, 2*Math.PI);
        ctx.stroke();
    }

    // Grid lines
    ctx.beginPath();
    ctx.moveTo(200, 280); ctx.lineTo(200, 80);
    ctx.moveTo(50, 280); ctx.lineTo(350, 280);
    ctx.stroke();

    // Sweep animation
    sweep += 4;
    if(sweep > 180) sweep = 0;
    let sweepRad = sweep * Math.PI / 180;

    ctx.strokeStyle = "rgba(0, 255, 238, 0.5)";
    ctx.beginPath();
    ctx.moveTo(200, 280);
    ctx.lineTo(200 + Math.cos(Math.PI - sweepRad) * 200, 280 - Math.sin(sweepRad) * 200);
    ctx.stroke();

    // Target tracking
    if(dist > 0 && dist < 100) {
        let a = angle * Math.PI / 180;
        let x = 200 - Math.cos(a) * (dist * 2);
        let y = 280 - Math.sin(a) * (dist * 2);

        let color = "#00ff00";
        if(threat === "WARNING") color = "yellow";
        if(threat === "INTRUDER") color = "orange";
        if(threat === "FINAL") color = "red";

        ctx.fillStyle = color;
        ctx.shadowBlur = 15;
        ctx.shadowColor = color;
        ctx.beginPath();
        ctx.arc(x, y, 8, 0, Math.PI*2);
        ctx.fill();
        ctx.shadowBlur = 0;
    }
}

// ================= TOGGLES & VOICE =================
function toggleSensorAI() {
    fetch("/toggle_sensor_ai", {
        method: "POST",
        headers: {"Content-Type":"application/json"},
        body: JSON.stringify({enabled: !sensorAI})
    }).then(() => sensorAI = !sensorAI);
}

function toggleEventVoice() {
    fetch("/toggle_event_voice", {
        method: "POST",
        headers: {"Content-Type":"application/json"},
        body: JSON.stringify({enabled: !eventVoice})
    }).then(() => eventVoice = !eventVoice);
}

function startListening() {
    recognition = new(window.SpeechRecognition || window.webkitSpeechRecognition)();
    recognition.lang = "en-US";
    recognition.continuous = true;
    recognition.onresult = async (e) => {
        let text = e.results[e.results.length-1][0].transcript;
        addChatMsg("USER: " + text, "user");
        let res = await fetch("/chat", {
            method: "POST",
            headers: {"Content-Type":"application/json"},
            body: JSON.stringify({text})
        });
        let data = await res.json();
        addChatMsg("OMNI: " + data.reply, "ai");
        speak(data.reply);
    };
    recognition.start();
}
</script>

</body>
</html>
"""

# ================= ROUTES =================

@app.route("/")
def home():
    return render_template_string(HTML)

@app.route("/chat", methods=["POST"])
def chat():
    user_text = request.json["text"]
    reply = ask_omni(user_text)
    return jsonify({"reply": reply})

@app.route("/sensor_data")
def get_sensor_data():
    return jsonify(system_state)

@app.route("/get_history")
def get_history():
    return jsonify(event_history)

@app.route("/toggle_sensor_ai", methods=["POST"])
def toggle_sensor():
    system_state["sensor_ai_enabled"] = request.json["enabled"]
    return jsonify({"ok": True})

@app.route("/toggle_event_voice", methods=["POST"])
def toggle_voice():
    system_state["event_voice_enabled"] = request.json["enabled"]
    return jsonify({"ok": True})

# ================= ANDROID COMMUNICATION (V2 NEW) =================

@app.route("/bluetooth", methods=["POST"])
def bluetooth_command():
    global system_state
    cmd = request.form.get("command", "").upper()
    if not cmd:
        cmd = request.json.get("command", "").upper() if request.json else ""

    system_state["arduino_cmd"] = cmd
    system_state["bluetooth"] = "CONNECTED"

    msg = ""
    threat = "CLEAR"

    if cmd == "POWER":
        system_state["power"] = "ON"
        msg = "OMNI_GUARD system online."
        log_event("SYSTEM_POWER", "Power sequence initiated.", "CLEAR")

    elif cmd == "ARM":
        system_state["security"] = "ARMED"
        msg = "Security system armed."
        log_event("SECURITY_ARM", "System entered ARMED state.", "CLEAR")

    elif cmd == "DISARM":
        system_state["security"] = "DISARMED"
        system_state["alarm_active"] = False
        # DISARM → buzzer OFF
        system_state["buzzer"] = False
        msg = "Security system disarmed."
        log_event("SECURITY_DISARM", "System disarmed by user.", "CLEAR")

    elif cmd == "SILENCE":
        system_state["silence_mode"] = True
        # SILENCE → buzzer OFF
        system_state["buzzer"] = False
        log_event("SYSTEM_SILENCE", "Audio alerts suppressed.", "CLEAR")

    elif cmd == "READY":
        system_state["threat"] = "CLEAR"
        # READY → buzzer OFF
        system_state["buzzer"] = False
        log_event("STATUS_READY", "System ready for deployment.", "CLEAR")

    elif cmd == "WARNING":
        threat = "WARNING"
        # WARNING → buzzer ON
        system_state["buzzer"] = True
        msg = "Warning. Motion detected."
        log_event("THREAT_DETECTION", "Unidentified motion in proximity.", "WARNING")

    elif cmd == "INTRUDER":
        threat = "INTRUDER"
        # INTRUDER → buzzer ON
        system_state["buzzer"] = True
        msg = "Intruder detected."
        log_event("CRITICAL_INTRUSION", "Unauthorized breach detected.", "INTRUDER")

    elif cmd == "FINAL":
        threat = "FINAL"
        system_state["alarm_active"] = True
        # FINAL → buzzer ON
        system_state["buzzer"] = True
        msg = "Critical security breach detected."
        log_event("SYSTEM_FINAL", "Final defensive protocol engaged.", "FINAL")

    elif cmd == "SYSTEM_OFF":
        system_state["power"] = "OFF"
        system_state["security"] = "DISARMED"
        # POWER OFF → buzzer OFF
        system_state["buzzer"] = False
        log_event("SYSTEM_SHUTDOWN", "System powering down.", "CLEAR")

    if msg:
        system_state["last_message"] = msg
        system_state["threat"] = threat

    return jsonify({"status": "received", "command": cmd})

# ================= SENSOR (LEGACY UPGRADED) =================

@app.route("/sensor")
def sensor_input():
    global system_state

    if not system_state["sensor_ai_enabled"]:
        return "SENSOR_OFF"

    dist = request.args.get("dist", default=0, type=int)
    state = request.args.get("state", default="CLEAR").upper()
    angle = request.args.get("angle", default=0, type=int)

    rad = math.radians(angle)
    system_state.update({
        "distance": dist,
        "angle": angle,
        "x": int(dist * math.cos(rad)),
        "y": int(dist * math.sin(rad))
    })

    now = time.time()
    msg = None
    threat = "CLEAR"

    if state == "INTRUDER" and now - last_trigger_time["INTRUDER"] > COOLDOWN:
        threat = "INTRUDER"
        # INTRUDER → buzzer ON
        system_state["buzzer"] = True
        msg = f"Intruder detected at {dist} cm"
        last_trigger_time["INTRUDER"] = now
        log_event("SENSOR_INTRUDER", msg, "INTRUDER")

    elif state == "WARNING" and now - last_trigger_time["WARNING"] > COOLDOWN:
        threat = "WARNING"
        # WARNING → buzzer ON
        system_state["buzzer"] = True
        msg = "Warning: Target approaching"
        last_trigger_time["WARNING"] = now
        log_event("SENSOR_WARNING", msg, "WARNING")

    elif state == "FINAL_WARNING" and now - last_trigger_time["FINAL_WARNING"] > COOLDOWN:
        threat = "FINAL"
        # FINAL_WARNING → buzzer ON
        system_state["buzzer"] = True
        msg = "Final warning. Leave immediately"
        last_trigger_time["FINAL_WARNING"] = now
        log_event("SENSOR_FINAL", msg, "FINAL")

    if msg:
        system_state["last_message"] = msg
        system_state["threat"] = threat

    return "OK"

# ================= ARDUINO POLLING (NEW) =================

@app.route("/arduino")
def arduino_poll():
    """[ADDED] Endpoint for Arduino House Unit to poll buzzer and system status."""
    return jsonify({
        "buzzer": system_state.get("buzzer", False),
        "threat": system_state.get("threat", "CLEAR"),
        "power": system_state.get("power", "OFF"),
        "security": system_state.get("security", "DISARMED")
    })

# ================= AI ENGINE (OMNI) =================

def ask_omni(text):
    try:
        # Construct the context for OMNI
        history_summary = "\\n".join([f"- {h['time']}: {h['event']} ({h['threat']})" for h in event_history[:5]])

        context = f"""You are OMNI_GUARD SYSTEM, an advanced AI-powered security and monitoring OS.

        CURRENT SYSTEM STATE:
        - Power: {system_state['power']}
        - Security Mode: {system_state['security']}
        - Current Threat: {system_state['threat']}
        - Last Distance: {system_state['distance']} cm
        - Target Coords: X:{system_state['x']} Y:{system_state['y']}

        RECENT MISSION LOG:
        {history_summary}

        Rules:
        - Be concise, intelligent, and professional.
        - Respond like a futuristic OS (similar to JARVIS/HAL).
        - Prioritize security and system status reporting.
        - If the user asks about system state or history, use the data provided above.
        - Speak confidently. You are a man's voice.

        System Name: OMNI_GUARD SYSTEM
        Creator: Tshepang Oliver
        """

        url = "https://api.openai.com/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {OPENAI_API_KEY}",
            "Content-Type": "application/json"
        }
        data = {
            "model": "gpt-4o-mini",
            "messages": [
                {"role": "system", "content": context},
                {"role": "user", "content": text}
            ]
        }

        r = requests.post(url, json=data, headers=headers)
        return r.json()["choices"][0]["message"]["content"]

    except Exception as e:
        return f"System error: {str(e)}"

# ================= START =================
if __name__ == "__main__":
    print("====================================")
    print("   OMNI_GUARD SYSTEM V2 ONLINE      ")
    print("====================================")
    app.run(host="0.0.0.0", port=8000)

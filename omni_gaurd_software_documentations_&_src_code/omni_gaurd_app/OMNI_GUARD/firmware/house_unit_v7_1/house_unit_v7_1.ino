//==============================================================================
// OMNI_GUARD HOUSE UNIT V7.1 - FINAL PRODUCTION FIRMWARE (PATCHED)
// Production Ready - Watchdog Enabled - Fully Non-Blocking
//==============================================================================

#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <SoftwareSerial.h>
#include <avr/wdt.h>

#define VERSION "7.1"
#define MODEL   "Arduino UNO R3"

// LCD Configuration

LiquidCrystal_I2C lcd(0x27, 16, 2);
char currentL1[17] = "";
char currentL2[17] = "";

// Bluetooth Configuration
SoftwareSerial BT(10, 11); // RX, TX
char btBuffer[32];
byte bufferIdx = 0;

// Pin Assignments
const byte RED_LED     = 6;
const byte GREEN_LED   = 7;
const byte BUZZER      = 8;
const byte BTN_POWER   = 2;
const byte BTN_ARM     = 3;
const byte BTN_DISARM  = 4;
const byte BTN_SILENCE = 5;

// System States
enum AlarmState { STATE_OFF, STATE_READY, STATE_WARNING, STATE_INTRUDER, STATE_FINAL };
AlarmState sysState = STATE_OFF;

bool powerOn = false;
bool armed = false;
bool muted = false;
bool autoScan = false;
int distance = 0;

// Timing Variables
unsigned long ms = 0;
unsigned long lastHeartbeat = 0;
unsigned long lastBuzzer = 0;
unsigned long lastFlash = 0;
unsigned long feedbackTimer = 0;
unsigned long lastBtnScan = 0;
unsigned long buzzerOffTime = 0;
bool buzzerActive = false;

// Connection State
bool isOnline = false;
bool pendingOnlineNotify = false;
unsigned long lastRxTime = 0;

// --- LCD HELPER FUNCTIONS ---

void updateLCD(const char* l1, const char* l2) {
    if (strncmp(l1, currentL1, 16) != 0) {
        lcd.setCursor(0, 0);
        lcd.print(F("                "));
        lcd.setCursor(0, 0);
        lcd.print(l1);
        strncpy(currentL1, l1, 16);
        currentL1[16] = '\0';
    }
    if (strncmp(l2, currentL2, 16) != 0) {
        lcd.setCursor(0, 1);
        lcd.print(F("                "));
        lcd.setCursor(0, 1);
        lcd.print(l2);
        strncpy(currentL2, l2, 16);
        currentL2[16] = '\0';
    }
}

void showFeedback(const char* msg) {
    updateLCD(msg, "");
    feedbackTimer = millis() + 2000;
}

void refreshLCD() {
    if (!powerOn) {
        updateLCD("SYSTEM OFF", "OMNI_GUARD V7.1");
        return;
    }

    if (millis() < feedbackTimer) return;

    char l1[17] = "";
    switch (sysState) {
        case STATE_OFF:      strcpy(l1, "SYSTEM OFF"); break;
        case STATE_READY:    strcpy(l1, armed ? "SYSTEM ARMED" : "SYSTEM DISARMED"); break;
        case STATE_WARNING:  strcpy(l1, "WARNING"); break;
        case STATE_INTRUDER: strcpy(l1, "INTRUDER ALERT"); break;
        case STATE_FINAL:    strcpy(l1, "FINAL ALERT!"); break;
        default:             strcpy(l1, "READY"); break;
    }

    char l2[17];
    snprintf(l2, 17, "Dist: %d cm", distance);
    updateLCD(l1, l2);
}

// --- COMMUNICATION FUNCTIONS ---

void sendStatus() {
    BT.print(F("STATUS:POWER="));    BT.print(powerOn);
    BT.print(F(",ARMED="));          BT.print(armed);
    BT.print(F(",MUTED="));          BT.print(muted);
    BT.print(F(",AUTOSCAN="));       BT.print(autoScan);
    BT.print(F(",STATE="));
    switch(sysState) {
        case STATE_OFF:      BT.print(F("OFF"));      break;
        case STATE_READY:    BT.print(F("READY"));    break;
        case STATE_WARNING:  BT.print(F("WARNING"));  break;
        case STATE_INTRUDER: BT.print(F("INTRUDER")); break;
        case STATE_FINAL:    BT.print(F("FINAL"));    break;
    }
    BT.print(F(",DISTANCE="));       BT.print(distance);
    BT.print(F(",REDLED="));         BT.print(digitalRead(RED_LED));
    BT.print(F(",GREENLED="));       BT.print(digitalRead(GREEN_LED));
    BT.print(F(",BUZZER="));         BT.print(digitalRead(BUZZER));
    BT.print(F(",VERSION="));        BT.println(VERSION);
}

void resetHardwareState() {
    distance = 0;
    armed = false;
    muted = false;
    autoScan = false;
    sysState = STATE_OFF;
    digitalWrite(RED_LED, LOW);
    digitalWrite(GREEN_LED, LOW);
    digitalWrite(BUZZER, LOW);
    buzzerActive = false;
    feedbackTimer = 0;
    lastBuzzer = 0;
    lastFlash = 0;
    showFeedback("POWER OFF");
}

void beep(int duration) {
    if (muted) return;
    digitalWrite(BUZZER, HIGH);
    buzzerOffTime = millis() + duration;
    buzzerActive = true;
}

void processCommand(const char* cmd, bool isButton) {
    if (strlen(cmd) == 0) return;

    const char* p = (strncmp(cmd, "CMD:", 4) == 0) ? cmd + 4 : cmd;

    char ackPrefix[15] = "ACK:";
    if (isButton) strcpy(ackPrefix, "ACK:BUTTON_");

    if (strcmp(p, "POWER") == 0) {
        powerOn = !powerOn;
        if (!powerOn) {
            resetHardwareState();
            BT.print(ackPrefix); BT.println(F("POWER"));
        } else {
            sysState = STATE_READY;
            showFeedback("POWER ON");
            BT.print(ackPrefix); BT.println(F("POWER"));
        }
        sendStatus();
    } else if (strcmp(p, "ARM") == 0) {
        if (powerOn) {
            armed = true; muted = false;
            showFeedback("SYSTEM ARMED");
            BT.print(ackPrefix); BT.println(F("ARM"));
            sendStatus();
        }
    } else if (strcmp(p, "DISARM") == 0) {
        if (powerOn) {
            armed = false; muted = false;
            if (sysState != STATE_OFF) sysState = STATE_READY;
            showFeedback("SYSTEM DISARMED");
            BT.print(ackPrefix); BT.println(F("DISARM"));
            sendStatus();
        }
    } else if (strcmp(p, "EMERGENCY") == 0) {
        powerOn = true; armed = true; muted = false;
        sysState = STATE_FINAL;
        showFeedback("EMERGENCY!");
        BT.print(ackPrefix); BT.println(F("EMERGENCY"));
        sendStatus();
    } else if (strcmp(p, "SILENCE") == 0) {
        if (powerOn) {
            muted = true;
            showFeedback("SILENCED");
            BT.print(ackPrefix); BT.println(F("SILENCE"));
            sendStatus();
        }
    } else if (strcmp(p, "SOUND") == 0) {
        if (powerOn) {
            muted = false;
            showFeedback("SOUND ON");
            BT.print(ackPrefix); BT.println(F("SOUND"));
            sendStatus();
        }
    } else if (strcmp(p, "AUTO_SCAN") == 0) {
        if (powerOn) {
            autoScan = true;
            showFeedback("AUTO SCAN ON");
            BT.print(ackPrefix); BT.println(F("AUTO_SCAN"));
            sendStatus();
        }
    } else if (strcmp(p, "STOP_SCAN") == 0) {
        if (powerOn) {
            autoScan = false;
            showFeedback("AUTO SCAN OFF");
            BT.print(ackPrefix); BT.println(F("STOP_SCAN"));
            sendStatus();
        }
    } else if (strcmp(p, "PING") == 0) {
        BT.println(F("PONG"));
    } else if (strcmp(p, "VERSION") == 0) {
        BT.println(F("VERSION:7.1"));
        BT.println(F("MODEL:Arduino UNO R3"));
        BT.println(F("DEVICE:HOUSE_UNIT"));
        BT.println(F("BUILD:RELEASE"));
        BT.println(F("WATCHDOG:ENABLED"));
        BT.println(F("PROTOCOL:V7"));
    } else if (strcmp(p, "RESET") == 0) {
        BT.println(F("HOUSE_UNIT_RESTARTING"));
        BT.print(ackPrefix); BT.println(F("RESET"));
        delay(500);
        wdt_enable(WDTO_15MS);
        while (1);
    } else if (strncmp(p, "STATE:", 6) == 0) {
        char body[20];
        strncpy(body, p + 6, sizeof(body) - 1);
        body[sizeof(body) - 1] = '\0';
        char* comma = strchr(body, ',');
        if (comma) {
            *comma = '\0';
            distance = atoi(comma + 1);
        }
        if (strcmp(body, "OFF") == 0) sysState = STATE_OFF;
        else if (strcmp(body, "READY") == 0) sysState = STATE_READY;
        else if (strcmp(body, "WARNING") == 0) sysState = STATE_WARNING;
        else if (strcmp(body, "INTRUDER") == 0) sysState = STATE_INTRUDER;
        else if (strcmp(body, "FINAL") == 0) sysState = STATE_FINAL;
        else {
            BT.print(F("ERROR:INVALID_STATE:"));
            BT.println(body);
            return;
        }
        sendStatus();
    } else {
        BT.print(F("ERROR:UNKNOWN_COMMAND:"));
        BT.println(p);
    }
}

// --- CORE SYSTEM FUNCTIONS ---

void setup() {
    wdt_disable();
    pinMode(RED_LED, OUTPUT); pinMode(GREEN_LED, OUTPUT); pinMode(BUZZER, OUTPUT);
    pinMode(BTN_POWER, INPUT_PULLUP); pinMode(BTN_ARM, INPUT_PULLUP);
    pinMode(BTN_DISARM, INPUT_PULLUP); pinMode(BTN_SILENCE, INPUT_PULLUP);

    // Standard 9600 baud for HC-05/HC-06
    Serial.begin(9600);
    BT.begin(9600);

    // Add stabilization delay for BT module
    delay(500);

    lcd.init();
    lcd.backlight();
    updateLCD("BOOTING...", "OMNI_GUARD");
    delay(500);
    updateLCD("OMNI_GUARD", "HOUSE UNIT V7.1");
    delay(1000);
    updateLCD("SYSTEM READY", "");

    BT.println(F("HOUSE_UNIT_READY"));
    BT.println(F("HOUSE_UNIT_ONLINE"));
    sendStatus();

    wdt_enable(WDTO_2S);
}

void loop() {
    wdt_reset();
    ms = millis();

    // Bluetooth Reception
    while (BT.available()) {
        char c = BT.read();
        lastRxTime = ms;
        if (!isOnline) {
            isOnline = true;
            pendingOnlineNotify = true;
        }
        if (c == '\n' || c == '\r') {
            if (bufferIdx > 0) {
                btBuffer[bufferIdx] = '\0';
                processCommand(btBuffer, false);
                bufferIdx = 0;
            }
        } else if (bufferIdx < 30) {
            btBuffer[bufferIdx++] = c;
        } else {
            BT.println(F("ERROR:PACKET_TOO_LONG"));
            bufferIdx = 0;
            while(BT.available() && BT.read() != '\n');
        }
    }

    // Delayed Online Notification to avoid blocking the read loop
    if (pendingOnlineNotify) {
        pendingOnlineNotify = false;
        BT.println(F("HOUSE_UNIT_ONLINE"));
        sendStatus();
    }

    // Online state monitoring
    if (isOnline && ms - lastRxTime > 15000) {
        isOnline = false;
    }

    // Heartbeat
    if (ms - lastHeartbeat >= 1000) {
        lastHeartbeat = ms;
        if (powerOn) sendStatus();
    }

    // Button Scanning
    if (ms - lastBtnScan >= 50) {
        lastBtnScan = ms;
        static bool lp = HIGH, la = HIGH, ld = HIGH, ls = HIGH;
        bool p = digitalRead(BTN_POWER);
        bool a = digitalRead(BTN_ARM);
        bool d = digitalRead(BTN_DISARM);
        bool s = digitalRead(BTN_SILENCE);

        if (lp == HIGH && p == LOW) {
            beep(50);
            BT.println(F("EVENT:BUTTON_POWER"));
            processCommand("POWER", true);
        }
        if (powerOn) {
            if (la == HIGH && a == LOW) {
                beep(50);
                BT.println(F("EVENT:BUTTON_ARM"));
                processCommand("ARM", true);
            }
            if (ld == HIGH && d == LOW) {
                beep(50);
                BT.println(F("EVENT:BUTTON_DISARM"));
                processCommand("DISARM", true);
            }
            if (ls == HIGH && s == LOW) {
                beep(50);
                BT.println(F("EVENT:BUTTON_SILENCE"));
                processCommand("SILENCE", true);
            }
        }
        lp = p; la = a; ld = d; ls = s;
    }

    // Alarm Driver Logic (Fully Non-blocking)
    unsigned long flashInterval = (sysState == STATE_FINAL) ? 100 : 300;
    if (ms - lastFlash >= flashInterval) {
        lastFlash = ms;
        static bool f = false; f = !f;

        if (!powerOn) {
            digitalWrite(RED_LED, LOW); digitalWrite(GREEN_LED, LOW); digitalWrite(BUZZER, LOW);
            buzzerActive = false;
        } else {
            digitalWrite(GREEN_LED, armed && sysState != STATE_OFF);
            if (sysState == STATE_READY) {
                digitalWrite(RED_LED, LOW);
                if (!buzzerActive) digitalWrite(BUZZER, LOW);
            } else if (sysState == STATE_WARNING) {
                digitalWrite(RED_LED, HIGH);
                if (ms - lastBuzzer > 800) { beep(20); lastBuzzer = ms; }
            } else if (sysState == STATE_INTRUDER) {
                digitalWrite(RED_LED, f);
                if (ms - lastBuzzer > 400) { beep(40); lastBuzzer = ms; }
            } else if (sysState == STATE_FINAL) {
                digitalWrite(RED_LED, f);
                if (!muted) digitalWrite(BUZZER, HIGH);
                else digitalWrite(BUZZER, LOW);
            } else {
                digitalWrite(RED_LED, LOW);
                digitalWrite(BUZZER, LOW);
                buzzerActive = false;
            }
        }
    }

    // Buzzer cleanup
    if (buzzerActive && ms >= buzzerOffTime) {
        digitalWrite(BUZZER, LOW);
        buzzerActive = false;
    }

    // LCD Maintenance
    refreshLCD();
}

package com.example.omni_guard.network

import android.util.Log
import com.example.omni_guard.data.SystemStateManager
import com.example.omni_guard.repository.SecurityRepository
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalServer @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val stateManager: SystemStateManager
) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        if (server != null) return

        try {
            server = embeddedServer(Netty, port = 8000, host = "0.0.0.0") {
                install(ContentNegotiation) {
                    json()
                }
                routing {
                    get("/") {
                        call.respondText("OMNI_GUARD V3.0 Internal Backend Online")
                    }

                    get("/sensor") {
                        val dist = call.request.queryParameters["dist"]?.toIntOrNull() ?: 0
                        val state = call.request.queryParameters["state"] ?: "CLEAR"
                        val angle = call.request.queryParameters["angle"]?.toIntOrNull() ?: 0
                        
                        // Optimized: Process immediately
                        securityRepository.handleSensorData(dist, angle, state)
                        call.respondText("ACK")
                    }

                    post("/sensor_v8") {
                        try {
                            val body = call.receiveText()
                            val json = Json.parseToJsonElement(body).jsonObject
                            
                            val sensor = json["sensor"]?.jsonObject
                            val dist = sensor?.get("s1")?.jsonPrimitive?.int ?: 0
                            val angle = sensor?.get("angle")?.jsonPrimitive?.int ?: 0
                            
                            val telemetry = json["telemetry"]?.jsonObject
                            val threatInt = telemetry?.get("threat")?.jsonPrimitive?.int ?: 0
                            val threatStr = when(threatInt) {
                                4 -> "FINAL"
                                3 -> "INTRUDER"
                                2 -> "WARNING"
                                1 -> "READY"
                                else -> "CLEAR"
                            }

                            // Update repository with rich data
                            securityRepository.handleSensorData(dist, angle, threatStr)
                            
                            // Log additional telemetry for diagnostics
                            stateManager.updateState { it.copy(
                                batteryLevel = 100, // Placeholder
                                signalStrength = telemetry?.get("rssi")?.jsonPrimitive?.int ?: 0,
                                lastMessage = "V8.0: ${json["type"]?.jsonPrimitive?.content}"
                            ) }

                            call.respondText("ACK_V8")
                        } catch (e: Exception) {
                            Log.e("InternalServer", "V8 Parse Error: ${e.message}")
                            call.respondText("ERROR")
                        }
                    }

                    get("/arduino") {
                        val state = stateManager.state.value
                        call.respond(buildJsonObject {
                            put("buzzer", state.buzzer)
                            put("threat", state.threat.name)
                            put("power", state.power.name)
                            put("security", state.security.name)
                        })
                    }
                }
            }
            server?.start(wait = false)
            Log.d("InternalServer", "V3.0 Core Server Active")
        } catch (e: Exception) {
            Log.e("InternalServer", "Server Bind Error: ${e.localizedMessage}")
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}

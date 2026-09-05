package dev.goodwy.rphone.controller.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Manages the "Wave to Answer" feature using the proximity sensor.
 * Detects a gesture: Far -> Near -> Far within 0.2 to 1.5 seconds.
 */
class WaveToAnswerManager(
    context: Context,
    private val onWaveDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var lastNearTime = 0L
    private var isNear = false
    private var isRunning = false

    // Time window for the gesture (Far -> Near -> Far)
    private val minGestureDuration = 200L
    private val maxGestureDuration = 1500L

    fun start() {
        if (isRunning) return
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isRunning = true
            Log.d("WaveToAnswerManager", "Proximity sensor registered")
        }
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isNear = false
        isRunning = false
        lastNearTime = 0L
        Log.d("WaveToAnswerManager", "Proximity sensor unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = event.sensor.maximumRange
            
            // Logic to determine Near vs Far
            // Usually, proximity sensor returns 0 for Near and maxRange for Far.
            // Some sensors might return values in between.
            val currentIsNear = distance < maxRange && distance < 5.0f

            if (currentIsNear && !isNear) {
                // Transition: Far -> Near
                isNear = true
                lastNearTime = System.currentTimeMillis()
                Log.d("WaveToAnswerManager", "Gesture: Near detected")
            } else if (!currentIsNear && isNear) {
                // Transition: Near -> Far
                isNear = false
                val now = System.currentTimeMillis()
                val duration = now - lastNearTime
                
                Log.d("WaveToAnswerManager", "Gesture: Far detected. Duration: $duration ms")

                if (duration in minGestureDuration..maxGestureDuration) {
                    Log.d("WaveToAnswerManager", "Wave gesture successful!")
                    onWaveDetected()
                } else {
                    Log.d("WaveToAnswerManager", "Wave gesture failed: duration out of range")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}

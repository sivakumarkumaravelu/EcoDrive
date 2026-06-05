package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.domain.model.DrivingEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A rule-based coaching engine that provides driving insights locally.
 * Used as a fallback when AI services are unavailable.
 */
@Singleton
class LocalEcoCoach @Inject constructor() {

    fun getInsight(trip: Trip, events: List<DrivingEvent>): String {
        val hardBrakes = events.count { it.type == DrivingEventType.HARD_BRAKE }
        val hardAccels = events.count { it.type == DrivingEventType.HARD_ACCELERATION }
        val sharpTurns = events.count { it.type == DrivingEventType.SHARP_TURN }
        val idleTime = events.filter { it.type == DrivingEventType.EXCESSIVE_IDLE }
            .sumOf { it.value.toLong() }

        return when {
            trip.ecoScore >= 95 -> {
                "Incredible job! Your driving is exceptionally smooth and efficient. Keep maintaining this level of consistency to maximize your fuel savings."
            }
            hardBrakes > 3 -> {
                "You had $hardBrakes hard braking events. Try to look further ahead and coast to a stop naturally; this reduces wear and improves fuel efficiency significantly."
            }
            hardAccels > 3 -> {
                "Frequent rapid acceleration was detected. Aim for a 'feather-touch' on the pedal when starting from a stop to keep your engine in its most efficient power band."
            }
            idleTime > 120 -> {
                "You spent over 2 minutes idling. If you're stopped for more than 30 seconds, turning off the engine can save more fuel than restarting it later."
            }
            sharpTurns > 3 -> {
                "Taking corners too quickly reduces your efficiency. Slow down before the turn and accelerate gently out of it to maintain better momentum."
            }
            trip.ecoScore < 70 -> {
                "Your score was lower this trip. Focusing on smoother transitions between accelerating and braking will have the biggest impact on your Eco Score."
            }
            else -> {
                "Solid trip! You're driving well. To reach an elite score, try to maintain a very steady speed and minimize any sudden changes in motion."
            }
        }
    }
}

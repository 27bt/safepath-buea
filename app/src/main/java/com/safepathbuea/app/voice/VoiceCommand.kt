package com.safepathbuea.app.voice

/** The fixed voice-command vocabulary the app listens for on hold-to-speak. */
sealed class VoiceCommand {
    data object WhatsAhead : VoiceCommand()
    data object WhereAmI : VoiceCommand()
    data object ReportHazard : VoiceCommand()
    data object NearbyHazards : VoiceCommand()
    data object Repeat : VoiceCommand()
    data object Help : VoiceCommand()
    data object Stop : VoiceCommand()
    data object Resume : VoiceCommand()
    data object CallForHelp : VoiceCommand()
    data class Unrecognized(val heardText: String) : VoiceCommand()

    companion object {
        /**
         * Matches on keyword phrases rather than exact strings so minor
         * recognizer variance ("what is ahead", "whats ahead of me") still
         * resolves correctly. Order matters: more specific phrases first.
         */
        fun parse(heardText: String): VoiceCommand {
            val text = heardText.trim().lowercase()
            return when {
                text.isBlank() -> Unrecognized(heardText)
                "call for help" in text || "call emergency" in text || "emergency call" in text -> CallForHelp
                "what" in text && "ahead" in text -> WhatsAhead
                "where am i" in text || "where i am" in text -> WhereAmI
                "report" in text && "hazard" in text -> ReportHazard
                "nearby" in text && "hazard" in text -> NearbyHazards
                "repeat" in text -> Repeat
                "help" in text -> Help
                "stop" in text || "pause" in text -> Stop
                "resume" in text || "continue" in text || "start" in text -> Resume
                else -> Unrecognized(heardText)
            }
        }
    }
}

package com.windscore.midi

actual fun createMidiManager(): MidiManager = AndroidMidiManager(com.windscore.core.requireAndroidContext())

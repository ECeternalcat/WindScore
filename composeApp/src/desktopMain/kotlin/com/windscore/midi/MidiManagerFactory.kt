package com.windscore.midi

actual fun createMidiManager(): MidiManager = DesktopMidiManager()

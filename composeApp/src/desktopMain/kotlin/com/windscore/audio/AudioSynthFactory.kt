package com.windscore.audio

actual fun createAudioSynth(): AudioSynth = DesktopSynth()

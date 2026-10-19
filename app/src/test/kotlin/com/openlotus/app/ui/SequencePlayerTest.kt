package com.openlotus.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Collections

class SequencePlayerTest {

    private fun rgb(frame: ByteArray): Triple<Int, Int, Int> =
        Triple(frame[4].toInt() and 0xFF, frame[5].toInt() and 0xFF, frame[6].toInt() and 0xFF)

    @Test
    fun playsColorsInOrderThenWraps() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<ByteArray>())
        val player = SequencePlayer { sent.add(it) }
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            player.start(scope, listOf(0xFF0000, 0x00FF00, 0x0000FF), 20)
            withTimeout(2_000) {
                while (sent.size < 5) delay(5)
            }
            player.stop()
            assertEquals(Triple(255, 0, 0), rgb(sent[0]))
            assertEquals(Triple(0, 255, 0), rgb(sent[1]))
            assertEquals(Triple(0, 0, 255), rgb(sent[2]))
            assertEquals(Triple(255, 0, 0), rgb(sent[3]))
            assertEquals(Triple(0, 255, 0), rgb(sent[4]))
        } finally {
            player.stop()
        }
    }

    @Test
    fun startCanResumeFromAnIndex() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<ByteArray>())
        val player = SequencePlayer { sent.add(it) }
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            player.start(scope, listOf(0xFF0000, 0x00FF00, 0x0000FF), 20, startIndex = 2)
            withTimeout(2_000) {
                while (sent.size < 3) delay(5)
            }
            player.stop()
            assertEquals(Triple(0, 0, 255), rgb(sent[0]))
            assertEquals(Triple(255, 0, 0), rgb(sent[1]))
            assertEquals(Triple(0, 255, 0), rgb(sent[2]))
        } finally {
            player.stop()
        }
    }

    @Test
    fun startWithNoColorsDoesNothing() {
        val player = SequencePlayer { }
        player.start(CoroutineScope(Dispatchers.Default), emptyList(), 20)
        assertFalse(player.playing)
        assertEquals(-1, player.currentIndex)
    }

    @Test
    fun stopResetsState() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<ByteArray>())
        val player = SequencePlayer { sent.add(it) }
        val scope = CoroutineScope(Dispatchers.Default)
        player.start(scope, listOf(0xFF0000), 20)
        withTimeout(1_000) {
            while (sent.isEmpty()) delay(5)
        }
        player.stop()
        assertFalse(player.playing)
        assertEquals(-1, player.currentIndex)
    }

    @Test
    fun toggleStopsWhenPlaying() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<ByteArray>())
        val player = SequencePlayer { sent.add(it) }
        val scope = CoroutineScope(Dispatchers.Default)
        player.toggle(scope, listOf(0xFF0000, 0x00FF00), 20)
        withTimeout(1_000) {
            while (sent.isEmpty()) delay(5)
        }
        player.toggle(scope, listOf(0xFF0000, 0x00FF00), 20)
        assertFalse(player.playing)
    }
}

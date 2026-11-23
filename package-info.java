/**
 * Mod core: tick-based input recording and playback.
 *
 * <p>{@link com.mirror.recorder.handler.RecordingHandler} coordinates the tick and calls the
 * playback parts (movement, camera, clicks, keyboard, windows, cursor, chat). Each part owns its
 * fields and is reset via {@code reset()}; shared state lives in {@link
 * com.mirror.recorder.handler.ReplayState}.
 *
 * <p>The window event format and the {@code GA_*} constants are described in {@link
 * com.mirror.recorder.handler.GuiEventQueue}; they are stored in recording files and do not change.
 * Detailed rules are in {@code AGENTS.md} at the project root.
 */
package com.mirror.recorder.handler;

package dev.spotifyhud.playback;

import dev.spotifyhud.SpotifyHudMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads the Windows 10/11 system media session (the same source used by the
 * Spotify desktop app) through a small PowerShell helper.
 *
 * <p>There is no official Java API for {@code Windows.Media.Control}, so a
 * hidden PowerShell process is used. The process is started lazily, restarted
 * automatically if it dies, and always killed when the mod shuts down or auto
 * detection is disabled.</p>
 */
public final class WindowsMediaDetector implements AutoCloseable {
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Playback status 4 is {@code Playing} in Windows.Media.Control. */
    private static final int PLAYBACK_STATUS_PLAYING = 4;

    private static final long RESTART_DELAY_MS = 5_000L;

    private static final String SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                $_.Name -eq 'AsTask' -and
                $_.GetParameters().Count -eq 1 -and
                $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            })[0]
            function Await($op, $type) {
                $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
                $t.Wait(-1) | Out-Null
                $t.Result
            }
            $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
            $mgr = Await ($mgrType::RequestAsync()) $mgrType
            $sep = [char]31
            while ($true) {
                try {
                    $sessions = $mgr.GetSessions()
                    $s = $sessions | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1
                    if (-not $s) { $s = $mgr.GetCurrentSession() }
                    if ($s) {
                        $m = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                        $tl = $s.GetTimelineProperties()
                        $pb = $s.GetPlaybackInfo()
                        $posMs = $tl.Position.TotalMilliseconds
                        if ([int]$pb.PlaybackStatus -eq 4) {
                            $age = ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
                            if ($age -gt 0 -and $age -lt 900000) { $posMs += $age }
                        }
                        Write-Output ("T" + $sep + $m.Title + $sep + $m.Artist + $sep + [int]$tl.Position.TotalSeconds + $sep + [int]$tl.EndTime.TotalSeconds + $sep + [int]$pb.PlaybackStatus + $sep + [long]$posMs)
                    } else {
                        Write-Output "NONE"
                    }
                } catch {
                    Write-Output "NONE"
                }
                [Console]::Out.Flush()
                Start-Sleep -Milliseconds 350
            }
            """;

    private final PlaybackTracker tracker;

    private volatile boolean closed;
    private Process process;
    private Path scriptFile;
    private Thread readerThread;
    private long lastStartAttemptMs;
    /** Last time a real track was reported; used to age out "nothing playing". */
    private long lastTrackMs;
    private static final long EMPTY_GRACE_MS = 2_500L;

    public WindowsMediaDetector(PlaybackTracker tracker) {
        this.tracker = tracker;
        if (IS_WINDOWS) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "SpotifyHUD-DetectorShutdown"));
        }
    }

    public boolean isSupported() {
        return IS_WINDOWS;
    }

    /**
     * Called once per client tick. Starts or stops the helper according to the
     * enabled/auto-detect configuration.
     */
    public synchronized void tick(boolean shouldRun) {
        if (!IS_WINDOWS || this.closed) {
            return;
        }

        if (!shouldRun) {
            this.stopProcess();
            // Without the detector the stored snapshot would otherwise stay on
            // screen forever after auto detection is switched off.
            if (this.tracker.current().hasTrack()) {
                this.tracker.clear();
            }
            return;
        }

        if (this.process != null && this.process.isAlive()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastStartAttemptMs < RESTART_DELAY_MS) {
            return;
        }
        this.lastStartAttemptMs = now;
        this.startProcess();
    }

    private void startProcess() {
        try {
            if (this.scriptFile == null || !Files.isRegularFile(this.scriptFile)) {
                this.scriptFile = Files.createTempFile("spotifyhud_media", ".ps1");
                this.scriptFile.toFile().deleteOnExit();
                Files.writeString(this.scriptFile, SCRIPT, StandardCharsets.UTF_8);
            }

            Process started = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden",
                    "-File", this.scriptFile.toAbsolutePath().toString()
            ).start();

            this.process = started;
            this.readerThread = new Thread(() -> this.readLoop(started), "SpotifyHUD-MediaDetector");
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        } catch (IOException | RuntimeException exception) {
            SpotifyHudMod.LOGGER.warn("Could not start the Windows media detector.", exception);
        }
    }

    private void readLoop(Process running) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!this.closed && (line = reader.readLine()) != null) {
                this.handleLine(line);
            }
        } catch (IOException ignored) {
            // The process was stopped or PowerShell exited; tick() restarts it.
        } finally {
            running.destroy();
        }
    }

    private void handleLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }

        if ("NONE".equals(line.trim())) {
            // Closing Spotify (or a session hand-off) reports no session. Keep
            // the last track for a short grace period so a momentary gap does
            // not blank the HUD, then fall back to the idle placeholder.
            if (this.tracker.current().hasTrack()
                    && System.currentTimeMillis() - this.lastTrackMs > EMPTY_GRACE_MS) {
                this.tracker.clear();
            }
            return;
        }

        String[] parts = line.split("\u001f", -1);
        if (parts.length < 6 || !"T".equals(parts[0])) {
            return;
        }

        this.lastTrackMs = System.currentTimeMillis();
        String title = parts[1].trim();
        String artist = parts[2].trim();
        int durationSec = parseInt(parts[4], 0);
        boolean playing = parseInt(parts[5], 0) == PLAYBACK_STATUS_PLAYING;
        long fallbackPositionMs = parseInt(parts[3], 0) * 1000L;
        long positionMs = parts.length >= 7 ? parseLong(parts[6], fallbackPositionMs) : fallbackPositionMs;

        this.tracker.updateFromDetector(title, artist, durationSec, positionMs, playing);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private synchronized void stopProcess() {
        Process running = this.process;
        this.process = null;
        this.readerThread = null;

        if (running != null) {
            running.destroy();
            try {
                if (!running.waitFor(500L, TimeUnit.MILLISECONDS)) {
                    running.destroyForcibly();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.destroyForcibly();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.stopProcess();
    }
}

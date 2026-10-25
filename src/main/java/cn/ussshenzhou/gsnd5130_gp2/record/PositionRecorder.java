package cn.ussshenzhou.gsnd5130_gp2.record;

import com.mojang.logging.LogUtils;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * @author USS_Shenzhou
 */
public class PositionRecorder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CSV_HEADER = "timestamp,x,y,z";
    private static final String INVALID_FILE_NAME_CHARACTERS = "\\/:*?\"<>|";
    /**
     * Rows stay in the writer's buffer and are pushed to the file once per second,
     * so memory use stays constant and a crash loses at most about one second of data.
     */
    private static final int FLUSH_INTERVAL_TICKS = 20;
    private static @Nullable BufferedWriter writer = null;
    private static @Nullable Path recordingFile = null;
    private static int ticksSinceLastFlush = 0;

    public static Path getOutputDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("GSND5130-GP2");
    }

    public static boolean isValidFileNamePart(String value) {
        if (value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (INVALID_FILE_NAME_CHARACTERS.indexOf(character) >= 0 || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    /**
     * An existing file with the same name is appended to instead of overwritten.
     */
    public static boolean start(String playerId, String round) {
        stop();
        try {
            Path directory = getOutputDirectory();
            Files.createDirectories(directory);
            Path file = directory.resolve(playerId + "-" + round + ".csv");
            boolean isNewFile = !Files.exists(file) || Files.size(file) == 0;
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            recordingFile = file;
            ticksSinceLastFlush = 0;
            if (isNewFile) {
                writer.write(CSV_HEADER);
                writer.write('\n');
            }
            LOGGER.info("Started recording player position to {}", file);
            return true;
        } catch (IOException | InvalidPathException e) {
            LOGGER.error("Failed to start recording player position.", e);
            stop();
            return false;
        }
    }

    public static void record(Vec3 position) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f\n", System.currentTimeMillis(), position.x(), position.y(), position.z()));
            ticksSinceLastFlush++;
            if (ticksSinceLastFlush >= FLUSH_INTERVAL_TICKS) {
                writer.flush();
                ticksSinceLastFlush = 0;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write player position, recording stopped.", e);
            stop();
        }
    }

    public static void stop() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
            LOGGER.info("Stopped recording player position to {}", recordingFile);
        } catch (IOException e) {
            LOGGER.error("Failed to close {}", recordingFile, e);
        } finally {
            writer = null;
            recordingFile = null;
        }
    }

    public static boolean isRecording() {
        return writer != null;
    }

    public static @Nullable Path getRecordingFile() {
        return recordingFile;
    }
}

package com.naocraftlab.configbackuper.core;

import com.naocraftlab.configbackuper.util.LoggerWrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.notExists;
import static java.nio.file.Files.walk;
import static java.util.zip.Deflater.BEST_COMPRESSION;
import static java.util.zip.Deflater.NO_COMPRESSION;

public class ConfigBackuper {

    public final static String BACKUP_EXTENSION = ".zip";

    private final static DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM_dd__HH_mm_ss");
    private final static Path GAME_PATH = Paths.get("./");
    private final static Path GAME_CONFIG_PATH = Paths.get("./options.txt");
    private final static Path CONFIG_FOLDER_PATH = Paths.get("./config");
    private final static Path SHADER_FOLDER_PATH = Paths.get("./shaderpacks");
    private final static Set<String> SHADER_CONFIG_EXTENSIONS = new HashSet<>(Arrays.asList(
            // OptiFine or Iris shader configs
            ".txt"
    ));
    private final static Set<Path> UNIQUE_MOD_CONFIG_PATHS = new HashSet<>(Arrays.asList(
            // OptiFine settings
            Paths.get("./optionsf.txt"),
            // OptiFine or Iris shader settings
            Paths.get("./optionsshaders.txt")
    ));

    private final LoggerWrapper log;
    private final ModConfig modConfig;

    public ConfigBackuper(LoggerWrapper log, ModConfig modConfig) {
        this.log = log;
        this.modConfig = modConfig;
    }

    public void performBackup() {
        final File backupFile = backupPath().toFile();
        log.info("Configuration backup creation started: {}", backupFile.getName());
        try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(backupFile))) {
            if (modConfig.isCompressionEnabled()) {
                log.info("Backup compression enabled");
                stream.setLevel(BEST_COMPRESSION);
            } else {
                log.info("Backup compression disabled");
                stream.setLevel(NO_COMPRESSION);
            }

            performBackupGameConfigs(stream);
            performBackupModConfigs(stream);
            performBackupShaderPackConfigs(stream);
        } catch (IOException e) {
            throw new NestedConfigBackuperException(e);
        }
        log.info("Configuration backup created: {}", backupFile.getName());
    }

    private Path backupPath() {
        final String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        final String backupFileName = modConfig.getBackupFilePrefix() + timestamp + modConfig.getBackupFileSuffix() + BACKUP_EXTENSION;
        return modConfig.getBackupFolder().resolve(backupFileName);
    }

    private void performBackupGameConfigs(ZipOutputStream stream) {
        if (!modConfig.isIncludeGameConfigs()) {
            log.info("Game configs not included in backup");
            return;
        }
        addZipEntry(stream, GAME_CONFIG_PATH);
        log.info("Game configs included in backup");
    }

    private void performBackupModConfigs(ZipOutputStream stream) {
        if (!modConfig.isIncludeModConfigs()) {
            log.info("Mod configs not included in backup");
            return;
        }

        UNIQUE_MOD_CONFIG_PATHS.forEach(config -> addZipEntry(stream, config));

        if (notExists(CONFIG_FOLDER_PATH)) {
            log.info("Mod configs not included in backup");
            return;
        }
        try (final Stream<Path> pathStream = walk(CONFIG_FOLDER_PATH)) {
            pathStream.filter(path -> !isDirectory(path)).forEach(path -> addZipEntry(stream, path));
        } catch (Exception e) {
            throw new NestedConfigBackuperException(e);
        }
        log.info("Mod configs included in backup");
    }

    private void performBackupShaderPackConfigs(ZipOutputStream stream) {
        if (!modConfig.isIncludeShaderPackConfigs()) {
            log.info("Shader pack configs not included in backup");
            return;
        }

        if (notExists(SHADER_FOLDER_PATH)) {
            log.info("Shader pack configs not included in backup");
            return;
        }
        try (final Stream<Path> pathStream = walk(SHADER_FOLDER_PATH)) {
            pathStream
                    .filter(path -> !isDirectory(path))
                    .filter(path -> SHADER_CONFIG_EXTENSIONS.stream().anyMatch(extension -> path.toString().endsWith(extension)))
                    .forEach(path -> addZipEntry(stream, path));
        } catch (Exception e) {
            throw new NestedConfigBackuperException(e);
        }
        log.info("Shader pack configs included in backup");
    }

    private void addZipEntry(ZipOutputStream stream, Path file) {
        if (notExists(file)) {
            return;
        }
        try {
            final ZipEntry zipEntry = new ZipEntry(toBackupPath(file));
            stream.putNextEntry(zipEntry);
            copy(file, stream);
            stream.closeEntry();
        } catch (IOException e) {
            throw new NestedConfigBackuperException(e);
        }
    }

    private String toBackupPath(Path file) {
        return GAME_PATH.relativize(file).toString();
    }
}

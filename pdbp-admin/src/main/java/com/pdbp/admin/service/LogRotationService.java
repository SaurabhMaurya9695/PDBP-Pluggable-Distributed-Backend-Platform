package com.pdbp.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing log file rotation and cleanup.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Monitors log directory for old files</li>
 *   <li>Deletes log files older than 3 days</li>
 *   <li>Runs as a background daemon thread</li>
 * </ul>
 *
 * @author Saurabh Maurya
 */
public class LogRotationService implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(LogRotationService.class);
    private static final String LOG_DIRECTORY = "/Users/saurabh/Desktop/Saurabh/PDBP/PDBP/logs";
    private static final int RETENTION_DAYS = 3;
    private static final long CLEANUP_INTERVAL_HOURS = 6; // Run cleanup every 6 hours

    private volatile boolean running = true;
    private Thread serviceThread;

    /**
     * Starts the log rotation service.
     */
    public void start() {
        if (serviceThread != null && serviceThread.isAlive()) {
            logger.warn("LogRotationService is already running");
            return;
        }

        serviceThread = new Thread(this, "LogRotationService");
        serviceThread.setDaemon(true);
        serviceThread.start();
        logger.info("LogRotationService started - Monitoring log directory: {}", LOG_DIRECTORY);
    }

    /**
     * Stops the log rotation service.
     */
    public void stop() {
        running = false;
        if (serviceThread != null) {
            serviceThread.interrupt();
            try {
                serviceThread.join(2000);
                logger.info("LogRotationService stopped");
            } catch (InterruptedException e) {
                logger.warn("Interrupted while stopping LogRotationService");
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        logger.info("LogRotationService: Starting log cleanup thread");

        // Perform initial cleanup
        cleanupOldLogs();

        // Run periodic cleanup
        while (running) {
            try {
                TimeUnit.HOURS.sleep(CLEANUP_INTERVAL_HOURS);
                if (running) {
                    cleanupOldLogs();
                }
            } catch (InterruptedException e) {
                logger.info("LogRotationService: Thread interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("LogRotationService: Error during cleanup cycle", e);
            }
        }

        logger.info("LogRotationService: Cleanup thread stopped");
    }

    /**
     * Cleans up log files older than retention period.
     */
    private void cleanupOldLogs() {
        try {
            Path logDir = Paths.get(LOG_DIRECTORY);
            if (!Files.exists(logDir)) {
                logger.debug("LogRotationService: Log directory does not exist: {}", LOG_DIRECTORY);
                return;
            }

            if (!Files.isDirectory(logDir)) {
                logger.warn("LogRotationService: Path is not a directory: {}", LOG_DIRECTORY);
                return;
            }

            Instant cutoffTime = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
            int deletedCount = 0;
            long totalSizeDeleted = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.log")) {
                for (Path logFile : stream) {
                    if (Files.isRegularFile(logFile)) {
                        FileTime lastModified = Files.getLastModifiedTime(logFile);
                        if (lastModified.toInstant().isBefore(cutoffTime)) {
                            long fileSize = Files.size(logFile);
                            Files.delete(logFile);
                            deletedCount++;
                            totalSizeDeleted += fileSize;
                            logger.info("LogRotationService: Deleted old log file: {} (age: {} days, size: {} bytes)",
                                    logFile.getFileName(),
                                    ChronoUnit.DAYS.between(lastModified.toInstant(), Instant.now()), fileSize);
                        }
                    }
                }
            }

            if (deletedCount > 0) {
                logger.info("LogRotationService: Cleanup completed - Deleted {} file(s), freed {} bytes", deletedCount,
                        totalSizeDeleted);
            } else {
                logger.debug("LogRotationService: No old log files to delete");
            }
        } catch (IOException e) {
            logger.error("LogRotationService: Error cleaning up log files", e);
        }
    }

    /**
     * Gets the current log file name based on today's date.
     *
     * @return log file name (e.g., "pdbp-2025-12-26.log")
     */
    public static String getCurrentLogFileName() {
        LocalDate today = LocalDate.now();
        return String.format("pdbp-%s.log", today);
    }

    /**
     * Gets the log directory path.
     *
     * @return log directory path
     */
    public static String getLogDirectory() {
        return LOG_DIRECTORY;
    }
}


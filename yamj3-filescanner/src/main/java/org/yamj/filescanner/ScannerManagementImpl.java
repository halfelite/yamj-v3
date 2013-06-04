/*
 *      Copyright (c) 2004-2013 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      The YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.filescanner;

import org.yamj.common.cmdline.CmdLineParser;
import org.yamj.common.dto.ImportDTO;
import org.yamj.common.dto.StageDirectoryDTO;
import org.yamj.common.dto.StageFileDTO;
import org.yamj.common.remote.service.GitHubService;
import org.yamj.common.tools.PropertyTools;
import org.yamj.common.type.DirectoryType;
import org.yamj.common.type.ExitType;
import org.yamj.common.type.StatusType;
import org.yamj.filescanner.comparator.FileTypeComparator;
import org.yamj.filescanner.model.Library;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.filescanner.model.LibraryCollection;
import org.yamj.filescanner.model.StatType;
import org.yamj.filescanner.service.PingCore;
import org.yamj.filescanner.service.SendToCore;
import org.yamj.filescanner.tools.DirectoryEnding;
import org.yamj.filescanner.tools.Watcher;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.remoting.RemoteConnectFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;
import org.yamj.common.tools.ClassTools;

/**
 * Performs an initial scan of the library location and then updates when changes occur.
 *
 * @author Stuart
 */
public class ScannerManagementImpl implements ScannerManagement {

    /*
     * TODO: choose between watcher process and simple re-scan
     * TODO: determine what files have changed between scans
     * TODO: add library file reader
     * TODO: add multiple directory location support
     */
    private static final Logger LOG = LoggerFactory.getLogger(ScannerManagementImpl.class);
    // The default watched status
    private static final Boolean DEFAULT_WATCH_STATE = PropertyTools.getBooleanProperty("filescanner.watch.default", Boolean.FALSE);
    private AtomicInteger runningCount = new AtomicInteger(0);
    @Autowired
    private LibraryCollection libraryCollection;
    @Autowired
    private PingCore pingCore;
    @Autowired
    private ThreadPoolTaskExecutor yamjExecutor;
    @Autowired
    private GitHubService githubService;
    // ImportDTO constants
    private static final String DEFAULT_CLIENT = PropertyTools.getProperty("filescanner.default.client", "FileScanner");
    private static final String DEFAULT_PLAYER_PATH = PropertyTools.getProperty("filescanner.default.playerpath", "");
    // Date check
    private static final int MAX_INSTALL_AGE = PropertyTools.getIntProperty("filescanner.installation.maxdays", 1);
    // Map of filenames & extensions that cause scanning of a directory to stop or a filename to be ignored
    private static final Map<String, List<String>> DIR_EXCLUSIONS = new HashMap<String, List<String>>();
    private static final List<String> DIR_IGNORE_FILES = new ArrayList<String>();

    static {
        // Set up the break scanning list. A "null" for the list means all files.
        // Ensure all filenames and extensions are lowercase
        DIR_EXCLUSIONS.put(".mjbignore", null);
        DIR_EXCLUSIONS.put(".no_all.nmj", null);
        DIR_EXCLUSIONS.put(".no_video.nmj", Arrays.asList("avi", "mkv"));
        DIR_EXCLUSIONS.put(".no_photo.nmj", Arrays.asList("jpg", "png"));
    }

    /**
     * Start the scanner and process the command line properties.
     *
     * @param parser
     * @return
     */
    @Override
    public ExitType runScanner(CmdLineParser parser) {
        try {
            String fsDate = ClassTools.getBuildTimestamp(ScannerManagementImpl.class);
            boolean installationOk = githubService.checkInstallationDate(fsDate, MAX_INSTALL_AGE);

            if (installationOk) {
                LOG.info("Installation is less than {} days old.", MAX_INSTALL_AGE);
            } else {
                LOG.error("***** Your installation is more than {} days old. You should consider updating! *****", MAX_INSTALL_AGE);
            }
        } catch (RemoteConnectFailureException ex) {
            LOG.warn("Failed to get GitHub status, error: {}", ex.getMessage());
        }

        libraryCollection.setDefaultClient(DEFAULT_CLIENT);
        libraryCollection.setDefaultPlayerPath(DEFAULT_PLAYER_PATH);
        pingCore.check(0, 0);   // Do a quick check of the status of the connection

        String directoryProperty = parser.getParsedOptionValue("d");
        boolean watchEnabled = parseWatchStatus(parser.getParsedOptionValue("w"));
        String libraryFilename = parser.getParsedOptionValue("l");

        if (StringUtils.isNotBlank(libraryFilename)) {
            libraryCollection.processLibraryFile(libraryFilename, watchEnabled);
        }

        if (StringUtils.isNotBlank(directoryProperty)) {
            LOG.info("Adding directory from command line: {}", directoryProperty);
            libraryCollection.addLibraryDirectory(directoryProperty, watchEnabled);
        }

        LOG.info("Found {} libraries to process.", libraryCollection.size());
        if (libraryCollection.size() == 0) {
            return ExitType.NO_DIRECTORY;
        }

        ExitType status = ExitType.SUCCESS;
        for (Library library : libraryCollection.getLibraries()) {
            library.getStatistics().setTimeStart(System.currentTimeMillis());
            status = scan(library);
            library.getStatistics().setTimeEnd(System.currentTimeMillis());
            LOG.info("{}", library.getStatistics().generateStatistics(Boolean.TRUE));
            LOG.info("Scanning completed.");
        }

        if (watchEnabled) {
            Watcher wd = new Watcher();
            Boolean directoriesToWatch = Boolean.TRUE;

            for (Library library : libraryCollection.getLibraries()) {
                String dirToWatch = library.getImportDTO().getBaseDirectory();
                if (library.isWatch()) {
                    LOG.info("Watching directory '{}' for changes...", dirToWatch);
                    wd.addDirectory(dirToWatch);
                    directoriesToWatch = Boolean.TRUE;
                } else {
                    LOG.info("Watching skipped for directory '{}'", dirToWatch);
                }
            }

            if (directoriesToWatch) {
                wd.processEvents();
                LOG.info("Watching directory '{}' completed", directoryProperty);
            } else {
                LOG.info("No directories marked for watching.");
            }
        } else {
            LOG.info("Watching not enabled.");
        }

        LOG.info("Exiting with status {}", status);

        return status;
    }

    /**
     * Start scanning a library.
     *
     * @param library
     * @return
     */
    private ExitType scan(Library library) {
        ExitType status = ExitType.SUCCESS;
        File baseDirectory = new File(library.getImportDTO().getBaseDirectory());
        LOG.info("Scanning library '{}'...", baseDirectory.getAbsolutePath());

        if (!baseDirectory.exists()) {
            LOG.info("Failed to read directory '{}'", baseDirectory.getAbsolutePath());
            return ExitType.NO_DIRECTORY;
        }

        scanDir(library, baseDirectory);

        checkLibraryAllSent(library);
        LOG.info("Completed.");

        return status;
    }

    /**
     * Scan a directory (and recursively any other directories contained
     *
     * @param library
     * @param parentDto
     * @param directory
     */
    private StageDirectoryDTO scanDir(Library library, File directory) {
        DirectoryType dirType = DirectoryEnding.check(directory);
        StageDirectoryDTO stageDir;

        LOG.info("Scanning directory '{}', detected type - {}", library.getRelativeDir(directory), dirType);

        if (dirType == DirectoryType.BLURAY || dirType == DirectoryType.DVD) {
            // Don't scan BLURAY or DVD structures
            LOG.info("Skipping directory '{}' as its a {} type", directory.getAbsolutePath(), dirType);
            library.getStatistics().increment(dirType == DirectoryType.BLURAY ? StatType.BLURAY : StatType.DVD);
            stageDir = null;
        } else {
            stageDir = new StageDirectoryDTO();
            stageDir.setPath(directory.getAbsolutePath());
            stageDir.setDate(directory.lastModified());

            library.getStatistics().increment(StatType.DIRECTORY);

            List<File> currentFileList = Arrays.asList(directory.listFiles());
            FileTypeComparator comp = new FileTypeComparator(Boolean.FALSE);
            Collections.sort(currentFileList, comp);

            /*
             * We need to scan the directory and look for any of the exclusion filenames.
             *
             * We then build a list of those excluded extensions, so that when we scan the filename list we can exclude the unwanted files.
             */
            List<String> exclusions = new ArrayList<String>();
            for (File file : currentFileList) {
                if (file.isFile()) {
                    String lcFilename = file.getName().toLowerCase();
                    if (DIR_EXCLUSIONS.containsKey(lcFilename)) {
                        if (CollectionUtils.isEmpty(DIR_EXCLUSIONS.get(lcFilename))) {
                            // Because the value is null or empty we exclude the whole directory, so quit now.
                            LOG.debug("Exclusion file '{}' found, skipping scanning of directory {}.", lcFilename, file.getParent());
                            return null;
                        } else {
                            // We found a match, so add it to our local copy
                            LOG.debug("Exclusion file '{}' found, will exclude all {} file types", lcFilename, DIR_EXCLUSIONS.get(lcFilename).toString());
                            exclusions.addAll(DIR_EXCLUSIONS.get(lcFilename));
                        }
                    }
                } else {
                    // First directory we find, we can stop (because we sorted the files first)
                    break;
                }
            }

            /*
             * Scan the directory properly
             */
            for (File file : currentFileList) {
                if (file.isFile()) {
                    String lcFilename = file.getName().toLowerCase();
                    if (exclusions.contains(FilenameUtils.getExtension(lcFilename)) || DIR_EXCLUSIONS.containsKey(lcFilename)) {
                        LOG.debug("File name '{}' excluded because it's listed in the exlusion list for this directory", file.getName());
                        continue;
                    } else {
                        stageDir.addStageFile(scanFile(file));
                        library.getStatistics().increment(StatType.FILE);
                    }
                } else {
                    // First directory we find, we can stop (because we sorted the files first)
                    break;
                }
            }

            library.addDirectory(stageDir);
            sendToCore(library, stageDir);

            // Resort the files with directories first
            comp.setDirectoriesFirst(Boolean.TRUE);
            Collections.sort(currentFileList, comp);

            // Now scan the directories
            for (File scanDir : currentFileList) {
                if (scanDir.isDirectory()) {
                    if (scanDir(library, scanDir) == null) {
                        LOG.info("Not adding directory '{}', no files found or all excluded", scanDir.getAbsolutePath());
                    }
                } else {
                    // First file we find, we can stop (because we are sorted directories first)
                    break;
                }
            }
        }
        return stageDir;
    }

    /**
     * Scan an individual file
     *
     * @param library
     * @param parentDto
     * @param file
     */
    private StageFileDTO scanFile(File file) {
        LOG.info("Scanning file '{}'", file.getName());
        return new StageFileDTO(file);
    }

    /**
     * Send an ImportDTO to the core
     *
     * Increment the running count
     *
     * @param importDto
     */
    private void sendToCore(Library library, StageDirectoryDTO stageDir) {
        ImportDTO dto = library.getImportDTO(stageDir);

        LOG.debug("Sending #{}: {}", runningCount.incrementAndGet(), dto.getBaseDirectory());

        ApplicationContext appContext = ApplicationContextProvider.getApplicationContext();
        SendToCore stc = (SendToCore) appContext.getBean("sendToCore");
        stc.setImportDto(dto);
        stc.setCounter(runningCount);
        FutureTask<StatusType> task = new FutureTask<StatusType>(stc);

        yamjExecutor.submit(task);
//        library.addDirectoryStatus(stageDir.getPath(), ConcurrentUtils.constantFuture(StatusType.DONE));
        library.addDirectoryStatus(stageDir.getPath(), task);
    }

    /**
     * Check that the library has all the directories sent to the core server
     *
     * If there are entries that have not been sent, or need resending, they should be done as well
     *
     * @param library
     */
    private void checkLibraryAllSent(Library library) {
        int retryCount = 3; // The number of times to retry sending the files to core
        List<String> resendDirs = new ArrayList<String>();

        int processedDone, processedError, unprocessed;
        do {
            // Clear the directories to resend.
            resendDirs.clear();
            processedDone = 0;
            processedError = 0;
            unprocessed = 0;
            LOG.info("There are {} items remaining to be sent to core.", runningCount.get());
            for (Entry<String, Future<StatusType>> entry : library.getDirectoryStatus().entrySet()) {
                try {
                    if (entry.getValue().isDone()) {
                        LOG.info("{} - Status: {}", entry.getKey(), entry.getValue().get());
                        if (entry.getValue().get() == StatusType.ERROR) {
                            processedError++;
                            // Add the directory to the list to resend.
                            resendDirs.add(entry.getKey());
                        } else {
                            processedDone++;
                        }
                    } else {
                        LOG.info("{} - Not yet processed", entry.getKey());
                        unprocessed++;
                    }
                } catch (InterruptedException ex) {
                } catch (ExecutionException ex) {
                }
            }

            LOG.info("Done: {}, Error: {}, Unprocessed: {}", processedDone, processedError, unprocessed);

            if (processedError > 0) {
                LOG.info("There were {} errors sending to the server. Will attempt to send {} more times.", processedError, retryCount--);

                for (String errorDir : resendDirs) {
                    LOG.info("Resending '{}' to the core", errorDir);
                    // Get the error StageDTO
                    StageDirectoryDTO stageDto = library.getDirectory(errorDir);
                    // Now resend it to the core
                    sendToCore(library, stageDto);
                    // Add one to the unprocessed count
                    unprocessed++;
                }
            }

            if (unprocessed > 0) {
                try {
                    LOG.info("Sleeping...");
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException ex) {
                    //
                }
            }
        } while (unprocessed > 0 && retryCount >= 0);

    }

    /**
     * Get the watched status from the command line property or return the default value.
     *
     * @param parsedOptionValue the property from the command line
     * @return
     */
    private boolean parseWatchStatus(String parsedOptionValue) {
        if (StringUtils.isBlank(parsedOptionValue)) {
            return DEFAULT_WATCH_STATE;
        }
        return Boolean.parseBoolean(parsedOptionValue);
    }

    public ThreadPoolTaskExecutor getYamjExecutor() {
        return yamjExecutor;
    }

    public void setYamjExecutor(ThreadPoolTaskExecutor yamjExecutor) {
        this.yamjExecutor = yamjExecutor;
    }
}

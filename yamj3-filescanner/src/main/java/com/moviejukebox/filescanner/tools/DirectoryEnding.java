package com.moviejukebox.filescanner.tools;

import com.moviejukebox.core.database.model.type.DirectoryType;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Check the ending of a directory against known types
 *
 * @author Stuart
 */
public class DirectoryEnding {

    // Directory endings for DVD and Blurays
    private static final Map<String, DirectoryType> DIR_ENDINGS = new HashMap<String, DirectoryType>(3);

    static {
        // The ending of the directory & Type
        DIR_ENDINGS.put("BDMV", DirectoryType.BLURAY);
        DIR_ENDINGS.put("AUDIO_TS", DirectoryType.DVD);
        DIR_ENDINGS.put("VIDEO_TS", DirectoryType.DVD);
    }

    private DirectoryEnding() {
        throw new UnsupportedOperationException("Cannot initialise class");
    }

    /**
     * Add a directory ending to the list
     *
     * @param ending
     * @param dirType
     */
    public static void add(String ending, DirectoryType dirType) {
        DIR_ENDINGS.put(ending, dirType);
    }

    /**
     * Return the DirectoryType of the directory
     *
     * @param directory
     * @return
     */
    public static DirectoryType check(File directory) {
        if (DIR_ENDINGS.containsKey(directory.getName())) {
            return DIR_ENDINGS.get(directory.getName());
        }
        return DirectoryType.STANDARD;
    }
}
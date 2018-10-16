package com.eleybourn.bookcatalogue.filechooser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Object to provide a FileLister specific to archive files.
 *
 * @author pjw
 */
public class BackupLister extends FileLister {
    /** Pattern to match an archive file spec */
    private static final Pattern mBackupFilePattern = Pattern.compile(".bcbk$", Pattern.CASE_INSENSITIVE);
    /**
     * Construct a file filter to select only directories and backup files.
     */
    private final FileFilter mFilter = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return (f.isDirectory() && f.canWrite()) || (f.isFile() && mBackupFilePattern.matcher(f.getName()).find());
        }
    };

    /**
     * Constructor
     */
    BackupLister(@NonNull final File root) {
        super(root);
    }

    /**
     * Get the file filter we constructed
     */
    @NonNull
    protected FileFilter getFilter() {
        return mFilter;
    }

    /**
     * Process an array of Files into an ArrayList of BackupFileDetails
     */
    @NonNull
    protected ArrayList<FileDetails> processList(@Nullable final File[] files) {
        ArrayList<FileDetails> dirs = new ArrayList<>();
        if (files == null) {
            return dirs;
        }
        for (File entry : files) {
            BackupFileDetails fd = new BackupFileDetails(entry);
            dirs.add(fd);
            if (entry.getName().toUpperCase().endsWith(".BCBK")) {
                BackupReader reader = null;
                try {
                    reader = BackupManager.readFrom(entry);
                    fd.setInfo(reader.getInfo());
                    reader.close();
                } catch (IOException e) {
                    Logger.error(e);
                    if (reader != null)
                        try {
                            reader.close();
                        } catch (IOException ignore) {
                        }
                }
            }

        }
        return dirs;
    }

}

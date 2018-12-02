package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.filechooser.FileListerFragmentTask;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Object to provide a FileListerFragmentTask specific to archive files.
 *
 * @author pjw
 */
public class BackupListerFragmentTask extends FileListerFragmentTask {

    /**
     * Construct a file filter to select only directories and backup files.
     */
    private final FileFilter mFilter = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return (f.isDirectory() && f.canWrite()) || (f.isFile() && BackupFileDetails.isArchive(f));
        }
    };

    /**
     * Constructor
     */
    BackupListerFragmentTask(final @NonNull File root) {
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
    protected ArrayList<FileDetails> processList(final @NonNull Context context, final @Nullable File[] files) {
        ArrayList<FileDetails> dirs = new ArrayList<>();
        if (files == null) {
            return dirs;
        }
        for (File file : files) {
            BackupFileDetails fd = new BackupFileDetails(file);
            dirs.add(fd);
            if (BackupFileDetails.isArchive(file)) {
                BackupReader reader = null;
                try {
                    reader = BackupManager.readFrom(context, file);
                    fd.setInfo(reader.getInfo());
                } catch (IOException e) {
                    Logger.error(e);
                } finally {
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

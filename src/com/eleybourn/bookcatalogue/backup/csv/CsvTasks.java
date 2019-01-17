/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment.FragmentTaskAbstract;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * {@link FragmentTask} implementations for Exporting and Importing CSV data.
 *
 */
public final class CsvTasks {

    private CsvTasks() {
    }

    /**
     * Start a foreground task that exports all books to a CSV file.
     * <p>
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    public static void exportCSV(@NonNull final FragmentActivity context,
                                 final int taskId,
                                 @NonNull final ExportSettings settings) {

        settings.validate();
        final CsvExporter mExporter = new CsvExporter(settings);

        final FragmentTask task = new FragmentTaskAbstract() {

            @Override
            public void run(@NonNull final TaskWithProgressDialogFragment fragment,
                            @NonNull final SimpleTaskContext taskContext)
                    throws Exception {
                File tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
                final FileOutputStream out = new FileOutputStream(tmpFile);
                // start the export
                mExporter.doBooks(out, new Exporter.ExportListener() {
                    @Override
                    public void onProgress(@NonNull final String message,
                                           final int position) {
                        fragment.onProgress(message, position);
                    }

                    @Override
                    public boolean isCancelled() {
                        return fragment.isCancelled();
                    }

                    @Override
                    public void setMax(final int max) {
                        fragment.setMax(max);
                    }
                });

                if (out.getChannel().isOpen()) {
                    out.close();
                }

                if (fragment.isCancelled()) {
                    StorageUtils.deleteFile(tmpFile);
                } else {
                    mExporter.renameFiles(tmpFile);
                }
            }

            @Override
            public void onFinish(@NonNull final TaskWithProgressDialogFragment fragment,
                                 @Nullable final Exception e) {
                super.onFinish(fragment, e);
            }
        };
        // show progress dialog and start the task
        TaskWithProgressDialogFragment frag = TaskWithProgressDialogFragment
                .newInstance(context, R.string.progress_msg_backing_up,
                             task, false, taskId);
        frag.setNumberFormat(null);
    }

    /**
     * Start a foreground task that import books from a CSV file.
     * <p>
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    public static void importCSV(@NonNull final FragmentActivity context,
                                 final int taskId,
                                 @NonNull final ImportSettings settings) {

        final CsvImporter mImporter = new CsvImporter(settings);

        final FragmentTask task = new FragmentTaskAbstract() {

            @Override
            public void run(@NonNull final TaskWithProgressDialogFragment fragment,
                            @NonNull final SimpleTaskContext taskContext)
                    throws Exception {

                try (FileInputStream in = new FileInputStream(settings.file)) {
                    mImporter.doImport(in, new LocalCoverFinder(settings.file.getParent()),
                                       new Importer.ImportListener() {

                                           @Override
                                           public void onProgress(@NonNull final String message,
                                                                  final int position) {
                                               fragment.onProgress(message, position);
                                           }

                                           @Override
                                           public boolean isCancelled() {
                                               return fragment.isCancelled();
                                           }

                                           @Override
                                           public void setMax(final int max) {
                                               fragment.setMax(max);
                                           }
                                       });

                }
            }

            @Override
            public void onFinish(@NonNull final TaskWithProgressDialogFragment fragment,
                                 @Nullable final Exception e) {
                super.onFinish(fragment, e);
            }
        };

        // show progress dialog and start the task
        TaskWithProgressDialogFragment frag = TaskWithProgressDialogFragment
                .newInstance(context, R.string.progress_msg_importing,
                             task, false, taskId);
        frag.setNumberFormat(null);
    }
}

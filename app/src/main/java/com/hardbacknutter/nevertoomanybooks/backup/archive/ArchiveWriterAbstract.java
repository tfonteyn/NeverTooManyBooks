/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.backup.options.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.options.Options;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlExporter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Default implementation of format-specific methods.
 */
public abstract class ArchiveWriterAbstract
        extends ArchiveWriterAbstractBase {

    private static final int BUFFER_SIZE = 65535;

    @Nullable
    private File mTmpBookCsvFile;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    protected ArchiveWriterAbstract(@NonNull final Context context,
                                    @NonNull final ExportHelper helper) {
        super(context, helper);
    }

    /**
     * Default implementation: write as XML
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void writeArchiveHeader(@NonNull final ArchiveInfo archiveInfo)
            throws IOException {
        // Write the archiveInfo as XML to a byte array.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             // 'ALL', just to satisfy the validator; we're calling writeArchiveInfo directly.
             XmlExporter xmlExporter = new XmlExporter(new ExportHelper(ExportHelper.ALL))) {
            xmlExporter.writeArchiveInfo(writer, archiveInfo);
        }
        // and store the array
        putByteArray(ArchiveManager.INFO_FILE, data.toByteArray());
    }

    /**
     * Default implementation: write as XML
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void writeStyles(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {
        // Write the styles as XML to a byte array.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             // create a new helper! We can only handle one option here.
             Exporter exporter = new XmlExporter(new ExportHelper(Options.STYLES))) {
            mResults.add(exporter.write(context, writer, progressListener));
        }
        // and store the array
        putByteArray(ArchiveManager.STYLES, data.toByteArray());
    }

    /**
     * Default implementation: write as XML
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void writePreferences(@NonNull final Context context,
                                 @NonNull final ProgressListener progressListener)
            throws IOException {
        // Write the preferences as XML to a byte array.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             // create a new helper! We can only handle one option here.
             Exporter exporter = new XmlExporter(new ExportHelper(Options.PREFERENCES))) {
            exporter.write(context, writer, progressListener);
        }
        // and store the array
        putByteArray(ArchiveManager.PREFERENCES, data.toByteArray());
    }

    /**
     * Default implementation: write as a temporary CSV file,
     * to be added to the archive in {@link #writeBooks}.
     * <p>
     * {@inheritDoc}
     */
    public void prepareBooks(@NonNull final Context context,
                             @NonNull final ProgressListener progressListener)
            throws IOException {
        // Get a temp file and set for delete
        mTmpBookCsvFile = File.createTempFile("books_csv_", ".tmp");
        mTmpBookCsvFile.deleteOnExit();

        Exporter exporter = new CsvExporter(context, mHelper);
        mResults.add(exporter.write(context, mTmpBookCsvFile, progressListener));
    }

    /**
     * Default implementation: write the file as prepared in {@link #prepareBooks}.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void writeBooks(@NonNull final Context context,
                           @NonNull final ProgressListener progressListener)
            throws IOException {
        try {
            Objects.requireNonNull(mTmpBookCsvFile);
            putFile(ArchiveManager.BOOKS_CSV, mTmpBookCsvFile);
        } finally {
            FileUtils.delete(mTmpBookCsvFile);
        }
    }
}

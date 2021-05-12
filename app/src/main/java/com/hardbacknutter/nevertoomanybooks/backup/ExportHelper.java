/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Writes to a temporary file in the internal cache first.
 */
public class ExportHelper {

    /** Log tag. */
    private static final String TAG = "ExportHelper";

    /** What is going to be exported. */
    @NonNull
    private final Set<RecordType> mRecordTypes;
    /** <strong>Where</strong> we write to. */
    @Nullable
    private Uri mUri;
    /** <strong>How</strong> to write to the Uri. */
    @NonNull
    private ArchiveEncoding mEncoding = ArchiveEncoding.Zip;


    /**
     * Do an incremental export. Definition of incremental depends on the writer.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated</li>
     * </ul>
     */
    private boolean mIncremental;

    /**
     * Constructor.
     */
    public ExportHelper() {
        mRecordTypes = EnumSet.allOf(RecordType.class);
    }

    /**
     * Constructor for testing individual options.
     *
     * @param recordTypes to write
     */
    @VisibleForTesting
    public ExportHelper(@NonNull final RecordType... recordTypes) {
        mRecordTypes = EnumSet.copyOf(Arrays.asList(recordTypes));
    }

    @NonNull
    public ArchiveEncoding getEncoding() {
        return mEncoding;
    }

    public void setEncoding(@NonNull final ArchiveEncoding encoding) {
        mEncoding = encoding;
    }

    /**
     * Is this a backup or an export.
     *
     * @return {@code true} when this is considered a backup,
     * {@code false} when it's considered an export.
     */
    public boolean isBackup() {
        return mEncoding == ArchiveEncoding.Zip;
    }

    @NonNull
    public Uri getUri() {
        return Objects.requireNonNull(mUri, "mFileUri");
    }

    public void setUri(@NonNull final Uri uri) {
        mUri = uri;
    }

    void setRecordType(@NonNull final RecordType recordType,
                       final boolean isSet) {
        if (isSet) {
            mRecordTypes.add(recordType);
        } else {
            mRecordTypes.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getRecordTypes() {
        return mRecordTypes;
    }


    public boolean isIncremental() {
        return mIncremental;
    }

    void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }


    /**
     * Create an {@link ArchiveWriter} based on the type.
     *
     * @param context Current context
     *
     * @return a new writer
     */
    @NonNull
    public ArchiveWriter createWriter(@NonNull final Context context)
            throws FileNotFoundException {
        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(mUri, "uri");
            if (mRecordTypes.isEmpty()) {
                throw new IllegalStateException("mExportEntries.isEmpty()");
            }
        }
        return mEncoding.createWriter(context, this);
    }

    /**
     * Create/get the OutputStream to write to.
     * When writing is done (success <strong>and</strong> failure),
     * {@link #onSuccess} / {@link #onError} must be called as needed.
     *
     * @param context Current context
     *
     * @return FileOutputStream
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    public FileOutputStream createOutputStream(@NonNull final Context context)
            throws FileNotFoundException {
        return new FileOutputStream(getTempFile(context));
    }

    /**
     * Should be called after a successful write.
     *
     * @param context Current context
     *
     * @throws IOException on failure to write to the destination Uri
     */
    public void onSuccess(@NonNull final Context context)
            throws IOException {
        Objects.requireNonNull(mUri, "uri");

        // The output file is now properly closed, export it to the user Uri
        final File tmpOutput = getTempFile(context);

        try (InputStream is = new FileInputStream(tmpOutput);
             OutputStream os = context.getContentResolver().openOutputStream(mUri)) {
            if (os != null) {
                FileUtils.copy(is, os);
            }
        }

        // cleanup
        FileUtils.delete(tmpOutput);
    }

    /**
     * Should be called after a failed write.
     *
     * @param context Current context
     */
    public void onError(@NonNull final Context context) {
        // cleanup
        FileUtils.delete(getTempFile(context));
    }

    private File getTempFile(@NonNull final Context context) {
        return new File(context.getCacheDir(), TAG + ".tmp");
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + "mRecordTypes=" + mRecordTypes
               + ", mUri=" + mUri
               + ", mEncoding=" + mEncoding
               + ", mIncremental=" + mIncremental
               + '}';
    }
}

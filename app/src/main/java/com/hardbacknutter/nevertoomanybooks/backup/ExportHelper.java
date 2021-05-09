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
import android.os.Bundle;

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
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Writes to a temporary file in the internal cache first.
 */
public class ExportHelper {

    /** Log tag. */
    private static final String TAG = "ExportHelper";
    /** What is going to be exported. */
    @NonNull
    private final Set<RecordType> mExportEntries;
    /** Extra arguments for specific writers. The writer must define them. */
    private final Bundle mExtraArgs = new Bundle();
    /** Picked by the user; file Uri where we write to; will be {@code null} for remote servers. */
    @Nullable
    private Uri mFileUri;
    /** Set by the user; defaults to ZIP. */
    @NonNull
    private ArchiveEncoding mArchiveEncoding = ArchiveEncoding.Zip;
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
        mExportEntries = EnumSet.allOf(RecordType.class);
    }

    /**
     * Constructor for testing individual options.
     *
     * @param exportEntries to write
     */
    @VisibleForTesting
    public ExportHelper(@NonNull final RecordType... exportEntries) {
        mExportEntries = EnumSet.copyOf(Arrays.asList(exportEntries));
    }

    @NonNull
    public ArchiveEncoding getEncoding() {
        return mArchiveEncoding;
    }

    public void setEncoding(@NonNull final ArchiveEncoding archiveEncoding) {
        mArchiveEncoding = archiveEncoding;
    }

    /**
     * Is this a backup or an export.
     *
     * @return {@code true} when this is considered a backup,
     * {@code false} when it's considered an export.
     */
    public boolean isBackup() {
        return mArchiveEncoding == ArchiveEncoding.Zip;
    }

    @Nullable
    public Uri getFileUri() {
        return mFileUri;
    }

    public void setFileUri(@Nullable final Uri fileUri) {
        mFileUri = fileUri;
    }

    /**
     * Create an {@link ArchiveWriter} based on the type.
     *
     * @param context Current context
     *
     * @return a new writer
     *
     * @throws CertificateException on failures related to a user installed CA.
     * @throws SSLException         on secure connection failures
     */
    @NonNull
    public ArchiveWriter createArchiveWriter(@NonNull final Context context)
            throws CertificateException,
                   SSLException,
                   FileNotFoundException {

        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(mFileUri, "uri");
            if (mExportEntries.isEmpty()) {
                throw new IllegalStateException("mExportEntries.isEmpty()");
            }
        }

        return mArchiveEncoding.createWriter(context, this);
    }

    @NonNull
    public Bundle getExtraArgs() {
        return mExtraArgs;
    }

    private File getTempFile(@NonNull final Context context) {
        return new File(context.getCacheDir(), TAG + ".tmp");
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
        Objects.requireNonNull(mFileUri, "uri");

        if (getEncoding().isFile()) {
            // The output file is now properly closed, export it to the user Uri
            final File tmpOutput = getTempFile(context);

            try (InputStream is = new FileInputStream(tmpOutput);
                 OutputStream os = context.getContentResolver().openOutputStream(mFileUri)) {
                if (os != null) {
                    FileUtils.copy(is, os);
                }
            }

            // cleanup
            FileUtils.delete(tmpOutput);
        }
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

    void setExportEntry(@NonNull final RecordType entry,
                        final boolean isSet) {
        if (isSet) {
            mExportEntries.add(entry);
        } else {
            mExportEntries.remove(entry);
        }
    }

    /**
     * Get the currently selected entry types that will be exported.
     *
     * @return set
     */
    @NonNull
    public Set<RecordType> getExporterEntries() {
        return mExportEntries;
    }


    public boolean isIncremental() {
        return mIncremental;
    }

    void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + "mExportEntities=" + mExportEntries
               + ", mUri=" + mFileUri
               + ", mArchiveEncoding=" + mArchiveEncoding
               + ", mIncremental=" + mIncremental
               + ", mExtraArgs=" + mExtraArgs
               + '}';
    }
}

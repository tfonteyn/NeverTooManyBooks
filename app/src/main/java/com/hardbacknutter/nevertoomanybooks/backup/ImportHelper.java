/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordReader;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class ImportHelper {

    /**
     * New Books/Covers are always imported (if {@link ArchiveWriterRecord.Type#Books} is set obviously).
     * <p>
     * Existing Books/Covers handling:
     * <ul>
     *     <li>00: skip entirely, keep the current data.</li>
     *     <li>01: overwrite current data with incoming data.</li>
     *     <li>10: [invalid combination]</li>
     *     <li>11: check the "update_date" field and only import newer data.</li>
     * </ul>
     */
    public static final int OPTION_UPDATES_MAY_OVERWRITE = 1;
    public static final int OPTION_UPDATES_MUST_SYNC = 1 << 1;

    /** Picked by the user; where we read from. */
    @NonNull
    private final Uri mUri;
    /** Constructed from the Uri. */
    @NonNull
    private final ArchiveType mArchiveType;
    /** What is going to be imported. */
    @NonNull
    private final Set<ArchiveReaderRecord.Type> mImportEntries =
            EnumSet.of(ArchiveReaderRecord.Type.InfoHeader);
    @Nullable
    private ArchiveInfo mArchiveInfo;
    /** Bitmask.  Contains extra options for the {@link RecordReader}. */
    @Options
    private int mOptions;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param uri     to read from
     */
    public ImportHelper(@NonNull final Context context,
                        @NonNull final Uri uri) {
        mUri = uri;
        mArchiveType = ArchiveType.fromUri(context, mUri);
        // Note: don't pre-create the reader here. No need + it needs a reference to 'this'
    }


    /**
     * Check if the archive provides <strong>ONLY</strong> books to import.
     * (i.e. it does NOT contain Preferences, Styles, Covers,...)
     *
     * @return {@code true} if this archive contains only Books
     */
    boolean archiveCanOnlyHaveBooks() {
        // Not added to the ArchiveType class as this API is a bit shaky...
        switch (mArchiveType) {
            case Csv:
            case Json:
            case SqLiteDb:
                return true;

            case Zip:
            case Xml:
            case Tar:
            case Unknown:
            default:
                return false;
        }
    }

    /**
     * Get the Uri for the user location to read from.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        return mUri;
    }

    /**
     * Get the name of the archive (based on the Uri).
     *
     * @param context Current context
     *
     * @return name
     */
    @NonNull
    public String getArchiveName(@NonNull final Context context) {
        final FileUtils.UriInfo uriInfo = FileUtils.getUriInfo(context, mUri);
        return uriInfo.displayName;
    }

    /**
     * Get the archive type as detected from the uri.
     *
     * @return type
     */
    @NonNull
    ArchiveType getArchiveType() {
        return mArchiveType;
    }

    @NonNull
    public ArchiveReader createArchiveReader(@NonNull final Context context)
            throws InvalidArchiveException, IOException {
        if (BuildConfig.DEBUG /* always */) {
            if (mImportEntries.isEmpty()) {
                throw new IllegalStateException("mImportEntries is empty");
            }
        }
        return mArchiveType.createArchiveReader(context, this);
    }

    /**
     * Validate the selected archive.
     *
     * <strong>Dev. note:</strong> this is just a semantic wrapper
     * around {@link #getArchiveInfo(Context)}.
     *
     * @param context Current context
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    void validateArchive(@NonNull final Context context)
            throws InvalidArchiveException, IOException {
        // pre-read the archive info block which will do the actual validation.
        getArchiveInfo(context);
    }

    /**
     * Get the {@link ArchiveInfo}.
     * <p>
     * TODO: allows us to show the info contained to the user without starting an actual import.
     *
     * @param context Current context
     *
     * @return the info bundle, or {@code null} if the archive does not provide info
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @Nullable
    ArchiveInfo getArchiveInfo(@NonNull final Context context)
            throws InvalidArchiveException, IOException {
        if (mArchiveInfo == null) {
            try (ArchiveReader reader = createArchiveReader(context)) {
                mArchiveInfo = reader.readHeader(context);
            }
        }
        return mArchiveInfo;
    }


    public void setImportEntry(@NonNull final ArchiveReaderRecord.Type recordType,
                               final boolean isSet) {
        if (isSet) {
            mImportEntries.add(recordType);
        } else {
            mImportEntries.remove(recordType);
        }
    }

    @NonNull
    public Set<ArchiveReaderRecord.Type> getImportEntries() {
        return mImportEntries;
    }


    boolean isSkipUpdates() {
        return (mOptions & (OPTION_UPDATES_MAY_OVERWRITE | OPTION_UPDATES_MUST_SYNC)) == 0;
    }

    void setSkipUpdates() {
        mOptions &= ~OPTION_UPDATES_MAY_OVERWRITE;
        mOptions &= ~OPTION_UPDATES_MUST_SYNC;
    }

    public boolean isUpdatesMayOverwrite() {
        return (mOptions & OPTION_UPDATES_MAY_OVERWRITE) != 0
               && (mOptions & OPTION_UPDATES_MUST_SYNC) == 0;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void setUpdatesMayOverwrite() {
        mOptions |= OPTION_UPDATES_MAY_OVERWRITE;
        mOptions &= ~OPTION_UPDATES_MUST_SYNC;
    }

    public boolean isUpdatesMustSync() {
        return (mOptions & (OPTION_UPDATES_MAY_OVERWRITE | OPTION_UPDATES_MUST_SYNC)) != 0;
    }

    void setUpdatesMustSync() {
        mOptions |= OPTION_UPDATES_MAY_OVERWRITE;
        mOptions |= OPTION_UPDATES_MUST_SYNC;
    }

    @Options
    public int getOptions() {
        return mOptions;
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner options = new StringJoiner(",", "[", "]");
        if ((mOptions & OPTION_UPDATES_MAY_OVERWRITE) != 0) {
            options.add("UPDATES_MAY_OVERWRITE");
        }
        if ((mOptions & OPTION_UPDATES_MUST_SYNC) != 0) {
            options.add("UPDATES_MUST_SYNC");
        }

        return "ImportHelper{"
               + "mImportEntries=" + mImportEntries
               + ", mOptions=0b" + Integer.toBinaryString(mOptions) + ": " + options.toString()
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveType
               + ", mArchiveInfo=" + mArchiveInfo
               + '}';
    }

    @IntDef(flag = true, value = {OPTION_UPDATES_MAY_OVERWRITE, OPTION_UPDATES_MUST_SYNC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Options {

    }
}

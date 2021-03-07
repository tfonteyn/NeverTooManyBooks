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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvRecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonRecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlRecordWriter;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Detecting record encoding in {@link #getEncoding} is based purely on filename extension.
 */
public enum RecordEncoding {
    Xml(".xml", ".+\\.xml$"),
    Csv(".csv", ".+\\.csv$"),
    Json(".json", ".+\\.json$"),
    Cover(".jpg", ".+\\.(?:jpg|png|jpeg)$");

    /** Log tag. */
    private static final String TAG = "RecordEncoding";

    @NonNull
    private final Pattern mPattern;

    @NonNull
    private final String mExtension;

    /**
     * Constructor.
     *
     * @param extension will be used for <strong>writing</strong> files.
     * @param pattern   will be used for <strong>detecting/reading</strong> files.
     */
    RecordEncoding(@NonNull final String extension,
                   @NonNull final String pattern) {
        mExtension = extension;
        mPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Detect the encoding based on the passed name.
     *
     * @param entryName to get the type of (case insensitive)
     *
     * @return the encoding
     */
    @NonNull
    public static Optional<RecordEncoding> getEncoding(@NonNull final String entryName) {
        final String name = entryName.toLowerCase(AppLocale.getInstance().getSystemLocale());

        // (faster?) shortcut check for covers
        if (name.endsWith(".jpg")) {
            return Optional.of(Cover);
        }

        for (final RecordEncoding recordEncoding : values()) {
            if (recordEncoding.mPattern.matcher(name).find()) {
                return Optional.of(recordEncoding);
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Logger.w(TAG, "getEncoding|Unknown entry=" + entryName);
        }
        return Optional.empty();
    }

    /**
     * Get the file extension for writing an output file.
     *
     * @return file name extension starting with a '.'
     */
    @NonNull
    public String getFileExt() {
        return mExtension;
    }

    @NonNull
    public RecordWriter createWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        switch (this) {
            case Json:
                return new JsonRecordWriter(utcSinceDateTime);
            case Csv:
                return new CsvRecordWriter(utcSinceDateTime);
            case Xml:
                return new XmlRecordWriter(utcSinceDateTime);
            case Cover:
                // Not useful, won't implement. It's just a File copy operation
                throw new IllegalStateException(ArchiveWriter.ERROR_NO_WRITER_AVAILABLE);
            default:
                break;
        }
        throw new IllegalStateException(ArchiveWriter.ERROR_NO_WRITER_AVAILABLE);
    }

    @NonNull
    public RecordReader createReader(@NonNull final Context context,
                                     @NonNull final BookDao bookDao,
                                     @NonNull final Set<RecordType> importEntriesAllowed)
            throws InvalidArchiveException {
        switch (this) {
            case Json:
                return new JsonRecordReader(context, bookDao, importEntriesAllowed);
            case Csv:
                return new CsvRecordReader(context, bookDao);
            case Xml:
                //noinspection deprecation
                return new XmlRecordReader(context, importEntriesAllowed);
            case Cover:
                // discourage creating a new CoverRecordReader for each cover.
                throw new IllegalStateException("CoverRecordReader should be re-used");
                // return new CoverRecordReader();
            default:
                break;
        }
        throw new InvalidArchiveException(ArchiveReader.ERROR_NO_READER_AVAILABLE);
    }
}

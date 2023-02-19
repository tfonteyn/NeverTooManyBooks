/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonRecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlRecordReader;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleListUtils;

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
    private final Pattern pattern;

    @NonNull
    private final String extension;

    /**
     * Constructor.
     *
     * @param extension will be used for <strong>writing</strong> files.
     * @param pattern   will be used for <strong>detecting/reading</strong> files.
     */
    RecordEncoding(@NonNull final String extension,
                   @NonNull final String pattern) {
        this.extension = extension;
        this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
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
        final String name = entryName.toLowerCase(LocaleListUtils.getSystemLocale());

        // (faster?) shortcut check for covers
        if (name.endsWith(".jpg")) {
            return Optional.of(Cover);
        }

        for (final RecordEncoding recordEncoding : values()) {
            if (recordEncoding.pattern.matcher(name).find()) {
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
        return extension;
    }

    /**
     * Create a {@link RecordWriter} for this encoding.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     *
     * @return {@link RecordWriter}
     */
    @NonNull
    public RecordWriter createWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        switch (this) {
            case Json:
                return new JsonRecordWriter(utcSinceDateTime);
                case Cover:
                // Not useful, won't implement. It's just a File copy operation
            case Xml:
            case Csv:
                // No longer supported
            default:
                break;
        }
        throw new IllegalStateException(DataWriter.ERROR_NO_WRITER_AVAILABLE);
    }

    /**
     * Create a {@link RecordReader} for this encoding.
     *
     * @param context              Current context
     * @param importEntriesAllowed the record types which the reader
     *                             will be <strong>allowed</strong> to read
     *
     * @return Optional reader
     *
     * @throws IllegalStateException if requesting a known but invalid reader
     *                               (i.e. the developer made a boo-boo)
     */
    @NonNull
    public Optional<RecordReader> createReader(@NonNull final Context context,
                                               @NonNull
                                               final Set<RecordType> importEntriesAllowed) {
        switch (this) {
            case Json:
                return Optional.of(new JsonRecordReader(context, importEntriesAllowed));
            case Csv:
                return Optional.of(new CsvRecordReader(context));
            case Xml:
                //noinspection deprecation
                return Optional.of(new XmlRecordReader(context));
            case Cover:
                // discourage creating a new CoverRecordReader for each cover.
                throw new IllegalStateException("CoverRecordReader should be re-used");
            default:
                return Optional.empty();
        }
    }
}

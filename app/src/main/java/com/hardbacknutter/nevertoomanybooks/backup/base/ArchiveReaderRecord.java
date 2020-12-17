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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Interface provided by every record read from an archive file.
 * This class effectively should wrap an archive format specific record in a format agnostic record.
 * <p>
 * Note we're also forcing the encapsulation of the {@link ArchiveReader} input stream.
 */
public interface ArchiveReaderRecord {

    /** Buffer size used by {@link #asString()} and {@link #asList()}. */
    /* private */ int BUFFER_SIZE = 65535;

    /**
     * Get the original "file name" (archive record name) of the object.
     *
     * @return name
     */
    @NonNull
    String getName();

    /**
     * Get the type of this record.
     *
     * @return Type
     */
    @NonNull
    Type getType();

    /**
     * Get the encoding of this record.
     *
     * @return Encoding
     */
    @NonNull
    Encoding getEncoding();

    /**
     * Get the last modification time of this archive record in EpochMilli.
     * <p>
     * Primarily used for cover files.
     *
     * @return EpochMilli
     */
    long getLastModifiedEpochMilli();

    /**
     * Get the stream to read the record from.
     * Callers <strong>MUST NOT</strong> close this stream.
     * Implementations should close it when appropriate.
     *
     * @return the InputStream
     *
     * @throws IOException on failure
     */
    @NonNull
    InputStream getInputStream()
            throws IOException;

    default String asString()
            throws IOException {
        // Don't close this stream
        final InputStream is = getInputStream();
        final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE);

        final StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        return content.toString();
    }

    default List<String> asList()
            throws IOException {
        // Don't close this stream
        final InputStream is = getInputStream();
        final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE);

        final List<String> content = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            content.add(line);
        }
        return content;
    }

    /**
     * Supported archive entry types.
     * An entry will either be handled directly inside the
     * {@link ArchiveReader} and {@link ArchiveWriter} classes, or preferably handed
     * over to a {@link RecordReader} and {@link RecordWriter} for second-level archives.
     * <p>
     * Not all implementations will support all options.
     */
    enum Type {
        //TODO: implement magic-byte checks just like we do on top-level archive files.
        // Checking the prefix of the file name is a bit shaky
        // It's fine for our zip/tar archives were we control the name
        // but NOT suitable for checking standalone files.
        // However, it's not a real issue for now:
        // Csv: can only reads books anyhow
        // Xml: can detect its own format; but importing standalone files is not supported
        // Json: can detect its own format; but importing standalone files is not supported

        InfoHeader("info"),
        Styles("styles"),
        Preferences("preferences"),
        Books("books"),
        Cover(""),
        /**
         * ENHANCE: A special type indicating the reader is supposed to auto-detect the type(s).
         * Not supported by all readers. Not sure this is a good approach though; might change.
         */
        AutoDetect(""),
        /** The record type could not be detected. */
        Unknown("");

        /** Log tag. */
        private static final String TAG = "Type";

        @NonNull
        private final String mPrefix;

        Type(@NonNull final String prefix) {
            mPrefix = prefix;
        }

        /**
         * Detect the type of the passed name.
         *
         * @param entryName to get the type of (case insensitive)
         *
         * @return the record type
         */
        @NonNull
        public static Type getType(@NonNull final String entryName) {
            final String name = entryName.toLowerCase(AppLocale.getInstance().getSystemLocale());

            for (final Type type : Type.values()) {
                if (name.startsWith(type.mPrefix)) {
                    return type;
                }
            }

            Logger.warn(App.getAppContext(), TAG, "getType|Unknown entry=" + entryName);
            return Unknown;
        }
    }

    /**
     * Detecting record encoding is based purely on filename extension.
     */
    enum Encoding {
        Xml(".+\\.xml$"),
        Csv(".+\\.csv$"),
        Json(".+\\.json$"),
        Cover(".+\\.(?:jpg|png)$"),
        Unknown(".*");

        /** Log tag. */
        private static final String TAG = "Encoding";

        @NonNull
        private final Pattern mPattern;

        Encoding(@NonNull final String pattern) {
            mPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        /**
         * Detect the type of the passed name.
         *
         * @param entryName to get the type of (case insensitive)
         *
         * @return the encoding
         */
        @NonNull
        public static Encoding getEncoding(@NonNull final String entryName) {
            final String name = entryName.toLowerCase(AppLocale.getInstance().getSystemLocale());

            // (faster?) shortcut check for covers
            if (name.endsWith(".jpg") || name.endsWith(".png")) {
                return Cover;
            }

            for (final Encoding recordEncoding : Encoding.values()) {
                if (recordEncoding.mPattern.matcher(name).find()) {
                    return recordEncoding;
                }
            }

            Logger.warn(App.getAppContext(), TAG, "getEncoding|Unknown entry=" + entryName);
            return Unknown;
        }
    }
}

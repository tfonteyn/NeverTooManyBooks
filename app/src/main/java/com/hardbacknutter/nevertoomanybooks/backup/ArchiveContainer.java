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

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Archive formats (partially) supported.
 */
public enum ArchiveContainer {
    /** The default full backup/restore support. Text files are compressed, images are not. */
    Zip,

    /** Books as a CSV file; full support for export/import. */
    CsvBooks,

    /** XML <strong>Export only</strong>. */
    Xml,
    /** Database. */
    SqLiteDb,

    /** The legacy full backup/restore support. NOT compressed. */
    Tar,

    /** The archive we tried to read from was not identified. */
    Unknown;

    public static final String IMPORT_NOT_SUPPORTED = "Type not supported here";

    /**
     * Constructor. Determine which type the input file is.
     *
     * @param context Current context
     * @param uri     file to read
     *
     * @return type
     */
    public static ArchiveContainer create(@NonNull final Context context,
                                          @NonNull final Uri uri) {

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                // read the "magic bytes": https://en.wikipedia.org/wiki/List_of_file_signatures
                final byte[] b = new byte[0x200];
                final int len = is.read(b);

                // zip file, offset 0, "PK{3}{4}"
                if (len > 4
                    && b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04) {
                    return Zip;
                }

                // xml file, offset 0, the string "<?xml "
                if (len > 5
                    && b[0] == 0x3c && b[1] == 0x3f && b[2] == 0x78 && b[3] == 0x6d
                    && b[4] == 0x6c && b[5] == 0x20) {
                    return Xml;
                }

                // tar file: offset 0x101, the string "ustar"
                if (len > 0x110
                    && b[0x101] == 0x75 && b[0x102] == 0x73 && b[0x103] == 0x74
                    && b[0x104] == 0x61 && b[0x105] == 0x72) {
                    return Tar;
                }

                // sqlite v3, offset 0, 53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00
                // the string "SQLite format 3"
                if (len > 16
                    && b[0] == 0x53 && b[1] == 0x51 && b[2] == 0x4c && b[3] == 0x69
                    && b[4] == 0x74 && b[5] == 0x65 && b[6] == 0x20 && b[7] == 0x66
                    && b[8] == 0x6f && b[9] == 0x72 && b[10] == 0x6d && b[11] == 0x61
                    && b[12] == 0x74 && b[13] == 0x20 && b[14] == 0x33 && b[15] == 0x00) {
                    return SqLiteDb;
                }
            }
        } catch (@NonNull final IOException ignore) {
            // ignore
        }

        // If the magic bytes check did not work out,
        // we check for it being a CSV by looking at the extension.
        // Allow some name variations:"file.csv", "file.csv (1)" etc
        final Pair<String, Long> uriInfo = FileUtils.getUriInfo(context, uri);
        if (uriInfo != null && uriInfo.first != null) {
            final Pattern csvFilePattern =
                    Pattern.compile("^.*\\.csv( \\(\\d+\\))?$",
                                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (csvFilePattern.matcher(uriInfo.first).find()) {
                return CsvBooks;
            }
        }
        // give up.
        return Unknown;
    }

    /**
     * Get the <strong>proposed</strong> archive file extension.
     *
     * @return file name extension starting with a '.'
     */
    @NonNull
    public String getFileExt() {
        switch (this) {
            case Zip:
                return ".zip";
            case Xml:
                return ".xml";
            case CsvBooks:
                return ".csv";
            case SqLiteDb:
                return ".db";
            case Tar:
                return ".tar";

            case Unknown:
            default:
                throw new IllegalArgumentException(name());
        }
    }
}

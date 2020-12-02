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

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Archive types (formats) (partially) supported.
 * <p>
 * This is the top level, i.e. the actual file we read/write.
 * Handled by {@link ArchiveReader} and {@link ArchiveWriter}.
 */
public enum ArchiveType {
    /** The default full backup/restore support. Text files are compressed, images are not. */
    Zip(".zip"),
    /** Books as a CSV file; full support for export/import. */
    Csv(".csv"),
    /**
     * Books as a JSON file; full support for export/import.
     * <p>
     * ENHANCE: JSON export/import is experimental, not exposed to the user yet.
     * Added as top-level for easy testing only.
     * Real usage will likely be limited as part of a zip archive.
     */
    Json(".json"),
    /** XML <strong>Export only</strong>. */
    Xml(".xml"),
    /** Database. */
    SqLiteDb(".db"),
    /** The legacy full backup/restore support. NOT compressed. */
    Tar(".tar"),
    /** The archive we tried to read from was not identified. */
    Unknown("");

    public static final String ERROR_NO_READER_AVAILABLE = "No reader available";
    public static final String ERROR_NO_WRITER_AVAILABLE = "No writer available";

    @NonNull
    private final String mExtension;

    ArchiveType(@NonNull final String extension) {
        mExtension = extension;
    }

    /**
     * Detect the type based on the input Uri.
     *
     * @param context Current context
     * @param uri     to read
     *
     * @return ArchiveType
     */
    @NonNull
    public static ArchiveType fromUri(@NonNull final Context context,
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

                // xml file, offset 0, the string "<?xml "
                if (len > 5
                    && b[0] == 0x3c && b[1] == 0x3f && b[2] == 0x78 && b[3] == 0x6d
                    && b[4] == 0x6c && b[5] == 0x20) {
                    return Xml;
                }


                // csv book file as we write them out, offset 0, the string "\"_id\","
                // Obviously not foolproof but good enough.
                if (len > 6
                    && b[0] == 0x22 && b[1] == 0x5f && b[2] == 0x69
                    && b[3] == 0x64 && b[4] == 0x22 && b[5] == 0x2c) {
                    return Csv;
                }

                // json file as we write them out, offset 0, the string "{\""
                // This is very shaky, the { and " can have all sorts of whitespace in between.
                // We check for none as this is how we write our own json files.
                // Aside of that, checking only two bytes is of questionable quality.
                // HOWEVER: top-level json files are not supported by the UI!
                // We use them for testing only.
                if (len > 2
                    && b[0] == 0x7b && b[1] == 0x22) {
                    return Json;
                }
            }
        } catch (@NonNull final IOException ignore) {
            // ignore
        }

        // If the magic bytes check did not work out for csv/json,
        // we look at the extension allowing for some name variations:
        // "file.ext", "file.ext (1)", "file.ext (2)" etc

        // Note this is using the "display name" of the Uri.
        // It MIGHT be correct, but equally it MIGHT be unusable.
        // This MIGHT depend on Android version/device.
        final FileUtils.UriInfo uriInfo = FileUtils.getUriInfo(context, uri);
        if (uriInfo != null && uriInfo.displayName != null) {
            Pattern pattern = Pattern.compile("^.*\\.csv( \\(\\d+\\))?$",
                                              Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (pattern.matcher(uriInfo.displayName).find()) {
                return Csv;
            }

            pattern = Pattern.compile("^.*\\.json( \\(\\d+\\))?$",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (pattern.matcher(uriInfo.displayName).find()) {
                return Json;
            }
        }

        // give up.
        return Unknown;
    }

    /**
     * Create an {@link ArchiveReader} based on the type.
     *
     * @param context Current context
     * @param helper  reader configuration
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws IOException             on other failures
     */
    @NonNull
    public ArchiveReader createArchiveReader(@NonNull final Context context,
                                             @NonNull final ImportHelper helper)
            throws InvalidArchiveException, IOException {

        final ArchiveReader reader;
        switch (this) {
            case Zip:
                reader = new ZipArchiveReader(context, helper);
                break;

            case Tar:
                reader = new TarArchiveReader(context, helper);
                break;

            case Csv:
                reader = new CsvArchiveReader(helper);
                break;

            case SqLiteDb:
                reader = new DbArchiveReader(context, helper);
                break;

            case Json:
                reader = new JsonArchiveReader(helper);
                break;

            case Xml:
            case Unknown:
            default:
                throw new InvalidArchiveException(ERROR_NO_READER_AVAILABLE);
        }

        reader.validate(context);
        return reader;
    }


    /**
     * Create an {@link ArchiveWriter} based on the type.
     *
     * @param context Current context
     * @param helper  writer configuration
     *
     * @return a new writer
     *
     * @throws InvalidArchiveException on failure to produce a supported writer
     * @throws IOException             on other failures
     */
    @NonNull
    public ArchiveWriter createArchiveWriter(@NonNull final Context context,
                                             @NonNull final ExportHelper helper)
            throws InvalidArchiveException, IOException {

        switch (this) {
            case Zip:
                return new ZipArchiveWriter(context, helper);

            case Xml:
                return new XmlArchiveWriter(context, helper);

            case Csv:
                return new CsvArchiveWriter(helper);

            case Tar:
                return new TarArchiveWriter(context, helper);

            case SqLiteDb:
                return new DbArchiveWriter(helper);

            case Json:
                return new JsonArchiveWriter(helper);

            case Unknown:
            default:
                throw new InvalidArchiveException(ERROR_NO_WRITER_AVAILABLE);
        }
    }

    /**
     * Get the <strong>proposed</strong> archive file extension for writing an output file.
     *
     * @return file name extension starting with a '.'
     */
    @NonNull
    public String getFileExt() {
        return mExtension;
    }
}

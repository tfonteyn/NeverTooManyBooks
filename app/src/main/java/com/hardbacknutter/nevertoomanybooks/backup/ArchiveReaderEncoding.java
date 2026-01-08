/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveReader;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Archive encoding (formats) (partially) supported.
 * <p>
 * This is the top level, i.e. the actual file we read/write.
 */
public enum ArchiveReaderEncoding
        implements Parcelable {
    /**
     * The default full backup/restore support.
     * Will contain all data as json files + all cover images.
     */
    Zip,

    /**
     * Books as a CSV file.
     * <p>
     * Supports importing from:
     * <ul>
     *     <li>BookCatalogue / Legacy NTMB 1.0-3.x</li>
     *     <li>Goodreads</li>
     * </ul>
     */
    Csv,

    /**
     * Will contain all data in a single JSON file. No images.
     * Full support for export/import.
     */
    Json,

    /** The legacy BC archive restore support. NOT compressed. */
    Tar,

    /** Database. Recognized, but not supported for now. */
    SqLiteDb;

    /** {@link Parcelable}. */
    public static final Creator<ArchiveReaderEncoding> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ArchiveReaderEncoding createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public ArchiveReaderEncoding[] newArray(final int size) {
            return new ArchiveReaderEncoding[size];
        }
    };

    /** Log tag. */
    private static final String TAG = "ArchiveEncoding";

    /**
     * Detect the encoding based on the input Uri.
     *
     * @param context Current context
     * @param uri     to read
     *
     * @return ArchiveEncoding
     *
     * @throws FileNotFoundException if the uri cannot be resolved
     * @throws IllegalStateException if the uri scheme is not supported
     */
    @SuppressWarnings({"MagicNumber", "CheckStyle"})
    @NonNull
    public static Optional<ArchiveReaderEncoding> getEncoding(@NonNull final Context context,
                                                              @NonNull final Uri uri)
            throws FileNotFoundException {

        final String scheme = uri.getScheme();
        if (scheme != null && scheme.startsWith("http")) {
            throw new IllegalStateException("No support for http uri's");
        }

        // The below is all for file based imports
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                // read the "magic bytes": https://en.wikipedia.org/wiki/List_of_file_signatures
                final byte[] b = new byte[0x200];
                final int len = is.read(b);

                // zip file, offset 0, "PK{3}{4}"
                if (len > 4
                    && b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04) {
                    return Optional.of(Zip);
                }

                // tar file: offset 0x101, the string "ustar"
                if (len > 0x110
                    && b[0x101] == 0x75 && b[0x102] == 0x73 && b[0x103] == 0x74
                    && b[0x104] == 0x61 && b[0x105] == 0x72) {
                    return Optional.of(Tar);
                }

                // sqlite v3, offset 0, 53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00
                // the string "SQLite format 3"
                if (len > 16
                    && b[0] == 0x53 && b[1] == 0x51 && b[2] == 0x4c && b[3] == 0x69
                    && b[4] == 0x74 && b[5] == 0x65 && b[6] == 0x20 && b[7] == 0x66
                    && b[8] == 0x6f && b[9] == 0x72 && b[10] == 0x6d && b[11] == 0x61
                    && b[12] == 0x74 && b[13] == 0x20 && b[14] == 0x33 && b[15] == 0x00) {
                    return Optional.of(SqLiteDb);
                }

                // csv book file as we write them out, offset 0, the string "\"_id\","
                // Obviously not foolproof but good enough.
                if (len > 6
                    && b[0] == 0x22 && b[1] == 0x5f && b[2] == 0x69
                    && b[3] == 0x64 && b[4] == 0x22 && b[5] == 0x2c) {
                    return Optional.of(Csv);
                }

                // JSON file as we write them out, offset 0, the string "{\""
                // This is very shaky, the { and " can have all sorts of whitespace in between.
                // We check for none as this is how we write our own JSON files.
                // Aside of that, checking only two bytes is of questionable quality.
                // HOWEVER: top-level json files are not supported by the UI!
                // We use them for testing only.
                if (len > 2
                    && b[0] == 0x7b && b[1] == 0x22) {
                    return Optional.of(Json);
                }
            }
        } catch (@NonNull final FileNotFoundException e) {
            throw e;

        } catch (@NonNull final IOException e) {
            // log in debug, but otherwise skip to extension detection
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().e(TAG, e, "uri=" + uri);
            }
        }

        // If the magic bytes check did not work out for csv/json,
        // we look at the extension allowing for some name variations:
        // "file.ext", "file.ext (1)", "file.ext (2)" etc

        // Note this is using the "display name" of the Uri.
        // It MIGHT be correct, but equally it MIGHT be unusable.
        // This MIGHT depend on Android version/device.

        final String displayName = new UriInfo(uri).getDisplayName(context);

        Pattern pattern;

        pattern = Pattern.compile("^.*\\.csv( \\(\\d+\\))?$",
                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        if (pattern.matcher(displayName).find()) {
            return Optional.of(Csv);
        }

        pattern = Pattern.compile("^.*\\.json( \\(\\d+\\))?$",
                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (pattern.matcher(displayName).find()) {
            return Optional.of(Json);
        }

        // give up.
        return Optional.empty();
    }

    /**
     * Create an {@link DataReader} based on the type.
     *
     * @param context      Current context
     * @param uri          to read from
     * @param updateOption options
     * @param recordTypes  the record types to accept and read
     *
     * @return a new reader
     *
     * @throws DataReaderException      if the input is not recognized
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if there are no record types set
     * @throws CredentialsException     on authentication/login failures
     * @see DataReader
     */
    @WorkerThread
    @NonNull
    DataReader<ArchiveMetaData, ImportResults> createReader(
            @NonNull final Context context,
            @NonNull final Uri uri,
            @NonNull final DataReader.Updates updateOption,
            @NonNull final Set<RecordType> recordTypes)
            throws DataReaderException,
                   CredentialsException,
                   IOException {

        if (recordTypes.isEmpty()) {
            throw new IllegalArgumentException("no recordTypes set");
        }

        final DataReader<ArchiveMetaData, ImportResults> reader;
        switch (this) {
            case Zip: {
                reader = new ZipArchiveReader(context, uri, updateOption, recordTypes);
                break;
            }
            case Tar: {
                reader = new TarArchiveReader(context, uri, updateOption, recordTypes);
                break;
            }
            case Csv: {
                reader = new CsvArchiveReader(uri, updateOption);
                break;
            }
            case SqLiteDb: {
                reader = new DbArchiveReader(uri, updateOption);
                break;
            }
            case Json: {
                reader = new JsonArchiveReader(uri, updateOption, recordTypes);
                break;
            }
            default:
                throw new DataReaderException(context.getString(
                        R.string.error_file_not_recognized));
        }

        reader.validate(context);
        return reader;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(ordinal());
    }
}

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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServerWriter;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Archive encoding (formats) (partially) supported.
 * <p>
 * This is the top level, i.e. the actual file we read/write.
 * Handled by {@link ArchiveReader} and {@link ArchiveWriter}.
 */
public enum ArchiveEncoding
        implements Parcelable {
    /** The default full backup/restore support. Text files are compressed, images are not. */
    Zip(".zip"),
    /** Books as a CSV file; full support for export/import. */
    Csv(".csv"),
    /** Books, Styles, Preferences in a JSON file; full support for export/import. */
    Json(".json"),
    /** XML <strong>Export only</strong>. */
    Xml(".xml"),
    /** Database. */
    SqLiteDb(".db"),
    /** The legacy full backup/restore support. NOT compressed. */
    Tar(".tar"),
    /** A Calibre Content Server. */
    CalibreCS(null);

    /** {@link Parcelable}. */
    public static final Creator<ArchiveEncoding> CREATOR = new Creator<ArchiveEncoding>() {
        @Override
        @NonNull
        public ArchiveEncoding createFromParcel(@NonNull final Parcel in) {
            return ArchiveEncoding.values()[in.readInt()];
        }

        @Override
        @NonNull
        public ArchiveEncoding[] newArray(final int size) {
            return new ArchiveEncoding[size];
        }
    };
    /* Log tag. */
    private static final String TAG = "ArchiveEncoding";
    /**
     * The proposed archive filename extension to write to.
     * Will be {@code null} for archives which are not files; e.g. a remote server.
     */
    @Nullable
    private final String mFileExt;

    /**
     * Constructor.
     *
     * @param fileExt to use as the proposed archive filename extension
     *                or {@code null} for remote server "archives"
     */
    ArchiveEncoding(@Nullable final String fileExt) {
        mFileExt = fileExt;
    }

    /**
     * Detect the encoding based on the input Uri.
     *
     * @param context Current context
     * @param uri     to read
     *
     * @return ArchiveEncoding
     */
    @NonNull
    public static Optional<ArchiveEncoding> getEncoding(@NonNull final Context context,
                                                        @NonNull final Uri uri)
            throws FileNotFoundException {

        final String scheme = uri.getScheme();
        if (scheme != null && scheme.startsWith("http")) {
            // not supported for now... intention is to add detection for remote servers.
            throw new IllegalStateException();
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

                // xml file, offset 0, the string "<?xml "
                if (len > 5
                    && b[0] == 0x3c && b[1] == 0x3f && b[2] == 0x78 && b[3] == 0x6d
                    && b[4] == 0x6c && b[5] == 0x20) {
                    return Optional.of(Xml);
                }


                // csv book file as we write them out, offset 0, the string "\"_id\","
                // Obviously not foolproof but good enough.
                if (len > 6
                    && b[0] == 0x22 && b[1] == 0x5f && b[2] == 0x69
                    && b[3] == 0x64 && b[4] == 0x22 && b[5] == 0x2c) {
                    return Optional.of(Csv);
                }

                // json file as we write them out, offset 0, the string "{\""
                // This is very shaky, the { and " can have all sorts of whitespace in between.
                // We check for none as this is how we write our own json files.
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
                Logger.d(TAG, "getEncoding", e, "uri=" + uri.toString());
            }
        }

        // If the magic bytes check did not work out for csv/json,
        // we look at the extension allowing for some name variations:
        // "file.ext", "file.ext (1)", "file.ext (2)" etc

        // Note this is using the "display name" of the Uri.
        // It MIGHT be correct, but equally it MIGHT be unusable.
        // This MIGHT depend on Android version/device.
        final FileUtils.UriInfo uriInfo = FileUtils.getUriInfo(context, uri);
        Pattern pattern;

        pattern = Pattern.compile("^.*\\.csv( \\(\\d+\\))?$",
                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (pattern.matcher(uriInfo.getDisplayName()).find()) {
            return Optional.of(Csv);
        }

        pattern = Pattern.compile("^.*\\.json( \\(\\d+\\))?$",
                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (pattern.matcher(uriInfo.getDisplayName()).find()) {
            return Optional.of(Json);
        }

        pattern = Pattern.compile("^.*\\.xml( \\(\\d+\\))?$",
                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (pattern.matcher(uriInfo.getDisplayName()).find()) {
            return Optional.of(Xml);
        }

        // give up.
        return Optional.empty();
    }


    public boolean isFile() {
        return mFileExt != null;
    }

    /**
     * Get the <strong>proposed</strong> archive file extension for writing an output file.
     *
     * @return file name extension starting with a '.'
     */
    @NonNull
    public String getFileExt() {
        if (!isFile()) {
            throw new IllegalStateException("Not a file");
        }
        return mFileExt;
    }

    public boolean isRemoteServer() {
        return mFileExt == null;
    }

    @StringRes
    public int getRemoteServerDescriptionResId() {
        if (!isRemoteServer()) {
            throw new IllegalStateException("Not a remote server");
        }
        // WARNING: hardcoded for now as we only have one of these.
        return R.string.lbl_calibre_content_server;
    }

    /**
     * Create an {@link ArchiveWriter} based on the type.
     *
     * @param context Current context
     * @param helper  writer configuration
     *
     * @return a new writer
     *
     * @throws GeneralParsingException on a decoding/parsing of data issue
     * @throws IOException             on failures
     */
    @NonNull
    public ArchiveWriter createWriter(@NonNull final Context context,
                                      @NonNull final ExportHelper helper)
            throws GeneralParsingException, IOException,
                   CertificateException, KeyManagementException {

        switch (this) {
            case Zip:
                return new ZipArchiveWriter(context, helper);

            case Xml:
                return new XmlArchiveWriter(helper);

            case Csv:
                return new CsvArchiveWriter(helper);

            case SqLiteDb:
                return new DbArchiveWriter(helper);

            case Json:
                return new JsonArchiveWriter(helper);

            case CalibreCS:
                return new CalibreContentServerWriter(context, helper);

            case Tar:
                // writing to tar is no longer supported
            default:
                // reminder:do NOT use a InvalidArchiveException which is for readers only.
                throw new IllegalStateException(ArchiveWriter.ERROR_NO_WRITER_AVAILABLE);
        }
    }

    /**
     * Create an {@link ArchiveReader} based on the type.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws GeneralParsingException on a decoding/parsing of data issue
     * @throws IOException             on other failures
     */
    @NonNull
    @WorkerThread
    public ArchiveReader createReader(@NonNull final Context context,
                                      @NonNull final ImportHelper helper)
            throws InvalidArchiveException, GeneralParsingException,
                   IOException, CertificateException, KeyManagementException {

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

            case CalibreCS:
                reader = new CalibreContentServerReader(context, helper);
                break;

            case Xml:
                // reading from xml is not supported
            default:
                throw new InvalidArchiveException(ArchiveReader.ERROR_NO_READER_AVAILABLE);
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
        dest.writeInt(this.ordinal());
    }
}

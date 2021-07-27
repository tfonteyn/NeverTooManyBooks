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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerWriter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoReader;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoWriter;

public enum SyncServer
        implements Parcelable {

    /** A Calibre Content Server. */
    CalibreCS(R.string.lbl_calibre_content_server, true),
    /** StripInfo web site. */
    StripInfo(R.string.site_stripinfo_be, false);

    /** {@link Parcelable}. */
    public static final Creator<SyncServer> CREATOR = new Creator<SyncServer>() {
        @Override
        @NonNull
        public SyncServer createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public SyncServer[] newArray(final int size) {
            return new SyncServer[size];
        }
    };
    /* Log tag. */
    private static final String TAG = "SyncServer";
    /** The (optional) preset encoding to pass to export/import. */
    public static final String BKEY_SITE = TAG + ":encoding";
    @StringRes
    private final int mLabel;

    private final boolean mHasLastUpdateDateField;

    SyncServer(@StringRes final int label,
               final boolean hasLastUpdateDateField) {
        mLabel = label;
        mHasLastUpdateDateField = hasLastUpdateDateField;
    }


    /** A short label. Used in drop down menus and similar. */
    @StringRes
    public int getLabel() {
        return mLabel;
    }

    public boolean isEnabled(@NonNull final SharedPreferences global) {
        switch (this) {
            case CalibreCS:
                return CalibreHandler.isSyncEnabled(global);
            case StripInfo:
                return StripInfoHandler.isSyncEnabled(global);

            default:
                throw new IllegalArgumentException();
        }
    }

    public boolean hasLastUpdateDateField() {
        return mHasLastUpdateDateField;
    }

    /**
     * Create an {@link SyncWriter} based on the type.
     *
     * @param context Current context
     * @param config  writer configuration
     *
     * @return a new writer
     *
     * @throws CertificateException on failures related to a user installed CA.
     * @throws SSLException         on secure connection failures
     */
    @NonNull
    public SyncWriter createWriter(@NonNull final Context context,
                                   @NonNull final SyncWriterConfig config)
            throws CertificateException,
                   SSLException {

        if (BuildConfig.DEBUG /* always */) {
            if (config.getExporterEntries().isEmpty()) {
                throw new IllegalStateException("getExporterEntries().isEmpty()");
            }
        }

        switch (this) {
            case CalibreCS:
                return new CalibreContentServerWriter(context, config);

            case StripInfo:
                return new StripInfoWriter(config);

            default:
                throw new IllegalStateException(SyncWriter.ERROR_NO_WRITER_AVAILABLE);
        }
    }

    /**
     * Create an {@link SyncReader} based on the type.
     *
     * @param context Current context
     * @param config  import configuration
     *
     * @return a new reader
     *
     * @throws ImportException      on a decoding/parsing of data issue
     * @throws CertificateException on failures related to a user installed CA.
     * @throws SSLException         on secure connection failures
     * @throws IOException          on other failures
     */
    @NonNull
    @WorkerThread
    public SyncReader createReader(@NonNull final Context context,
                                   @NonNull final SyncReaderConfig config)
            throws ImportException,
                   CertificateException,
                   IOException {

        if (BuildConfig.DEBUG /* always */) {
            if (config.getImportEntries().isEmpty()) {
                throw new IllegalStateException("getImportEntries() is empty");
            }
        }

        final SyncReader reader;
        switch (this) {
            case CalibreCS:
                reader = new CalibreContentServerReader(context, config);
                break;

            case StripInfo:
                reader = new StripInfoReader(context, config);
                break;

            default:
                throw new IllegalStateException(SyncReader.ERROR_NO_READER_AVAILABLE);
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

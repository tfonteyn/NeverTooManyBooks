/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerWriter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoReader;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoWriter;

/**
 * Note: {@link #hasLastUpdateDateField} / {@link #syncDateIsUserEditable}:
 * It's debatable that we could just use {@link #hasLastUpdateDateField} for both meanings.
 */
public enum SyncServer
        implements Parcelable {

    /** A Calibre Content Server. */
    CalibreCS(R.string.lbl_calibre_content_server, true, true),
    /** StripInfo web site. */
    StripInfo(R.string.site_stripinfo_be, false, false);

    /** {@link Parcelable}. */
    public static final Creator<SyncServer> CREATOR = new Creator<>() {
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

    private static final String ERROR_NO_READER_AVAILABLE = "No reader available";
    /* Log tag. */
    private static final String TAG = "SyncServer";
    /** The (optional) preset encoding to pass to export/import. */
    public static final String BKEY_SITE = TAG + ":encoding";
    @StringRes
    private final int labelResId;


    private final boolean hasLastUpdateDateField;
    private final boolean syncDateIsUserEditable;


    /**
     * Constructor.
     *
     * @param labelResId             will be displayed to the user
     * @param hasLastUpdateDateField whether the server provides a 'last update' field we can use
     * @param syncDateUserEditable   whether the user can manually influence the sync date
     */
    SyncServer(@StringRes final int labelResId,
               final boolean hasLastUpdateDateField,
               final boolean syncDateUserEditable) {
        this.labelResId = labelResId;
        this.hasLastUpdateDateField = hasLastUpdateDateField;
        syncDateIsUserEditable = syncDateUserEditable;
    }


    /** A short label. Used in drop down menus and similar. */
    @StringRes
    public int getLabelResId() {
        return labelResId;
    }

    /**
     * Check if this server is globally enabled.
     *
     * @param context Current context
     *
     * @return flag
     *
     * @throws IllegalArgumentException for invalid servers.
     */
    public boolean isEnabled(@NonNull final Context context) {
        switch (this) {
            case CalibreCS:
                return CalibreHandler.isSyncEnabled(context);
            case StripInfo:
                return StripInfoHandler.isSyncEnabled(context);

            default:
                throw new IllegalArgumentException(toString());
        }
    }

    boolean isSyncDateUserEditable() {
        return syncDateIsUserEditable;
    }

    /**
     * Check whether each book has a specific last-update date to
     * (help) sync it with the server/web site.
     *
     * @return {@code true} if a last-update date is available
     */
    boolean hasLastUpdateDateField() {
        return hasLastUpdateDateField;
    }

    /**
     * Create an {@link DataWriter} based on the type.
     *
     * @param context      Current context
     * @param helper       writer configuration
     * @param systemLocale to use for ISO date parsing
     *
     * @return a new writer
     *
     * @throws CertificateException     on failures related to a user installed CA.
     * @throws IllegalArgumentException if there are no record types set
     * @throws IllegalStateException    if there is no writer available (which would be a bug)
     */
    @NonNull
    DataWriter<SyncWriterResults> createWriter(@NonNull final Context context,
                                               @NonNull final SyncWriterHelper helper,
                                               @NonNull final Locale systemLocale)
            throws CertificateException {

        if (helper.getRecordTypes().isEmpty()) {
            throw new IllegalArgumentException("helper.getRecordTypes().isEmpty()");
        }

        switch (this) {
            case CalibreCS:
                return new CalibreContentServerWriter(context, helper, systemLocale);

            case StripInfo:
                return new StripInfoWriter(context, helper, systemLocale);

            default:
                throw new IllegalStateException(DataWriter.ERROR_NO_WRITER_AVAILABLE);
        }
    }

    /**
     * Create an {@link DataReader} based on the type.
     *
     * @param context       Current context
     * @param systemLocale  to use for ISO date parsing
     * @param updateOption  options
     * @param recordTypes   the record types to accept and read
     * @param syncProcessor synchronization configuration
     * @param syncDate      optional cut-off date
     * @param extraArgs     Bundle with reader specific arguments
     *
     * @return a new reader
     *
     * @throws DataReaderException      if the input is not recognized
     * @throws CredentialsException     on authentication/login failures
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if there are no record types set
     * @throws IllegalStateException    if there is no reader available
     * @see DataReader
     */
    @NonNull
    @WorkerThread
    DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull final Context context,
            @NonNull final Locale systemLocale,
            @NonNull final DataReader.Updates updateOption,
            @NonNull final Set<RecordType> recordTypes,
            @Nullable final SyncReaderProcessor syncProcessor,
            @Nullable final LocalDateTime syncDate,
            @NonNull final Bundle extraArgs)
            throws DataReaderException,
                   CertificateException,
                   CredentialsException,
                   IOException {

        if (recordTypes.isEmpty()) {
            throw new IllegalArgumentException("no recordTypes set");
        }

        final DataReader<SyncReaderMetaData, ReaderResults> reader;
        switch (this) {
            case CalibreCS:
                reader = new CalibreContentServerReader(context, systemLocale,
                                                        updateOption, recordTypes,
                                                        syncProcessor, syncDate,
                                                        extraArgs);
                break;

            case StripInfo:
                reader = new StripInfoReader(context,
                                             updateOption, recordTypes,
                                             syncProcessor);
                break;

            default:
                throw new IllegalStateException(ERROR_NO_READER_AVAILABLE);
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

    @Override
    @NonNull
    public String toString() {
        return "SyncServer{"
               + "labelResId=" + labelResId
               + ", hasLastUpdateDateField=" + hasLastUpdateDateField
               + ", syncDateIsUserEditable=" + syncDateIsUserEditable
               + '}';
    }
}

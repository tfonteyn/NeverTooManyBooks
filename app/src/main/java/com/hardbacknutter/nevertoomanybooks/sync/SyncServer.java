/*
 * @Copyright 2018-2025 HardBackNutter
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerWriter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoReader;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoSyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoWriter;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Note: {@link #hasLastUpdateDateField} / {@link #syncDateIsUserEditable}:
 * It's debatable that we could just use {@link #hasLastUpdateDateField} for both meanings.
 */
public enum SyncServer
        implements Parcelable {

    /** A Calibre Content Server. */
    CalibreCS(R.string.lbl_calibre_content_server, true, true) {
        public boolean isEnabled(@NonNull final Context context) {
            return CalibreHandler.isSyncEnabled(context);
        }

        @Override
        @WorkerThread
        @NonNull
        DataWriter<SyncWriterResults> createWriter(@NonNull final Context context,
                                                   @NonNull final Set<RecordType> recordTypes,
                                                   final boolean incremental,
                                                   final boolean deleteLocalBook)
                throws CertificateException {
            return new CalibreContentServerWriter(context, recordTypes,
                                                  incremental,
                                                  deleteLocalBook);
        }

        @Override
        @WorkerThread
        @NonNull
        DataReader<SyncReaderMetaData, ReaderResults> createReader(
                @NonNull final Context context,
                @NonNull final Set<RecordType> recordTypes,
                @Nullable final SyncReaderProcessor.Builder syncProcessorBuilder,
                @Nullable final LocalDateTime syncDate,
                @NonNull final DataReader.Updates updateOption,
                @NonNull final Bundle extraArgs)
                throws DataReaderException,
                       CertificateException,
                       CredentialsException,
                       IOException {

            // Use either the custom passed-in, or the built-in default.
            final SyncReaderProcessor syncProcessor =
                    Objects.requireNonNullElseGet(
                                   syncProcessorBuilder,
                                   () -> createSyncProcessorBuilder(context))
                           .build(context);

            final DataReader<SyncReaderMetaData, ReaderResults> reader =
                    new CalibreContentServerReader(context, recordTypes,
                                                   syncProcessor, syncDate,
                                                   updateOption,
                                                   extraArgs);
            reader.validate(context);
            return reader;
        }

        @Override
        public String getSyncPreferencePrefix() {
            return CalibreContentServer.PREFERENCE_KEY + FIELDS_UPDATE;
        }

        @Override
        @NonNull
        public SyncReaderProcessor.Builder createSyncProcessorBuilder(
                @NonNull final Context context) {
            final List<Locale> locales = LocaleListUtils.asList(context);
            final RealNumberParser realNumberParser = new RealNumberParser(locales);
            final SyncReaderProcessor.Builder builder =
                    new SyncReaderProcessor.Builder(context,
                                                    getSyncPreferencePrefix(),
                                                    realNumberParser);

            // Cover fields will be at the top of the list.
            builder.add(context.getString(R.string.lbl_cover_front),
                        new String[]{DBKey.COVER[0]});
            builder.add(context.getString(R.string.lbl_cover_back),
                        new String[]{DBKey.COVER[1]});

            // These fields will be locally sorted and come next on the list
            final SortedMap<String, String[]> map = new TreeMap<>();

            map.put(context.getString(R.string.lbl_description),
                    new String[]{DBKey.DESCRIPTION});
            map.put(context.getString(R.string.lbl_format),
                    new String[]{DBKey.FORMAT});
            map.put(context.getString(R.string.lbl_language),
                    new String[]{DBKey.LANGUAGE});
            map.put(context.getString(R.string.lbl_date_published),
                    new String[]{DBKey.PUBLICATION_DATE});
            map.put(context.getString(R.string.lbl_title),
                    new String[]{DBKey.TITLE});

            map.put(context.getString(R.string.lbl_authors),
                    new String[]{DBKey.FK_AUTHOR, Book.BKEY_AUTHOR_LIST});
            map.put(context.getString(R.string.lbl_identifiers),
                    new String[]{DBKey.FK_IDENTIFIER, Book.BKEY_IDENTIFIER_LIST});
            map.put(context.getString(R.string.lbl_publishers),
                    new String[]{DBKey.FK_PUBLISHER, Book.BKEY_PUBLISHER_LIST});
            map.put(context.getString(R.string.lbl_series_multiple),
                    new String[]{DBKey.FK_SERIES, Book.BKEY_SERIES_LIST});
            map.put(context.getString(R.string.lbl_tags),
                    new String[]{DBKey.FK_TAG, Book.BKEY_TAG_LIST});


            // The site specific fields
            map.put(context.getString(R.string.site_calibre),
                    new String[]{DBKey.CALIBRE.BOOK_ID});
            map.put(context.getString(R.string.lbl_ebook_file_type),
                    new String[]{DBKey.CALIBRE.BOOK_MAIN_FORMAT});

            // The site specific CustomFields
            ServiceLocator.getInstance()
                          .getCalibreCustomFieldDao()
                          .getCustomFields().stream()
                          .map(CalibreCustomField::getDbKey)
                          .forEach(dbKey -> {
                              try {
                                  map.put(MapDBKey.getLabel(context, dbKey), new String[]{dbKey});
                              } catch (@NonNull final IllegalArgumentException ignore) {
                                  // will currently never fail, as all custom fields are hardcoded.
                                  LoggerFactory.getLogger().w(
                                          TAG, "No MapDBKey for: " + dbKey);
                              }
                          });


            map.forEach(builder::add);

            builder.addRelatedField(DBKey.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
                   .addRelatedField(DBKey.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
                   .addRelatedField(DBKey.CALIBRE.BOOK_ID, DBKey.CALIBRE.BOOK_UUID);

            return builder;
        }
    },

    /** StripInfo web site. */
    StripInfo(R.string.site_stripinfo_be, false, false) {
        public boolean isEnabled(@NonNull final Context context) {
            return StripInfoHandler.isSyncEnabled(context);
        }

        @Override
        @WorkerThread
        @NonNull
        DataWriter<SyncWriterResults> createWriter(@NonNull final Context context,
                                                   @NonNull final Set<RecordType> recordTypes,
                                                   final boolean incremental,
                                                   final boolean deleteLocalBook) {
            return new StripInfoWriter(context, incremental, deleteLocalBook);
        }

        @Override
        @NonNull
        @WorkerThread
        DataReader<SyncReaderMetaData, ReaderResults> createReader(
                @NonNull final Context context,
                @NonNull final Set<RecordType> recordTypes,
                @Nullable final SyncReaderProcessor.Builder syncProcessorBuilder,
                @Nullable final LocalDateTime syncDate,
                @NonNull final DataReader.Updates updateOption,
                @NonNull final Bundle extraArgs)
                throws DataReaderException,
                       CredentialsException,
                       IOException {

            // Use either the custom passed-in, or the built-in default.
            final SyncReaderProcessor syncProcessor =
                    Objects.requireNonNullElseGet(
                                   syncProcessorBuilder,
                                   () -> createSyncProcessorBuilder(context))
                           .build(builder -> new StripInfoSyncReaderProcessor(context, builder));

            final DataReader<SyncReaderMetaData, ReaderResults> reader =
                    new StripInfoReader(context, recordTypes, syncProcessor, updateOption);
            reader.validate(context);
            return reader;
        }

        @Override
        public String getSyncPreferencePrefix() {
            return EngineId.StripInfoBe.getPreferenceKey() + FIELDS_UPDATE;
        }

        @Override
        @NonNull
        public SyncReaderProcessor.Builder createSyncProcessorBuilder(
                @NonNull final Context context) {
            final Locale siteLocale = EngineId.StripInfoBe.getDefaultLocale();
            final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
            final RealNumberParser realNumberParser = new RealNumberParser(locales);
            final SyncReaderProcessor.Builder builder =
                    new SyncReaderProcessor.Builder(context,
                                                    getSyncPreferencePrefix(),
                                                    realNumberParser);

            // Cover fields will be at the top of the list.
            builder.add(context.getString(R.string.lbl_cover_front),
                        new String[]{DBKey.COVER[0]});
            builder.add(context.getString(R.string.lbl_cover_back),
                        new String[]{DBKey.COVER[1]});

            // These fields will be locally sorted and come next on the list
            final SortedMap<String, String[]> map = new TreeMap<>();

            // the wishlist
            map.put(context.getString(R.string.lbl_bookshelves),
                    new String[]{DBKey.FK_BOOKSHELF, Book.BKEY_BOOKSHELF_LIST});
            map.put(context.getString(R.string.lbl_date_acquired),
                    new String[]{DBKey.DATE_ACQUIRED});
            map.put(context.getString(R.string.lbl_location),
                    new String[]{DBKey.LOCATION});
            map.put(context.getString(R.string.lbl_personal_notes),
                    new String[]{DBKey.PERSONAL_NOTES});
            map.put(context.getString(R.string.lbl_read),
                    new String[]{DBKey.READ__BOOL});
            map.put(context.getString(R.string.lbl_price_paid),
                    new String[]{DBKey.PRICE_PAID});

            // The collection-data: see StripInfoSyncReaderProcessor
            map.put(context.getString(R.string.site_stripinfo_be),
                    new String[]{StripInfoCollectionData.BKEY});

            // add the sorted fields
            map.forEach(builder::add);

            builder.addRelatedField(DBKey.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
                   .addRelatedField(DBKey.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
                   .addRelatedField(DBKey.PRICE_PAID, DBKey.PRICE_PAID_CURRENCY);

            // The single external-id field is added at the end of the list.
            map.put(context.getString(R.string.lbl_identifiers),
                    new String[]{Identifier.SID_STRIP_INFO});

            return builder;

        }
    };

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

    /* Log tag. */
    private static final String TAG = "SyncServer";
    /** The (optional) preset encoding to pass to export/import. */
    public static final String BKEY_SITE = TAG + ":encoding";

    /** See {@link #getSyncPreferencePrefix()}. */
    private static final String FIELDS_UPDATE = ".fields.update.";

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


    /**
     * A short label. Used in drop down menus and similar.
     *
     * @param context Current context
     *
     * @return label
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    /**
     * Check if this server is globally enabled.
     *
     * @param context Current context
     *
     * @return flag
     */
    public abstract boolean isEnabled(@NonNull Context context);

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
     * Create an {@link DataWriter}.
     *
     * @param context         Current context
     * @param recordTypes     the record types to write
     * @param incremental     flag: if the last-sync-date setting should
     *                        be used to do an incremental write
     * @param deleteLocalBook flag: delete the local book if it no longer exists on the remote
     *
     * @return a new writer
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    @WorkerThread
    @NonNull
    abstract DataWriter<SyncWriterResults> createWriter(@NonNull Context context,
                                                        @NonNull Set<RecordType> recordTypes,
                                                        boolean incremental,
                                                        boolean deleteLocalBook)
            throws CertificateException;

    /**
     * Create an {@link DataReader}.
     *
     * @param context              Current context
     * @param recordTypes          the record types to accept and read
     * @param syncProcessorBuilder synchronization configuration
     * @param syncDate             optional cut-off date
     * @param updateOption         options
     * @param extraArgs            Bundle with reader specific arguments
     *
     * @return a new reader
     *
     * @throws CertificateException on failures related to a user installed CA.
     * @throws CredentialsException on authentication/login failures
     * @throws DataReaderException  if the input is not recognized
     * @throws IOException          on generic/other IO failures
     * @see DataReader
     */
    @NonNull
    @WorkerThread
    abstract DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull Context context,
            @NonNull Set<RecordType> recordTypes,
            @Nullable SyncReaderProcessor.Builder syncProcessorBuilder,
            @Nullable LocalDateTime syncDate,
            @NonNull DataReader.Updates updateOption,
            @NonNull Bundle extraArgs)
            throws DataReaderException,
                   CertificateException,
                   CredentialsException,
                   IOException;

    /**
     * Get the preference key prefix for all sync keys for this SyncServer.
     *
     * @return pref prefix
     */
    public abstract String getSyncPreferencePrefix();

    /**
     * Create the default {@link SyncReaderProcessor.Builder}.
     * <p>
     * Simple fields are set to {@link SyncAction#CopyIfBlank}.
     * List fields are set to {@link SyncAction#Append}.
     *
     * @param context Current context
     *
     * @return a {@link SyncReaderProcessor.Builder}
     */
    @NonNull
    public abstract SyncReaderProcessor.Builder createSyncProcessorBuilder(
            @NonNull Context context);

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
               + "label=" + ServiceLocator.getInstance().getAppContext().getString(labelResId)
               + ", hasLastUpdateDateField=" + hasLastUpdateDateField
               + ", syncDateIsUserEditable=" + syncDateIsUserEditable
               + '}';
    }
}

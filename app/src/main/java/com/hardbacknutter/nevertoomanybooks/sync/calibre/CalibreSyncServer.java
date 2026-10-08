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

package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncFieldDef;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServerId;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterResults;
import com.hardbacknutter.util.logger.LoggerFactory;

public class CalibreSyncServer
        implements SyncServer {

    private static final String TAG = "CalibreSyncServer";

    @NonNull
    @Override
    public SyncServerId getId() {
        return SyncServerId.Calibre;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(R.string.lbl_calibre_content_server);
    }

    @Override
    public boolean hasLastUpdateDateField() {
        return true;
    }

    @Override
    public boolean isSyncDateUserEditable() {
        return true;
    }

    @NonNull
    @Override
    public DataWriter<SyncWriterResults> createWriter(@NonNull final Context context,
                                                      @NonNull final Set<RecordType> recordTypes,
                                                      final boolean incremental,
                                                      final boolean deleteLocalBook)
            throws CertificateException {

        return new CalibreContentServerWriter(context, recordTypes,
                                              incremental,
                                              deleteLocalBook);
    }

    @NonNull
    @Override
    public DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull final Context context,
            @NonNull final Set<RecordType> recordTypes,
            @Nullable final SyncReaderProcessor.Builder syncProcessorBuilder,
            @Nullable final LocalDateTime syncDate,
            @NonNull final DataReader.Updates updateOption,
            @NonNull final Bundle extraArgs)
            throws DataReaderException, CertificateException, CredentialsException, IOException {
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

    private String getSyncPreferencePrefix() {
        return CalibreContentServer.PREFERENCE_KEY + SyncServerId.FIELDS_UPDATE;
    }

    @NonNull
    @Override
    public SyncReaderProcessor.Builder createSyncProcessorBuilder(@NonNull final Context context) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        final SyncReaderProcessor.Builder builder =
                new SyncReaderProcessor.Builder(getSyncPreferencePrefix(), allLocales);

        // NEWTHINGS: Calibre adding a field, or a custom field

        // Cover fields will be at the top of the list.
        // There is only 1 image supported by Calibre
        builder.add(context.getString(R.string.lbl_cover_front),
                    SyncField.Type.OTHER, DBKey.COVER[0]);

        // These fields will be locally sorted and come next on the list
        final SortedMap<String, SyncFieldDef> map = new TreeMap<>();

        map.put(context.getString(R.string.lbl_description),
                new SyncFieldDef(SyncField.Type.APPENDABLE_STRING, DBKey.DESCRIPTION));
        map.put(context.getString(R.string.lbl_format),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.FORMAT));
        map.put(context.getString(R.string.lbl_language),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.LANGUAGE));
        map.put(context.getString(R.string.lbl_date_published),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.PUBLICATION_DATE));
        map.put(context.getString(R.string.lbl_title),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.TITLE));

        map.put(context.getString(R.string.lbl_authors),
                new SyncFieldDef(SyncField.Type.LIST, Book.BKEY_AUTHOR_LIST,
                                 DBKey.FK_AUTHOR));
        map.put(context.getString(R.string.lbl_identifiers),
                new SyncFieldDef(SyncField.Type.LIST, Identifier.Value.BKEY_LIST,
                                 DBKey.FK_IDENTIFIER));
        map.put(context.getString(R.string.lbl_publishers),
                new SyncFieldDef(SyncField.Type.LIST, Book.BKEY_PUBLISHER_LIST,
                                 DBKey.FK_PUBLISHER));
        map.put(context.getString(R.string.lbl_series_multiple),
                new SyncFieldDef(SyncField.Type.LIST, Book.BKEY_SERIES_LIST,
                                 DBKey.FK_SERIES));
        map.put(context.getString(R.string.lbl_tags),
                new SyncFieldDef(SyncField.Type.LIST, Book.BKEY_TAG_LIST,
                                 DBKey.FK_TAG));


        map.put(context.getString(R.string.lbl_pages),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.PAGES));
        // URGENT: verify how "rating" versus "#rating" works
        map.put(context.getString(R.string.lbl_rating),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.RATING));

        // The site specific fields
        map.put(context.getString(R.string.site_calibre),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.CALIBRE.BOOK_ID));
        map.put(context.getString(R.string.lbl_ebook_file_type),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.CALIBRE.BOOK_MAIN_FORMAT));

        // The site specific CustomFields
        ServiceLocator.getInstance()
                      .getCalibreCustomFieldDao()
                      .getCustomFields()
                      .stream()
                      .map(CalibreCustomField::getDbKey)
                      .forEach(dbKey -> {
                          try {
                              map.put(MapDBKey.getLabel(context, dbKey),
                                      new SyncFieldDef(SyncField.Type.OTHER, dbKey));
                          } catch (@NonNull final IllegalArgumentException ignore) {
                              // will currently never fail, as all custom fields are hardcoded.
                              LoggerFactory.getLogger().w(
                                      TAG, "No MapDBKey for: " + dbKey);
                          }
                      });


        map.forEach((label, def) -> builder.add(
                label, def.type, def.fieldKey, def.enabledKey));

        builder.addRelatedField(DBKey.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
               .addRelatedField(DBKey.CALIBRE.BOOK_ID, DBKey.CALIBRE.BOOK_UUID);

        return builder;
    }
}

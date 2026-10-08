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

package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

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
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncFieldDef;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServerId;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterResults;

public class StripInfoSyncServer
        implements SyncServer {

    @NonNull
    @Override
    public SyncServerId getId() {
        return SyncServerId.StripInfo;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(R.string.site_stripinfo_be);
    }

    @Override
    public boolean hasLastUpdateDateField() {
        return false;
    }

    @Override
    public boolean isSyncDateUserEditable() {
        return false;
    }

    @NonNull
    @Override
    public DataWriter<SyncWriterResults> createWriter(@NonNull final Context context,
                                                      @NonNull final Set<RecordType> recordTypes,
                                                      final boolean incremental,
                                                      final boolean deleteLocalBook) {
        return new StripInfoWriter(context, incremental, deleteLocalBook);
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
                       .build(builder -> new StripInfoSyncReaderProcessor(context, builder));

        final DataReader<SyncReaderMetaData, ReaderResults> reader =
                new StripInfoReader(context, recordTypes, syncProcessor, updateOption);
        reader.validate(context);
        return reader;
    }

    private String getSyncPreferencePrefix() {
        return EngineId.StripInfoBe.getPreferenceKey() + SyncServerId.FIELDS_UPDATE;
    }

    @NonNull
    @Override
    public SyncReaderProcessor.Builder createSyncProcessorBuilder(@NonNull final Context context) {
        final Locale siteLocale = EngineId.StripInfoBe.getDefaultLocale();
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(siteLocale, userLocales);
        final SyncReaderProcessor.Builder builder =
                new SyncReaderProcessor.Builder(getSyncPreferencePrefix(), allLocales);

        // Cover fields will be at the top of the list.
        // There are only 2 images supported by this site.
        builder.add(context.getString(R.string.lbl_cover_front),
                    SyncField.Type.OTHER, DBKey.COVER[0]);
        builder.add(context.getString(R.string.lbl_cover_back),
                    SyncField.Type.OTHER, DBKey.COVER[1]);

        // These fields will be locally sorted and come next on the list
        final SortedMap<String, SyncFieldDef> map = new TreeMap<>();

        // the wishlist
        map.put(context.getString(R.string.lbl_bookshelves),
                new SyncFieldDef(SyncField.Type.LIST, Book.BKEY_BOOKSHELF_LIST,
                                 DBKey.FK_BOOKSHELF));
        map.put(context.getString(R.string.lbl_date_acquired),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.DATE_ACQUIRED));
        map.put(context.getString(R.string.lbl_location),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.LOCATION));
        map.put(context.getString(R.string.lbl_personal_notes),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.PERSONAL_NOTES));
        map.put(context.getString(R.string.lbl_read),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.READ__BOOL));
        map.put(context.getString(R.string.lbl_price_paid),
                new SyncFieldDef(SyncField.Type.OTHER, DBKey.PRICE_PAID));

        // The collection-data: see StripInfoSyncReaderProcessor
        map.put(context.getString(R.string.site_stripinfo_be),
                new SyncFieldDef(SyncField.Type.OTHER, StripInfoCollectionData.BKEY));

        // add the sorted fields
        map.forEach((label, def) -> builder.add(
                label, def.type, def.fieldKey, def.enabledKey));

        builder.addRelatedField(DBKey.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
               .addRelatedField(DBKey.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
               .addRelatedField(DBKey.PRICE_PAID, DBKey.PRICE_PAID_CURRENCY);

        // The single external-id field is added at the end of the list.
        map.put(context.getString(R.string.lbl_identifiers),
                new SyncFieldDef(SyncField.Type.OTHER, Identifier.SID_STRIP_INFO));

        return builder;
    }
}

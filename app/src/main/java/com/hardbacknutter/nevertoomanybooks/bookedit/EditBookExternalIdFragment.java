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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LongNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public class EditBookExternalIdFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookExternalIdFrag";

    public static boolean isShowTab(@NonNull final SharedPreferences global) {
        return global.getBoolean(Prefs.pk_edit_book_tabs_external_id, false);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return TAG;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_external_id, container, false);
    }

    @Override
    protected void onInitFields(@NonNull final Fields fields) {

        // These FieldFormatters can be shared between multiple fields.
        final FieldFormatter<Number> longNumberFormatter = new LongNumberFormatter();

        fields.add(R.id.site_goodreads, new EditTextAccessor<>(longNumberFormatter, true),
                   DBKey.SID_GOODREADS_BOOK)
              .setRelatedFields(R.id.lbl_site_goodreads);

        fields.add(R.id.site_isfdb, new EditTextAccessor<>(longNumberFormatter, true),
                   DBKey.SID_ISFDB)
              .setRelatedFields(R.id.lbl_site_isfdb);

        fields.add(R.id.site_library_thing, new EditTextAccessor<>(longNumberFormatter, true),
                   DBKey.SID_LIBRARY_THING)
              .setRelatedFields(R.id.lbl_site_library_thing);

        fields.add(R.id.site_strip_info_be, new EditTextAccessor<>(longNumberFormatter, true),
                   DBKey.SID_STRIP_INFO)
              .setRelatedFields(R.id.lbl_site_strip_info_be);

        fields.add(R.id.site_open_library, new EditTextAccessor<>(),
                   DBKey.SID_OPEN_LIBRARY)
              .setRelatedFields(R.id.lbl_site_open_library);
    }

    @Override
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        // hide unwanted fields
        // Force hidden fields to stay hidden; this will allow us to temporarily remove
        // some sites without removing the data.
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, true);
    }
}

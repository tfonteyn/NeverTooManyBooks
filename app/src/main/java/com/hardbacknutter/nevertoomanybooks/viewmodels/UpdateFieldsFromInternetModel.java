/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

public class UpdateFieldsFromInternetModel
        extends ViewModel {

    /** which fields to update and how. */
    @NonNull
    private final Map<String, FieldUsage> mFieldUsages = new LinkedHashMap<>();
    /** Sites to search on. */
    private ArrayList<Site> mSearchSites;
    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private ArrayList<Long> mBookIds;

    /** display reminder only. */
    @Nullable
    private String mTitle;

    /** senderId of the update task. */
    private long mUpdateSenderId;

    /** Allows restarting an update task from the given book id onwards. */
    @SuppressWarnings("FieldCanBeLocal")
    private long mFromBookIdOnwards = 0;


    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (args != null) {
            //noinspection unchecked
            mBookIds = (ArrayList<Long>) args.getSerializable(UniqueId.BKEY_ID_LIST);
            // optional activity title
            mTitle = args.getString(UniqueId.BKEY_DIALOG_TITLE);

            // use global preference.
            mSearchSites = SearchSites.getSites(context, SearchSites.ListType.Data);
        }
    }

    @Nullable
    public String getTitle() {
        return mTitle;
    }

    public long getUpdateSenderId() {
        return mUpdateSenderId;
    }

    public void setUpdateSenderId(final long updateSenderId) {
        mUpdateSenderId = updateSenderId;
    }

    @Nullable
    public FieldUsage getFieldUsage(@NonNull final String key) {
        return mFieldUsages.get(key);
    }

    @NonNull
    public Map<String, FieldUsage> getFieldUsages() {
        return mFieldUsages;
    }

    public void putFieldUsage(@NonNull final String key,
                              @NonNull final FieldUsage fieldUsage) {
        mFieldUsages.put(key, fieldUsage);
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list
     */
    @NonNull
    public ArrayList<Site> getSearchSites() {
        return mSearchSites;
    }

    /**
     * Override the initial list.
     *
     * @param searchSites to use temporarily
     */
    public void setSearchSites(@NonNull final ArrayList<Site> searchSites) {
        mSearchSites = searchSites;
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return bitmask
     */
    public int getEnabledSearchSites() {
        return SearchSites.getEnabledSites(mSearchSites);
    }

    /**
     * Allows restarting an update task from the given book id onwards.
     *
     * @return book id to start updating from, {@code 0} for all.
     */
    public long getFromBookIdOnwards() {
        return mFromBookIdOnwards;
    }

    /**
     * The books (id) to update.
     *
     * @return list of ids, or {@code null} for all books.
     */
    @Nullable
    public ArrayList<Long> getBookIds() {
        return mBookIds;
    }

    /** syntax sugar. */
    public boolean isSingleBook() {
        return mBookIds != null && mBookIds.size() == 1;
    }

    /**
     * Add any related fields with the same setting.
     *
     * @param fieldId        to check presence of
     * @param relatedFieldId to add if fieldId was present
     */
    public void addRelatedField(@SuppressWarnings("SameParameterValue")
                                @NonNull final String fieldId,
                                @SuppressWarnings("SameParameterValue")
                                @NonNull final String relatedFieldId) {
        FieldUsage field = mFieldUsages.get(fieldId);
        if (field != null && field.isWanted()) {
            FieldUsage fu = new FieldUsage(0, field.getUsage(), field.canAppend(), relatedFieldId);
            mFieldUsages.put(relatedFieldId, fu);
        }
    }

    /**
     * Add a FieldUsage for a <strong>list</strong> field if it has not been hidden by the user.
     *
     * @param nameStringId Field label string resource ID
     * @param visField     Field name to check for visibility.
     * @param fieldId      List-field name to use in FieldUsages
     */
    private void addListField(@StringRes final int nameStringId,
                              @NonNull final String visField,
                              @NonNull final String fieldId) {

        if (App.isUsed(visField)) {
            FieldUsage fu = new FieldUsage(nameStringId, FieldUsage.Usage.Append, true, fieldId);
            mFieldUsages.put(fieldId, fu);
        }
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param nameStringId Field label string resource ID
     * @param defaultUsage default Usage for this field
     * @param fieldId      Field name to use in FieldUsages + check for visibility
     */
    private void addField(@StringRes final int nameStringId,
                          @NonNull final FieldUsage.Usage defaultUsage,
                          @NonNull final String fieldId) {

        if (App.isUsed(fieldId)) {
            putFieldUsage(fieldId, new FieldUsage(nameStringId, defaultUsage, false, fieldId));
        }
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    public void initFields() {

        addListField(R.string.lbl_author, DBDefinitions.KEY_FK_AUTHOR,
                     UniqueId.BKEY_AUTHOR_ARRAY);

        addField(R.string.lbl_title, CopyIfBlank, DBDefinitions.KEY_TITLE);
        addField(R.string.lbl_isbn, CopyIfBlank, DBDefinitions.KEY_ISBN);
        addField(R.string.lbl_cover, CopyIfBlank, UniqueId.BKEY_IMAGE);

        addListField(R.string.lbl_series, DBDefinitions.KEY_SERIES_TITLE,
                     UniqueId.BKEY_SERIES_ARRAY);

        addListField(R.string.lbl_table_of_content, DBDefinitions.KEY_TOC_BITMASK,
                     UniqueId.BKEY_TOC_ENTRY_ARRAY);

        addField(R.string.lbl_publisher, CopyIfBlank,
                 DBDefinitions.KEY_PUBLISHER);
        addField(R.string.lbl_print_run, CopyIfBlank,
                 DBDefinitions.KEY_PRINT_RUN);
        addField(R.string.lbl_date_published, CopyIfBlank,
                 DBDefinitions.KEY_DATE_PUBLISHED);
        addField(R.string.lbl_first_publication, CopyIfBlank,
                 DBDefinitions.KEY_DATE_FIRST_PUBLICATION);

        addField(R.string.lbl_description, CopyIfBlank, DBDefinitions.KEY_DESCRIPTION);

        addField(R.string.lbl_pages, CopyIfBlank, DBDefinitions.KEY_PAGES);
        addField(R.string.lbl_format, CopyIfBlank, DBDefinitions.KEY_FORMAT);
        addField(R.string.lbl_color, CopyIfBlank, DBDefinitions.KEY_COLOR);
        addField(R.string.lbl_language, CopyIfBlank, DBDefinitions.KEY_LANGUAGE);

        // list price has related DBDefinitions.KEY_PRICE_LISTED
        addField(R.string.lbl_price_listed, CopyIfBlank, DBDefinitions.KEY_PRICE_LISTED
                );

        addField(R.string.lbl_genre, CopyIfBlank, DBDefinitions.KEY_GENRE);

        //NEWTHINGS: add new site specific ID: add a field
        addField(R.string.isfdb, Overwrite, DBDefinitions.KEY_EID_ISFDB);
        addField(R.string.goodreads, Overwrite, DBDefinitions.KEY_EID_GOODREADS_BOOK);
        addField(R.string.library_thing, Overwrite, DBDefinitions.KEY_EID_LIBRARY_THING);
        addField(R.string.open_library, Overwrite, DBDefinitions.KEY_EID_OPEN_LIBRARY);
        addField(R.string.stripinfo, Overwrite, DBDefinitions.KEY_EID_STRIP_INFO_BE);
    }
}

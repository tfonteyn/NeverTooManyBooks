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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.utils.FocusFixer;

//URGENT: add a switch?? to the layout to show ALL fields.
public class EditBookNativeIdFragment
        extends EditBookBaseFragment {

    private EditText mEidGoodreadsView;
    private EditText mEidIsfdbView;
    private EditText mEidLibraryThingView;
    private EditText mEidOpenLibraryView;
    private EditText mEidStripInfoView;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_book_native_id, container, false);
        mEidGoodreadsView = view.findViewById(R.id.site_goodreads);
        mEidIsfdbView = view.findViewById(R.id.site_isfdb);
        mEidLibraryThingView = view.findViewById(R.id.site_library_thing);
        mEidOpenLibraryView = view.findViewById(R.id.site_open_library);
        mEidStripInfoView = view.findViewById(R.id.site_strip_info_be);
        return view;
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookDetailsFragment}.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        fields.addString(R.id.site_goodreads, mEidGoodreadsView,
                         DBDefinitions.KEY_EID_GOODREADS_BOOK)
              .setRelatedFields(R.id.lbl_site_goodreads);
        fields.addString(R.id.site_isfdb, mEidIsfdbView,
                         DBDefinitions.KEY_EID_ISFDB)
              .setRelatedFields(R.id.lbl_site_isfdb);
        fields.addString(R.id.site_library_thing, mEidLibraryThingView,
                         DBDefinitions.KEY_EID_LIBRARY_THING)
              .setRelatedFields(R.id.lbl_site_library_thing);
        fields.addString(R.id.site_open_library, mEidOpenLibraryView,
                         DBDefinitions.KEY_EID_OPEN_LIBRARY)
              .setRelatedFields(R.id.lbl_site_open_library);
        fields.addString(R.id.site_strip_info_be, mEidStripInfoView,
                         DBDefinitions.KEY_EID_STRIP_INFO_BE)
              .setRelatedFields(R.id.lbl_site_strip_info_be);

        //noinspection ConstantConditions
        int sites = SiteList.getList(getContext(), SiteList.Type.Data).getEnabledSites();
        setSiteVisibility(sites);
    }

    private void setSiteVisibility(final int sites) {
        // related labels are dealt with automatically in #showOrHideFields
        mEidGoodreadsView.setVisibility((sites & SearchSites.GOODREADS) != 0
                                        ? View.VISIBLE : View.GONE);
        mEidIsfdbView.setVisibility((sites & SearchSites.ISFDB) != 0
                                    ? View.VISIBLE : View.GONE);
        mEidLibraryThingView.setVisibility((sites & SearchSites.LIBRARY_THING) != 0
                                           ? View.VISIBLE : View.GONE);
        mEidOpenLibraryView.setVisibility((sites & SearchSites.OPEN_LIBRARY) != 0
                                          ? View.VISIBLE : View.GONE);
        mEidStripInfoView.setVisibility((sites & SearchSites.STRIP_INFO_BE) != 0
                                        ? View.VISIBLE : View.GONE);
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do other stuff here that might affect the view.

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusFixer.fix(getView());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // hide unwanted fields
        showOrHideFields(false, true);
    }
}

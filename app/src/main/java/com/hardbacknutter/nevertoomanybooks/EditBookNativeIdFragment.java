/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.LongNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

public class EditBookNativeIdFragment
        extends EditBookBaseFragment {

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_native_id, container, false);
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    @Override
    protected void onInitFields() {
        super.onInitFields();
        final Fields fields = mFragmentVM.getFields();

        // These FieldFormatter's can be shared between multiple fields.
        final FieldFormatter<Number> longNumberFormatter = new LongNumberFormatter();

        fields.add(R.id.site_goodreads, DBDefinitions.KEY_EID_GOODREADS_BOOK,
                   new EditTextAccessor<>(longNumberFormatter, true))
              .setRelatedFields(R.id.lbl_site_goodreads);

        fields.add(R.id.site_isfdb, DBDefinitions.KEY_EID_ISFDB,
                   new EditTextAccessor<>(longNumberFormatter, true))
              .setRelatedFields(R.id.lbl_site_isfdb);

        fields.add(R.id.site_library_thing, DBDefinitions.KEY_EID_LIBRARY_THING,
                   new EditTextAccessor<>(longNumberFormatter, true))
              .setRelatedFields(R.id.lbl_site_library_thing);

        fields.add(R.id.site_strip_info_be, DBDefinitions.KEY_EID_STRIP_INFO_BE,
                   new EditTextAccessor<>(longNumberFormatter, true))
              .setRelatedFields(R.id.lbl_site_strip_info_be);

        fields.add(R.id.site_open_library, DBDefinitions.KEY_EID_OPEN_LIBRARY,
                   new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_site_open_library);
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        mFragmentVM.getFields().resetVisibility(getView(), false, true);
    }


//    /**
//     * Show all sites, or only the enabled sites.
//     *
//     * @param showAllSites flag
//     */
//    private void setSiteVisibility(final boolean showAllSites) {
//
//        @SearchSites.Id
//        final int sites;
//
//        if (showAllSites) {
//            sites = SearchSites.SEARCH_FLAG_MASK;
//        } else {
//            //noinspection ConstantConditions
//            Locale locale = LocaleUtils.getUserLocale(getContext());
//            sites = SiteList.getList(getContext(), locale, SiteList.Type.Data).getEnabledSites();
//        }
//
//        final Fields fields = mFragmentVM.getFields();
//        final View parent = getView();
//
//        //noinspection ConstantConditions
//        fields.getField(R.id.site_goodreads).setVisibility(
//                parent, (sites & SearchSites.GOODREADS) != 0 ? View.VISIBLE : View.GONE);
//
//        fields.getField(R.id.site_isfdb).setVisibility(
//                parent, (sites & SearchSites.ISFDB) != 0 ? View.VISIBLE : View.GONE);
//
//        fields.getField(R.id.site_library_thing).setVisibility(
//                parent, (sites & SearchSites.LIBRARY_THING) != 0 ? View.VISIBLE : View.GONE);
//
//        fields.getField(R.id.site_open_library).setVisibility(
//                parent, (sites & SearchSites.OPEN_LIBRARY) != 0 ? View.VISIBLE : View.GONE);
//
//        fields.getField(R.id.site_strip_info_be).setVisibility(
//                parent, (sites & SearchSites.STRIP_INFO_BE) != 0 ? View.VISIBLE : View.GONE);
//    }
}

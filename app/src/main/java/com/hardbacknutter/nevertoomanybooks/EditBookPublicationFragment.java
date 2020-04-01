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
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.DecimalEditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This class is called by {@link EditBookFragment} and displays the publication fields Tab.
 */
public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_publication, container, false);
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

        fields.add(R.id.pages, new EditTextAccessor<String>(), DBDefinitions.KEY_PAGES)
              .setRelatedFields(R.id.lbl_pages);

        fields.add(R.id.format, new EditTextAccessor<String>(), DBDefinitions.KEY_FORMAT)
              .setRelatedFields(R.id.lbl_format);

        fields.add(R.id.color, new EditTextAccessor<String>(), DBDefinitions.KEY_COLOR)
              .setRelatedFields(R.id.lbl_color);

        fields.add(R.id.language, new EditTextAccessor<>(new LanguageFormatter(), true),
                   DBDefinitions.KEY_LANGUAGE
                  )
              .setRelatedFields(R.id.lbl_language);

        fields.add(R.id.publisher, new EditTextAccessor<String>(), DBDefinitions.KEY_PUBLISHER)
              .setRelatedFields(R.id.lbl_publisher);

        fields.add(R.id.print_run, new EditTextAccessor<String>(), DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.date_published, new EditTextAccessor<>(new DateFieldFormatter(), false),
                   DBDefinitions.KEY_DATE_PUBLISHED
                  )
              .setRelatedFields(R.id.lbl_date_published);

        fields.add(R.id.first_publication, new EditTextAccessor<>(new DateFieldFormatter(), false),
                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION
                  )
              .setRelatedFields(R.id.lbl_first_publication);

        // MUST be defined before the currency.
        fields.add(R.id.price_listed,
                   new DecimalEditTextAccessor(new DoubleNumberFormatter(), false),
                   DBDefinitions.KEY_PRICE_LISTED
                  );
        fields.add(R.id.price_listed_currency, new EditTextAccessor<String>(),
                   DBDefinitions.KEY_PRICE_LISTED_CURRENCY
                  )
              .setRelatedFields(R.id.lbl_price_listed,
                                R.id.lbl_price_listed_currency, R.id.price_listed_currency);
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        mFragmentVM.getFields().resetVisibility(getView(), false, false);
    }

    @Override
    public void onResume() {
        // the super will trigger the population of all defined Fields and their Views.
        super.onResume();

        // With all Views populated, (re-)add the helpers
        addAutocomplete(R.id.format, mFragmentVM.getFormats());
        addAutocomplete(R.id.color, mFragmentVM.getColors());
        addAutocomplete(R.id.language, mFragmentVM.getLanguagesCodes());
        addAutocomplete(R.id.publisher, mFragmentVM.getPublishers());
        addAutocomplete(R.id.price_listed_currency, mFragmentVM.getListPriceCurrencyCodes());

        addDatePicker(R.id.date_published, R.string.lbl_date_published, false, true);
        addDatePicker(R.id.first_publication, R.string.lbl_first_publication, false, true);
    }
}

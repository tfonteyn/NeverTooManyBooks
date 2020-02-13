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
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.DecimalEditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
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
    protected void initFields() {
        super.initFields();
        final Fields fields = getFields();

        // These FieldFormatter's can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();
        final FieldFormatter<String> languageFormatter = new LanguageFormatter();
        final FieldFormatter<Number> doubleNumberFormatter = new DoubleNumberFormatter();

        fields.<String>add(R.id.pages, new EditTextAccessor<>(),
                           DBDefinitions.KEY_PAGES)
                .setRelatedFields(R.id.lbl_pages);

        fields.<String>add(R.id.format, new EditTextAccessor<>(),
                           DBDefinitions.KEY_FORMAT)
                .setRelatedFields(R.id.lbl_format);

        fields.<String>add(R.id.color, new EditTextAccessor<>(),
                           DBDefinitions.KEY_COLOR)
                .setRelatedFields(R.id.lbl_color);

        fields.<String>add(R.id.language, new EditTextAccessor<>(),
                           DBDefinitions.KEY_LANGUAGE)
                .setRelatedFields(R.id.lbl_language)
                .setFormatter(languageFormatter);

        fields.<String>add(R.id.publisher, new EditTextAccessor<>(),
                           DBDefinitions.KEY_PUBLISHER)
                .setRelatedFields(R.id.lbl_publisher);

        fields.<String>add(R.id.date_published, new EditTextAccessor<>(),
                           DBDefinitions.KEY_DATE_PUBLISHED)
                .setRelatedFields(R.id.lbl_date_published)
                .setFormatter(dateFormatter);

        fields.<String>add(R.id.print_run, new EditTextAccessor<>(),
                           DBDefinitions.KEY_PRINT_RUN)
                .setRelatedFields(R.id.lbl_print_run);

        fields.<String>add(R.id.first_publication, new EditTextAccessor<>(),
                           DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
                .setRelatedFields(R.id.lbl_first_publication)
                .setFormatter(dateFormatter);

        // MUST be defined before the currency.
        fields.<Number>add(R.id.price_listed, new DecimalEditTextAccessor<>(),
                           DBDefinitions.KEY_PRICE_LISTED)
                .setFormatter(doubleNumberFormatter);
        fields.<String>add(R.id.price_listed_currency, new EditTextAccessor<>(),
                           DBDefinitions.KEY_PRICE_LISTED_CURRENCY)
                .setRelatedFields(R.id.lbl_price_listed,
                                  R.id.lbl_price_listed_currency, R.id.price_listed_currency);
    }

    @Override
    void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        getFields().resetVisibility(getView(), false, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // The views will now have been restored to the fields. (re-)add the helpers

        addAutocomplete(R.id.format, mBookModel.getFormats());
        addAutocomplete(R.id.color, mBookModel.getColors());
        addAutocomplete(R.id.language, mBookModel.getLanguagesCodes());
        addAutocomplete(R.id.publisher, mBookModel.getPublishers());
        addAutocomplete(R.id.price_listed_currency, mBookModel.getListPriceCurrencyCodes());

        addDatePicker(R.id.date_published, R.string.lbl_date_published, false);
        addDatePicker(R.id.first_publication, R.string.lbl_first_publication, false);
    }
}

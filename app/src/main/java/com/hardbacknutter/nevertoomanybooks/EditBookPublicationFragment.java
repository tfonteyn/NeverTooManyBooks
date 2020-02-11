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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This class is called by {@link EditBookFragment} and displays the publication fields Tab.
 */
public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    private View mPagesView;
    private AutoCompleteTextView mFormatView;
    private AutoCompleteTextView mColorView;
    private AutoCompleteTextView mLanguageView;
    private AutoCompleteTextView mPublisherView;
    private View mDatePublishedView;
    private View mPrintRunView;
    private View mFirstPubView;
    private EditText mPriceListedView;
    private AutoCompleteTextView mPriceListedCurrencyView;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater
                .inflate(R.layout.fragment_edit_book_publication, container, false);
        mPagesView = view.findViewById(R.id.pages);
        mFormatView = view.findViewById(R.id.format);
        mColorView = view.findViewById(R.id.color);
        mLanguageView = view.findViewById(R.id.language);
        mPublisherView = view.findViewById(R.id.publisher);
        mDatePublishedView = view.findViewById(R.id.date_published);
        mPrintRunView = view.findViewById(R.id.print_run);
        mFirstPubView = view.findViewById(R.id.first_publication);
        mPriceListedView = view.findViewById(R.id.price_listed);
        mPriceListedCurrencyView = view.findViewById(R.id.price_listed_currency);
        return view;
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookDetailsFragment}.
     * <p>
     * <br>{@inheritDoc}
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initFields() {
        super.initFields();
        final Fields fields = getFields();

        // A DateFieldFormatter can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();

        fields.addString(mPagesView, DBDefinitions.KEY_PAGES)
              .setRelatedFields(R.id.lbl_pages);

        fields.addString(mFormatView, DBDefinitions.KEY_FORMAT)
              .setRelatedFields(R.id.lbl_format)
              .setAutocomplete(mFormatView, mBookModel.getFormats());

        fields.addString(mColorView, DBDefinitions.KEY_COLOR)
              .setRelatedFields(R.id.lbl_color)
              .setAutocomplete(mColorView, mBookModel.getColors());

        fields.addString(mLanguageView, DBDefinitions.KEY_LANGUAGE)
              .setRelatedFields(R.id.lbl_language)
              .setFormatter(new LanguageFormatter())
              .setAutocomplete(mLanguageView, mBookModel.getLanguagesCodes());

        fields.addString(mPublisherView, DBDefinitions.KEY_PUBLISHER)
              .setRelatedFields(R.id.lbl_publisher)
              .setAutocomplete(mPublisherView, mBookModel.getPublishers());

        fields.addString(mDatePublishedView, DBDefinitions.KEY_DATE_PUBLISHED)
              .setRelatedFields(R.id.lbl_date_published)
              .setFormatter(dateFormatter)
              .addDatePicker(getChildFragmentManager(), mDatePublishedView,
                             R.string.lbl_date_published, false);

        fields.addString(mPrintRunView, DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.addString(mFirstPubView, DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
              .setRelatedFields(R.id.lbl_first_publication)
              .setFormatter(dateFormatter)
              .addDatePicker(getChildFragmentManager(), mFirstPubView,
                             R.string.lbl_first_publication, false);

        // MUST be defined before the currency.
        fields.addMoneyValue(mPriceListedView, DBDefinitions.KEY_PRICE_LISTED);
        fields.addString(mPriceListedCurrencyView, DBDefinitions.KEY_PRICE_LISTED_CURRENCY)
              .setRelatedFields(R.id.lbl_price_listed,
                                R.id.lbl_price_listed_currency, R.id.price_listed_currency)
              .setAutocomplete(mPriceListedCurrencyView, mBookModel.getListPriceCurrencyCodes());
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    @Override
    protected void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        getFields().resetVisibility(getView(), false, false);
    }
}

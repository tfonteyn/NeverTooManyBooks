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
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;

/**
 * This class is called by {@link EditBookFragment} and displays the publication fields Tab.
 */
public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    public static final String TAG = "EditBookPublicationFragment";

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
        View view = inflater.inflate(R.layout.fragment_edit_book_publication, container, false);
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
    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        // A DateFieldFormatter can be shared between multiple fields.
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        Field<String> field;

        // book fields

        fields.addString(R.id.pages, mPagesView, DBDefinitions.KEY_PAGES)
              .setRelatedFields(R.id.lbl_pages);

        field = fields.addString(R.id.format, mFormatView, DBDefinitions.KEY_FORMAT)
                      .setRelatedFields(R.id.lbl_format);
        initValuePicker(field, mFormatView, R.string.lbl_format, R.id.btn_format,
                        mBookModel.getFormats());

        field = fields.addString(R.id.color, mColorView, DBDefinitions.KEY_COLOR)
                      .setRelatedFields(R.id.lbl_color);
        initValuePicker(field, mColorView, R.string.lbl_color, R.id.btn_color,
                        mBookModel.getColors());

        field = fields.addString(R.id.language, mLanguageView, DBDefinitions.KEY_LANGUAGE)
                      .setFormatter(new Fields.LanguageFormatter())
                      .setRelatedFields(R.id.lbl_language);
        initValuePicker(field, mLanguageView, R.string.lbl_language, R.id.btn_language,
                        mBookModel.getLanguagesCodes());

        field = fields.addString(R.id.publisher, mPublisherView, DBDefinitions.KEY_PUBLISHER)
                      .setRelatedFields(R.id.lbl_publisher);
        initValuePicker(field, mPublisherView, R.string.lbl_publisher, R.id.btn_publisher,
                        mBookModel.getPublishers());

        field = fields.addString(R.id.date_published, mDatePublishedView,
                                 DBDefinitions.KEY_DATE_PUBLISHED)
                      .setFormatter(dateFormatter)
                      .setRelatedFields(R.id.lbl_date_published);
        initPartialDatePicker(field, mDatePublishedView, R.string.lbl_date_published, false);

        fields.addString(R.id.print_run, mPrintRunView, DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        field = fields.addString(R.id.first_publication, mFirstPubView,
                                 DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
                      .setFormatter(dateFormatter)
                      .setRelatedFields(R.id.lbl_first_publication);
        initPartialDatePicker(field, mFirstPubView, R.string.lbl_first_publication, false);

        fields.addMonetary(R.id.price_listed, mPriceListedView, DBDefinitions.KEY_PRICE_LISTED)
              .setInputIsDecimal();

        field = fields
                .addString(R.id.price_listed_currency, mPriceListedCurrencyView,
                           DBDefinitions.KEY_PRICE_LISTED_CURRENCY)
                .setRelatedFields(R.id.lbl_price_listed, R.id.price_listed_currency);
        initValuePicker(field, mPriceListedCurrencyView, R.string.lbl_currency,
                        R.id.btn_price_listed_currency,
                        mBookModel.getListPriceCurrencyCodes());
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do other stuff here that might affect the view.

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusSettings.fix(getView());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // hide unwanted fields
        showOrHideFields(false);
    }
}

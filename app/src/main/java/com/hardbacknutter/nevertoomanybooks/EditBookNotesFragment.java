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
import android.widget.Checkable;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FocusFixer;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment<Integer> {

    public static final String TAG = "EditBookNotesFragment";

    private View mReadCbx;
    private View mSignedCbx;
    private View mRatingView;
    private View mNotesView;
    private EditText mPricePaidView;
    private AutoCompleteTextView mPricePaidCurrencyView;
    private AutoCompleteTextView mLocationView;
    private View mEditionView;
    private View mDateAcquiredView;
    private View mDateReadStartView;
    private View mDateReadEndView;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
        mReadCbx = view.findViewById(R.id.cbx_read);
        mSignedCbx = view.findViewById(R.id.cbx_signed);
        mRatingView = view.findViewById(R.id.rating);
        mNotesView = view.findViewById(R.id.notes);
        mPricePaidView = view.findViewById(R.id.price_paid);
        mPricePaidCurrencyView = view.findViewById(R.id.price_paid_currency);
        mLocationView = view.findViewById(R.id.location);
        mEditionView = view.findViewById(R.id.edition);
        mDateAcquiredView = view.findViewById(R.id.date_acquired);
        mDateReadStartView = view.findViewById(R.id.read_start);
        mDateReadEndView = view.findViewById(R.id.read_end);
        return view;
    }

    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        // A DateFieldFormatter can be shared between multiple fields.
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        // ENHANCE: Add a partial date validator. Or not.
        //FieldValidator blankOrDateValidator = new Fields.OrValidator(
        //     new Fields.BlankValidator(), new Fields.DateValidator());

        Field<String> field;

        fields.addBoolean(R.id.cbx_read, mReadCbx, DBDefinitions.KEY_READ);
        mReadCbx.setOnClickListener(v -> {
            // when user sets 'read', also set the read-end date to today (unless set before)
            Checkable cb = (Checkable) v;
            if (cb.isChecked()) {
                Field<String> readEnd = fields.getField(R.id.read_end);
                if (readEnd.isEmpty()) {
                    readEnd.setValue(DateUtils.localSqlDateForToday());
                }
            }
        });

        fields.addBoolean(R.id.cbx_signed, mSignedCbx, DBDefinitions.KEY_SIGNED);

        fields.addFloat(R.id.rating, mRatingView, DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.addString(R.id.notes, mNotesView, DBDefinitions.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        fields.addMonetary(R.id.price_paid, mPricePaidView, DBDefinitions.KEY_PRICE_PAID)
              .setInputIsDecimal();

        field = fields.addString(R.id.price_paid_currency, mPricePaidCurrencyView,
                                 DBDefinitions.KEY_PRICE_PAID_CURRENCY)
                      .setRelatedFields(R.id.lbl_price_paid, R.id.price_paid_currency);

        initValuePicker(field, mPricePaidCurrencyView, R.string.lbl_currency,
                        R.id.btn_price_paid_currency,
                        mBookModel.getPricePaidCurrencyCodes());

        field = fields.addString(R.id.location, mLocationView, DBDefinitions.KEY_LOCATION)
                      .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);
        initValuePicker(field, mLocationView, R.string.lbl_location, R.id.btn_location,
                        mBookModel.getLocations());

        //noinspection ConstantConditions
        Field<Long> editionsField = fields
                .addLong(R.id.edition, mEditionView, DBDefinitions.KEY_EDITION_BITMASK)
                .setFormatter(new Fields.BitMaskFormatter(Book.getEditions(getContext())))
                .setRelatedFields(R.id.lbl_edition);
        initCheckListEditor(editionsField, mEditionView, R.string.lbl_edition, () ->
                mBookModel.getBook().getEditableEditionList());

        field = fields
                .addString(R.id.date_acquired, mDateAcquiredView, DBDefinitions.KEY_DATE_ACQUIRED)
                .setFormatter(dateFormatter)
                .setRelatedFields(R.id.lbl_date_acquired);
        initPartialDatePicker(field, mDateAcquiredView, R.string.lbl_date_acquired, true);

        field = fields.addString(R.id.read_start, mDateReadStartView, DBDefinitions.KEY_READ_START)
                      .setFormatter(dateFormatter)
                      .setRelatedFields(R.id.lbl_read_start);
        initPartialDatePicker(field, mDateReadStartView, R.string.lbl_read_start, true);

        field = fields.addString(R.id.read_end, mDateReadEndView, DBDefinitions.KEY_READ_END)
                      .setFormatter(dateFormatter)
                      .setRelatedFields(R.id.lbl_read_end);
        initPartialDatePicker(field, mDateReadEndView, R.string.lbl_read_end, true);
    }

    @Override
    @CallSuper
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
        showOrHideFields(false);
    }
}

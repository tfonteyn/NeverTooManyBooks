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
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment {

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
        final View view = inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
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
        final Fields fields = getFields();

        // A DateFieldFormatter can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();

        fields.addBoolean(mReadCbx, DBDefinitions.KEY_READ);
        // when user sets 'read', also set the read-end date to today (unless set before)
        mReadCbx.setOnClickListener(v -> {
            Checkable cb = (Checkable) v;
            if (cb.isChecked()) {
                Field<String> readEnd = fields.getField(mDateReadEndView);
                if (readEnd.getAccessor().isEmpty()) {
                    String value = DateUtils.localSqlDateForToday();
                    // Update, display and notify
                    readEnd.getAccessor().setValue(value);
                    readEnd.onChanged();
                }
            }
        });

        fields.addBoolean(mSignedCbx, DBDefinitions.KEY_SIGNED);

        fields.addFloat(mRatingView, DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.addString(mNotesView, DBDefinitions.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        // MUST be defined before the currency.
        fields.addMoneyValue(mPricePaidView, DBDefinitions.KEY_PRICE_PAID);
        fields.addString(mPricePaidCurrencyView, DBDefinitions.KEY_PRICE_PAID_CURRENCY)
              .setRelatedFields(R.id.lbl_price_paid,
                                R.id.lbl_price_paid_currency, R.id.price_paid_currency)
              .setAutocomplete(mPricePaidCurrencyView, mBookModel.getPricePaidCurrencyCodes());

        fields.addString(mLocationView, DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long)
              .setAutocomplete(mLocationView, mBookModel.getLocations());

        //noinspection ConstantConditions
        fields.addBitmask(mEditionView, DBDefinitions.KEY_EDITION_BITMASK,
                          Book.getEditions(getContext()), true)
              .setRelatedFields(R.id.lbl_edition);

        fields.addString(mDateAcquiredView, DBDefinitions.KEY_DATE_ACQUIRED)
              .setRelatedFields(R.id.lbl_date_acquired)
              .setFormatter(dateFormatter)
              .addDatePicker(getChildFragmentManager(), mDateAcquiredView,
                             R.string.lbl_date_acquired, true);

        fields.addString(mDateReadStartView, DBDefinitions.KEY_READ_START)
              .setRelatedFields(R.id.lbl_read_start)
              .setFormatter(dateFormatter)
              .addDatePicker(getChildFragmentManager(), mDateReadStartView,
                             R.string.lbl_read_start, true);

        fields.addString(mDateReadEndView, DBDefinitions.KEY_READ_END)
              .setRelatedFields(R.id.lbl_read_end)
              .setFormatter(dateFormatter)
              .addDatePicker(getChildFragmentManager(), mDateReadEndView,
                             R.string.lbl_read_end, true);
    }

    @Override
    @CallSuper
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

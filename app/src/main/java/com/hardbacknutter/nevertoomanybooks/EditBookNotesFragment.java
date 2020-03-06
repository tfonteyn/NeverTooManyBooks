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
import android.widget.Checkable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Field;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.CompoundButtonAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.DecimalEditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment {

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    @Override
    protected void onInitFields() {
        super.onInitFields();
        final Fields fields = mFragmentVM.getFields();

        fields.add(R.id.cbx_read, DBDefinitions.KEY_READ, new CompoundButtonAccessor());
        fields.add(R.id.cbx_signed, DBDefinitions.KEY_SIGNED, new CompoundButtonAccessor());

        fields.add(R.id.rating, DBDefinitions.KEY_RATING, new RatingBarAccessor())
              .setRelatedFields(R.id.lbl_rating);

        fields.add(R.id.notes, DBDefinitions.KEY_PRIVATE_NOTES, new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_notes);

        // MUST be defined before the currency.
        fields.add(R.id.price_paid, DBDefinitions.KEY_PRICE_PAID,
                   new DecimalEditTextAccessor(new DoubleNumberFormatter(), false));
        fields.add(R.id.price_paid_currency, DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                   new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_price_paid,
                                R.id.lbl_price_paid_currency, R.id.price_paid_currency);

        fields.add(R.id.location, DBDefinitions.KEY_LOCATION, new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        //noinspection ConstantConditions
        fields.add(R.id.edition, DBDefinitions.KEY_EDITION_BITMASK,
                   new BitmaskChipGroupAccessor(Book.Edition.getEditions(getContext()), true))
              .setRelatedFields(R.id.lbl_edition);

        fields.add(R.id.date_acquired, DBDefinitions.KEY_DATE_ACQUIRED,
                   new EditTextAccessor<>(new DateFieldFormatter(), false))
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.add(R.id.read_start, DBDefinitions.KEY_READ_START,
                   new EditTextAccessor<>(new DateFieldFormatter(), false))
              .setRelatedFields(R.id.lbl_read_start);
        fields.add(R.id.read_end, DBDefinitions.KEY_READ_END,
                   new EditTextAccessor<>(new DateFieldFormatter(), false))
              .setRelatedFields(R.id.lbl_read_end);
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
        setOnClickListener(R.id.cbx_read, v -> {
            // when user sets 'read',
            // also set the read-end date to today (unless set before)
            Checkable cb = (Checkable) v;
            if (cb.isChecked()) {
                Field<String> readEnd = mFragmentVM.getFields().getField(R.id.read_end);
                if (readEnd.getAccessor().isEmpty()) {
                    String value = DateUtils.localSqlDateForToday();
                    // Update, display and notify
                    readEnd.getAccessor().setValue(value);
                    readEnd.onChanged();
                }
            }
        });

        addAutocomplete(R.id.price_paid_currency, mFragmentVM.getPricePaidCurrencyCodes());
        addAutocomplete(R.id.location, mFragmentVM.getLocations());

        addDatePicker(R.id.date_acquired, R.string.lbl_date_acquired, true, false);
        addDatePicker(R.id.read_start, R.string.lbl_read_start, true, false);
        addDatePicker(R.id.read_end, R.string.lbl_read_end, true, false);
    }
}

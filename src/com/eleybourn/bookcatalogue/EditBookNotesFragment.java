/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListItemBase;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is called by {@link EditBookActivity} and displays the Notes Tab
 */
public class EditBookNotesFragment extends BookAbstractFragment implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener,
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener {

    /** Lists in database so far, we cache them for performance */
    private List<String> mLocations;
    private List<String> mPricePaidCurrencies;

    /**
     * Load a location list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of locations
     */
    @NonNull
    private List<String> getLocations() {
        if (mLocations == null) {
            mLocations = mDb.getLocations();
        }
        return mLocations;
    }

    /**
     * Load a currency list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getPricePaidCurrencyCodes() {
        if (mPricePaidCurrencies == null) {
            mPricePaidCurrencies = mDb.getCurrencyCodes(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY);
        }
        return mPricePaidCurrencies;
    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    /**
     * Check the activity supports the interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof CheckListEditorDialogFragment.OnCheckListEditorResultsListener)) {
            throw new RTE.MustImplementException(context, CheckListEditorDialogFragment.OnCheckListEditorResultsListener.class);
        }
    }

    /**
     * has no specific Arguments or savedInstanceState as all is done via
     * {@link #getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause     */
    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        try {
            //noinspection ConstantConditions
            ViewUtils.fixFocusSettings(getView());
        } catch (Exception e) {
            // Log, but ignore. This is a non-critical feature that prevents crashes when the
            // 'next' key is pressed and some views have been hidden.
            Logger.error(e);
        }
    }

    @CallSuper
    @Override
    protected void initFields() {
        super.initFields();
        Field field;

        // non-text; simple checkbox
        mFields.add(R.id.read, UniqueId.KEY_BOOK_READ);

        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED);
        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING);

        mFields.add(R.id.notes, UniqueId.KEY_NOTES);
        //ENHANCE?: initTextFieldEditor(R.id.notes, R.string.lbl_notes, R.id.btn_notes, true);

        mFields.add(R.id.price_paid, UniqueId.KEY_BOOK_PRICE_PAID);
        field = mFields.add(R.id.price_paid_currency, UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY);
        initValuePicker(field, R.string.currency, R.id.btn_price_paid_currency, getPricePaidCurrencyCodes());

        field = mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION);
        initValuePicker(field, R.string.lbl_location, R.id.btn_location, getLocations());

        field = mFields.add(R.id.edition, UniqueId.KEY_BOOK_EDITION_BITMASK)
                .setFormatter(new Fields.BookEditionsFormatter());
        initCheckListEditor(field, R.string.lbl_edition, getBook().getEditableEditionList());


        // ENHANCE: Add a partial date validator. Or not.
        //FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());
        FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        field = mFields.add(R.id.date_purchased, UniqueId.KEY_BOOK_DATE_ADDED)
                .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_date_purchased, true);

        field = mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START)
                .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_read_start, true);

        field = mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END)
                .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_read_end, true);

        mFields.addCrossValidator(new Fields.FieldCrossValidator() {
            public void validate(final @NonNull Fields fields, final @NonNull Bundle values) throws ValidatorException{
                String start = values.getString(UniqueId.KEY_BOOK_READ_START);
                if (start == null || start.isEmpty()) {
                    return;
                }
                String end = values.getString(UniqueId.KEY_BOOK_READ_END);
                if (end == null || end.isEmpty()) {
                    return;
                }
                if (start.compareToIgnoreCase(end) > 0) {
                    throw new ValidatorException(R.string.vldt_read_start_after_end, new Object[]{});
                }
            }
        });
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(final @NonNull Book book, final boolean setAllFrom) {
        super.onLoadFieldsFromBook(book, setAllFrom);

        // populateFields: all done in super

        // Restore default visibility
        showHideFields(false);

        if (BuildConfig.DEBUG) {
            Logger.info(this, "onLoadFieldsFromBook done");
        }
    }

    @Override
    @CallSuper
    public void onPause() {
        mFields.putAllInto(getBook());
        super.onPause();
    }

    /**
     * Overriding to get some debug
     */
    @Override
    protected void onSaveFieldsToBook(final @NonNull Book book) {
        super.onSaveFieldsToBook(book);

        if (BuildConfig.DEBUG) {
            Logger.info(this, "onSaveFieldsToBook done");
        }
    }

    @Override
    public <T> void onCheckListEditorSave(final @NonNull CheckListEditorDialogFragment dialog,
                                          final int destinationFieldId,
                                          final @NonNull List<CheckListItem<T>> list) {
        dialog.dismiss();

        if (destinationFieldId == R.id.edition) {
            ArrayList<Integer> result = CheckListItemBase.extractList(list);
            getBook().putEditions(result);
            mFields.getField(destinationFieldId).setValue(getBook().getString(UniqueId.KEY_BOOK_EDITION_BITMASK));
        }
    }

    @Override
    public void onCheckListEditorCancel(final @NonNull CheckListEditorDialogFragment dialog,
                                        final @IdRes int destinationFieldId) {
        dialog.dismiss();
    }

    @Override
    public void onPartialDatePickerSave(@NonNull final PartialDatePickerDialogFragment dialog,
                                        final int destinationFieldId,
                                        @Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
        dialog.dismiss();
        mFields.getField(destinationFieldId).setValue(DateUtils.buildPartialDate(year, month, day));
    }

    @Override
    public void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                          final int destinationFieldId) {
        dialog.dismiss();
    }
}
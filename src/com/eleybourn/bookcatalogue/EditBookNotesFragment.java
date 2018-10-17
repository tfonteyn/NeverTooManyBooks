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

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.util.Date;
import java.util.List;

/**
 * This class is called by {@link EditBookActivity} and displays the Notes Tab
 */
public class EditBookNotesFragment extends BookAbstractFragment implements OnPartialDatePickerListener {

    /** Lists in database so far, we cache them for performance */
    private List<String> mLocations;

    /**
     * Load a location list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of locations
     */
    @NonNull
    public List<String> getLocations() {
        if (mLocations == null) {
            mLocations = mDb.getLocations();
        }
        return mLocations;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                getEditBookManager().setDirty(false);
            }

            initFields();

            try {
                //noinspection ConstantConditions
                ViewUtils.fixFocusSettings(getView());
            } catch (Exception e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.error(e);
            }

        } catch (Exception e) {
            Logger.error(e);
        }
        Tracker.exitOnCreate(this);
    }

    protected void initFields() {

        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING, null);
        mFields.add(R.id.lbl_rating, "", UniqueId.KEY_BOOK_RATING, null);
        mFields.add(R.id.read, UniqueId.KEY_BOOK_READ, null);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES, null);
        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED, null);

        /* location  TODO: unify with {@link EditBookFieldsFragment#setupMenuMoreButton} */
        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION, null);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, getLocations());
        mFields.setAdapter(R.id.location, adapter);

        final Field locationField = mFields.getField(R.id.location);
        // Get the list to use in the AutoComplete stuff
        AutoCompleteTextView textView = locationField.getView();
        textView.setAdapter(new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, getLocations()));
        // Get the drop-down button for the list and setup dialog
        //noinspection ConstantConditions
        getView().findViewById(R.id.location_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        StandardDialogs.selectStringDialog(requireActivity().getLayoutInflater(),
                                getString(R.string.location),
                                getLocations(), locationField.getValue().toString(),
                                new StandardDialogs.SimpleDialogOnClickListener() {
                                    @Override
                                    public void onClick(@NonNull final StandardDialogs.SimpleDialogItem item) {
                                        locationField.setValue(item.toString());
                                    }
                                });
                    }
                });

        // ENHANCE: Add a partial date validator. Or not.
        //FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());
        FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        Field field;
        field = mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START, null, dateFormatter);
        field.getView().setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance()
                        .setTitle(R.string.read_start)
                        .setDialogId(R.id.read_start); // Set to the destination field ID
                try {
                    frag.setDate(getDateFrom(R.id.read_start));
                } catch (Exception ignore) {
                    // use the default date
                }

                frag.show(requireFragmentManager(), null);
            }
        });

        field = mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END, null, dateFormatter);
        field.getView().setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance()
                        .setTitle(R.string.read_end)
                        .setDialogId(R.id.read_end); // Set to the destination field ID
                try {
                    frag.setDate(getDateFrom(R.id.read_end));
                } catch (Exception ignore) {
                    // use the default date
                }
                frag.show(requireFragmentManager(), null);
            }
        });

        mFields.addCrossValidator(new Fields.FieldCrossValidator() {
            public void validate(@NonNull final Fields fields, @NonNull final Bundle values) {
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

        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            @Override
            public void afterFieldChange(@NonNull final Field field, @Nullable final String newValue) {
                getEditBookManager().setDirty(true);
            }
        });
    }

    @NonNull
    private String getDateFrom(@IdRes final int fieldResId) {
        Object value = mFields.getField(fieldResId).getValue();
        if (value.toString().isEmpty()) {
            return DateUtils.toSqlDateTime(new Date());
        } else {
            return value.toString();
        }
    }

    /**
     * The callback received when the user "sets" the date in the dialog.
     *
     * Build a full or partial date in SQL format
     */
    @Override
    public void onPartialDatePickerSet(final int dialogId,
                                       @NonNull final PartialDatePickerFragment dialog,
                                       @Nullable final Integer year,
                                       @Nullable final Integer month,
                                       @Nullable final Integer day) {
        mFields.getField(dialogId).setValue(DateUtils.buildPartialDate(year, month, day));
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(final int dialogId, @NonNull final PartialDatePickerFragment dialog) {
        dialog.dismiss();
    }

    @Override
    @CallSuper
    public void onPause() {
        mFields.getAllInto(getBook());
        super.onPause();
    }

    @Override
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        super.onLoadBookDetails(book, setAllDone);

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);
    }
}
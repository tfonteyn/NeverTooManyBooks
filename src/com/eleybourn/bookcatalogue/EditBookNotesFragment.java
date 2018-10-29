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
import com.eleybourn.bookcatalogue.dialogs.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class is called by {@link EditBookActivity} and displays the Notes Tab
 */
public class EditBookNotesFragment extends BookAbstractFragment
        implements
        PartialDatePickerDialogFragment.OnPartialDatePickerResultListener,
        CheckListEditorDialogFragment.OnCheckListChangedListener {

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
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            setDirty(false);
        }

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

        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING);
        mFields.add(R.id.read, UniqueId.KEY_BOOK_READ);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES);
        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED);

        /*
        setOutputOnly, so the field is not fetched from the database
        source column is "", so this field is not saved to the database
        ... so what do we get ?
        -> all interaction between View and controller.
         */
        mFields.add(R.id.edition, "", UniqueId.KEY_BOOK_EDITION_BITMASK)
                .setOutputOnly(true);
        mFields.getField(R.id.edition).getView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                CheckListEditorDialogFragment<Integer> c = new CheckListEditorDialogFragment<>();
                c.setTitle(R.string.edition);
                c.setDestinationFieldId(R.id.edition);
                // if we chain these calls, then lint complains as the param type will be wrong
                c.setList(getBook().getEditableEditionList());
                c.show(requireFragmentManager(), null);
            }
        });


        /* location  TOMF: unify with {@link EditBookFieldsFragment#setupMenuMoreButton} */
        mFields.add(R.id.lbl_location, UniqueId.KEY_BOOK_LOCATION);
        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, getLocations());
        mFields.setAdapter(R.id.location, adapter);

        // Get the list to use in the AutoComplete Location field
        final Field locationField = mFields.getField(R.id.location);
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
                                getLocations(), locationField.putValueInto().toString(),
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
        field = mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START)
                .setFormatter(dateFormatter);
        field.getView().setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                PartialDatePickerDialogFragment frag = PartialDatePickerDialogFragment.newInstance()
                        .setTitle(R.string.read_start)
                        .setDestinationFieldId(R.id.read_start);
                try {
                    frag.setDate(getDateFrom(R.id.read_start));
                } catch (Exception ignore) {
                    // use the default date
                }

                frag.show(requireFragmentManager(), null);
            }
        });

        field = mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END)
                .setFormatter(dateFormatter);
        field.getView().setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                PartialDatePickerDialogFragment frag = PartialDatePickerDialogFragment.newInstance()
                        .setTitle(R.string.read_end)
                        .setDestinationFieldId(R.id.read_end);
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
                setDirty(true);
            }
        });
    }

    @NonNull
    private String getDateFrom(@IdRes final int fieldResId) {
        Object value = mFields.getField(fieldResId).putValueInto();
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
    public void onPartialDatePickerSet(@NonNull final PartialDatePickerDialogFragment dialog,
                                       @IdRes final int destinationFieldId,
                                       @Nullable final Integer year,
                                       @Nullable final Integer month,
                                       @Nullable final Integer day) {
        mFields.getField(destinationFieldId).setValue(DateUtils.buildPartialDate(year, month, day));
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                          @IdRes final int destinationFieldId) {
        dialog.dismiss();
    }

    @Override
    @CallSuper
    public void onPause() {
        mFields.putAllInto(getBook());
        super.onPause();
    }

    @Override
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllFrom) {
        super.onLoadBookDetails(book, setAllFrom);

        populateEditions(mFields.getField(R.id.edition), book);

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);
    }

    /**
     * Overriding to get some debug
     */
    @Override
    protected void onSaveBookDetails(@NonNull final Book book) {
        if (BuildConfig.DEBUG) {
            Logger.info(this,"onSaveBookDetails");
        }
        // nothing special, just let all Fields be copied into the book
        super.onSaveBookDetails(book);
    }

    @Override
    public <T> void onCheckListSave(@NonNull final CheckListEditorDialogFragment dialog,
                                    final int destinationFieldId,
                                    @NonNull final List<CheckListItem<T>> list) {
        dialog.dismiss();

        ArrayList<Integer> result = new Book.EditionCheckListItem().extractList(list);
        int bitmask = 0;
        for (Integer bit : result) {
            bitmask += bit;
        }
        getBook().putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);
        mFields.getField(destinationFieldId).setValue(getBook().getEditionListAsText());
    }

    @Override
    public void onCheckListCancel(@NonNull final CheckListEditorDialogFragment dialog,
                                  @IdRes final int destinationFieldId) {
        dialog.dismiss();
    }
}
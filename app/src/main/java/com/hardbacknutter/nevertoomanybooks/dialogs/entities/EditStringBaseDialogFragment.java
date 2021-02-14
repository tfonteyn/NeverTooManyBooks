/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Base Dialog class to edit an <strong>in-line in Books table</strong> String field.
 */
public abstract class EditStringBaseDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditStringBaseDialog";
    /** Argument. */
    static final String BKEY_TEXT = TAG + ":text";
    protected static final String BKEY_REQUEST_KEY = TAG + ":rk";

    @StringRes
    private final int mDialogTitleId;
    @StringRes
    private final int mLabelId;
    @BooksOnBookshelf.RowChangeListener.Change
    private final int mFieldFlag;
    /** Database Access. */
    @Nullable
    DAO mDb;
    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    /** View Binding. */
    private DialogEditStringBinding mVb;
    /** The text we're editing. */
    private String mOriginalText;
    /** Current edit. */
    private String mCurrentText;

    /**
     * Constructor; only used by the child class no-args constructor.
     *
     * @param titleId   for the dialog (i.e. the toolbar)
     * @param label     to use for the 'hint' of the input field
     * @param fieldFlag one of the {@link BooksOnBookshelf.RowChangeListener.Change} bits
     */
    EditStringBaseDialogFragment(@StringRes final int titleId,
                                 @StringRes final int label,
                                 @BooksOnBookshelf.RowChangeListener.Change final int fieldFlag) {
        super(R.layout.dialog_edit_string);

        mDialogTitleId = titleId;
        mLabelId = label;
        mFieldFlag = fieldFlag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mDb = new DAO(getContext(), TAG);
        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mOriginalText = args.getString(BKEY_TEXT, "");

        if (savedInstanceState == null) {
            mCurrentText = mOriginalText;
        } else {
            mCurrentText = savedInstanceState.getString(BKEY_TEXT, "");
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditStringBinding.bind(view);

        mVb.toolbar.setTitle(mDialogTitleId);

        mVb.lblEditString.setHint(getString(mLabelId));
        mVb.lblEditString.setErrorEnabled(true);

        mVb.editString.setText(mCurrentText);

        // soft-keyboards 'done' button act as a shortcut to confirming/saving the changes
        mVb.editString.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        final List<String> objects = getList();
        if (objects != null) {
            //noinspection ConstantConditions
            final ExtArrayAdapter<String> adapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    ExtArrayAdapter.FilterType.Diacritic, objects);
            mVb.editString.setAdapter(adapter);
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    /**
     * Get the (optional) list of strings for the auto-complete.
     *
     * @return list, or {@code null} if there is none
     */
    @Nullable
    protected List<String> getList() {
        return null;
    }

    private boolean saveChanges() {
        viewToModel();
        if (mCurrentText.isEmpty()) {
            showError(mVb.lblEditString, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mCurrentText.equals(mOriginalText)) {
            return true;
        }

        onSave(mOriginalText, mCurrentText);

        BooksOnBookshelf.RowChangeListener.setResult(this, mRequestKey, mFieldFlag, 0);
        return true;
    }

    private void viewToModel() {
        mCurrentText = mVb.editString.getText().toString().trim();
    }

    /**
     * Save data.
     *
     * @param originalText the original text which was passed in to be edited
     * @param currentText  the modified text
     */
    abstract void onSave(String originalText,
                         String currentText);

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BKEY_TEXT, mCurrentText);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}

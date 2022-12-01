/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
 * <p>
 * For now this + its 3 sibling classes are not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 *
 * @see EditBookshelfDialogFragment
 * @see EditSeriesDialogFragment
 * @see EditPublisherDialogFragment
 */
public class EditAuthorDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditAuthorDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** View Binding. */
    private DialogEditAuthorBinding vb;

    /** The Author we're editing. */
    private Author author;

    /** Current edit. */
    private Author currentEdit;

    /**
     * No-arg constructor for OS use.
     */
    public EditAuthorDialogFragment() {
        super(R.layout.dialog_edit_author);
    }

    /**
     * Launch the dialog.
     *
     * @param fm     The FragmentManager this fragment will be added to.
     * @param author to edit.
     */
    public static void launch(@NonNull final FragmentManager fm,
                              @NonNull final Author author) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, RowChangedListener.REQUEST_KEY);
        args.putParcelable(DBKey.FK_AUTHOR, author);

        final DialogFragment frag = new EditAuthorDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        author = Objects.requireNonNull(args.getParcelable(DBKey.FK_AUTHOR), DBKey.FK_AUTHOR);

        if (savedInstanceState == null) {
            currentEdit = new Author(author.getFamilyName(),
                                     author.getGivenNames(),
                                     author.isComplete());
        } else {
            //noinspection ConstantConditions
            currentEdit = savedInstanceState.getParcelable(DBKey.FK_AUTHOR);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditAuthorBinding.bind(view);

        final Context context = getContext();
        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_FAMILY_NAME));

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_GIVEN_NAMES));

        vb.familyName.setText(currentEdit.getFamilyName());
        vb.familyName.setAdapter(familyNameAdapter);
        vb.givenNames.setText(currentEdit.getGivenNames());
        vb.givenNames.setAdapter(givenNameAdapter);
        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        // don't requestFocus() as we have multiple fields.
    }

    @Nullable
    @Override
    protected Button mapButton(@NonNull final Button actionButton,
                               @NonNull final View buttonPanel) {
        if (actionButton.getId() == R.id.btn_save) {
            return buttonPanel.findViewById(R.id.btn_positive);
        }
        return null;
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            if (button.getId() == R.id.btn_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        if (currentEdit.getFamilyName().isEmpty()) {
            showError(vb.lblFamilyName, R.string.vldt_non_blank_required);
            return false;
        }

        final Context context = getContext();
        final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();

        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        final boolean nameChanged = !author.getFamilyName().equals(currentEdit.getFamilyName())
                                    || !author.getGivenNames().equals(currentEdit.getGivenNames());

        // anything changed at all ? If not, we're done.
        if (!nameChanged
            && author.isComplete() == currentEdit.isComplete()) {
            return true;
        }

        // store changes
        author.copyFrom(currentEdit, false);


        if (author.getId() == 0) {
            // It's a new one. Check if there is an existing one with the same name
            // noinspection ConstantConditions
            final long existingId = dao.find(context, author, true, bookLocale);
            if (existingId == 0) {
                // it's an entirely new one; add it.
                if (dao.insert(context, author) > 0) {
                    RowChangedListener.setResult(this, requestKey,
                                                 DBKey.FK_AUTHOR, author.getId());
                    return true;
                }
            } else {
                // There is one with the same name; ask whether to merge the 2
                askToMerge(author, existingId);
            }
        } else {
            // It's an existing one
            if (nameChanged) {
                // but the name was changed. Check if there is an existing one with the same name
                //noinspection ConstantConditions
                final long existingId = dao.find(context, author, true, bookLocale);
                if (existingId == 0) {
                    // no-one else with the same name; so we just update this one
                    if (dao.update(context, author)) {
                        RowChangedListener.setResult(this, requestKey,
                                                     DBKey.FK_AUTHOR, author.getId());
                        return true;
                    }
                } else {
                    // There is one with the same name; ask whether to merge the 2
                    askToMerge(author, existingId);
                }
            } else {
                // The name was not changed; just update the other attributes
                //noinspection ConstantConditions
                if (dao.update(context, author)) {
                    RowChangedListener.setResult(this, requestKey,
                                                 DBKey.FK_AUTHOR, author.getId());
                    return true;
                }
            }
        }

        return false;
    }

    private void askToMerge(@NonNull final Author source,
                            final long targetId) {
        final Context context = getContext();
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(source.getLabel(context))
                .setMessage(R.string.confirm_merge_authors)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_merge, (d, w) -> {
                    dismiss();
                    try {
                        final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();
                        final Author target = Objects.requireNonNull(dao.getById(targetId));
                        //URGENT: should we copy these extra attributes ? Probably NOT...
                        // target.setComplete(current.isComplete());
                        // target.setRealAuthorId(current.getRealAuthorId());
                        dao.moveBooks(context, source, target);

                        // return the author who 'lost' their books
                        RowChangedListener.setResult(this, requestKey,
                                                     DBKey.FK_AUTHOR, source.getId());
                    } catch (@NonNull final DaoWriteException e) {
                        Logger.error(TAG, e);
                        StandardDialogs.showError(context, R.string.error_storage_not_writable);
                    }
                })
                .create()
                .show();
    }

    private void viewToModel() {
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_AUTHOR, currentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}

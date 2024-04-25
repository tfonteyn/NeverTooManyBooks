/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditPublisherContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Publisher}.
 * For now this class is not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 * <ul>
 * <li>Direct/in-place editing.</li>
 * <li>Modifications ARE STORED in the database</li>
 * <li>Returns the modified item.</li>
 * </ul>
 *
 * @see EditAuthorDialogFragment
 * @see EditSeriesDialogFragment
 * @see EditPublisherDialogFragment
 * @see EditBookshelfDialogFragment
 */
public class EditPublisherDialogFragment
        extends EditMergeableDialogFragment<Publisher> {

    /** Fragment/Log tag. */
    public static final String TAG = "EditPublisherDialogFrag";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** View Binding. */
    private DialogEditPublisherContentBinding vb;

    /** The Publisher we're editing. */
    private Publisher publisher;

    /** Current edit. */
    private Publisher currentEdit;

    /**
     * No-arg constructor for OS use.
     */
    public EditPublisherDialogFragment() {
        super(R.layout.dialog_edit_publisher, R.layout.dialog_edit_publisher_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(
                args.getString(ParcelableDialogLauncher.BKEY_REQUEST_KEY),
                ParcelableDialogLauncher.BKEY_REQUEST_KEY);
        publisher = Objects.requireNonNull(
                args.getParcelable(ParcelableDialogLauncher.BKEY_ITEM),
                ParcelableDialogLauncher.BKEY_ITEM);

        if (savedInstanceState == null) {
            currentEdit = new Publisher(publisher.getName());
        } else {
            currentEdit = savedInstanceState.getParcelable(DBKey.FK_PUBLISHER);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditPublisherContentBinding.bind(view.findViewById(R.id.dialog_content));

        //noinspection DataFlowIssue
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getPublisherDao().getNames());

        vb.publisherName.setText(currentEdit.getName());
        vb.publisherName.setAdapter(nameAdapter);
        autoRemoveError(vb.publisherName, vb.lblPublisherName);

        vb.publisherName.requestFocus();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
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

        if (currentEdit.getName().isEmpty()) {
            vb.lblPublisherName.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // Case-sensitive! We must allow the user to correct case.
        final boolean nameChanged = !publisher.isSameName(currentEdit);

        // anything actually changed ? If not, we're done.
        if (nameChanged) {
            return true;
        }

        // store changes
        publisher.copyFrom(currentEdit);

        final Context context = requireContext();
        final PublisherDao dao = ServiceLocator.getInstance().getPublisherDao();
        final Locale locale = getResources().getConfiguration().getLocales().get(0);

        final Consumer<Publisher> onSuccess = savedPublisher -> ParcelableDialogLauncher
                .setEditInPlaceResult(this, requestKey, savedPublisher);

        try {
            if (publisher.getId() == 0) {
                // Check if there is an another one with the same new name.
                final Optional<Publisher> existingEntity =
                        dao.findByName(context, publisher, locale);

                if (existingEntity.isPresent()) {
                    // There is one with the same name; ask whether to merge the 2
                    askToMerge(dao, R.string.confirm_merge_publishers,
                               publisher, existingEntity.get(), onSuccess);
                    return false;
                } else {
                    // Just insert or update as needed
                    if (publisher.getId() == 0) {
                        dao.insert(context, publisher, locale);
                    } else {
                        dao.update(context, publisher, locale);
                    }
                    onSuccess.accept(publisher);
                    return true;
                }
            } else {
                // It's an existing one and the name was not changed;
                // just update the other attributes
                dao.update(context, publisher, locale);
                onSuccess.accept(publisher);
                return true;
            }
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, publisher);
            return false;
        }
    }

    private void viewToModel() {
        currentEdit.setName(vb.publisherName.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_PUBLISHER, currentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}

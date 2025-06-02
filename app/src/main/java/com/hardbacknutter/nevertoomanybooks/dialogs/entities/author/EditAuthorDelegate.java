/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.author;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAction;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate.PartialDatePickerLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
 * For now this class is not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 * <ul>
 * <li>{@link EditAction#EditInPlace}</li>
 * <li>Modifications <strong>ARE STORED</strong> in the database</li>
 * <li>Returns the modified item.</li>
 * <li>Supports merging.</li>
 * </ul>
 */
class EditAuthorDelegate
        implements FlexDialogDelegate {

    private static final String TAG = "EditAuthorDelegate";
    private static final String RK_DATE_PICKER_PARTIAL = TAG + ":rk:pd";
    private static final String BKEY_DATE_PICKER_FIELD_ID = TAG + ":pd:fieldId";

    /** Author View model. Fragment scope. */
    private final EditAuthorViewModel vm;
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    private final PartialDatePickerLauncher partialDatePickerLauncher;
    private final FieldFormatter<String> dateFieldFormatter;
    /** View Binding. */
    private DialogEditAuthorContentBinding vb;
    /** MUST keep a strong reference. */
    private final PartialDatePickerLauncher.ResultListener datePickerListener = this::onDateSet;
    @Nullable
    private Toolbar toolbar;

    EditAuthorDelegate(@NonNull final DialogFragment owner,
                       @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(EditAuthorViewModel.class);
        vm.init(args);

        final FragmentManager fm = owner.getChildFragmentManager();

        partialDatePickerLauncher = new PartialDatePickerLauncher(RK_DATE_PICKER_PARTIAL);
        partialDatePickerLauncher.setResultListener(datePickerListener);
        partialDatePickerLauncher.registerForFragmentResult(fm, owner);

        dateFieldFormatter = new DateFieldFormatter(
                owner.getResources().getConfiguration().getLocales().get(0), false);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditAuthorContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_author, container, false);
        vb = DialogEditAuthorContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @NonNull
    public Toolbar getToolbar() {
        return Objects.requireNonNull(toolbar, "No toolbar set");
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        final Context context = vb.getRoot().getContext();

        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
        }

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        setupNames(context, authorDao);
        setupRealAuthorField(context, authorDao);
        setupBirthDate(context);
        setupDeathDate(context);

        vb.cbxIsComplete.setChecked(vm.getCurrentEdit().isComplete());

        vb.familyName.requestFocus();
    }

    private void setupNames(@NonNull final Context context,
                            @NonNull final AuthorDao authorDao) {
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR.FAMILY_NAME));
        vb.familyName.setText(vm.getCurrentEdit().getFamilyName());
        vb.familyName.setAdapter(familyNameAdapter);
        TilUtil.autoRemoveError(vb.familyName, vb.lblFamilyName);

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR.GIVEN_NAMES));
        vb.givenNames.setText(vm.getCurrentEdit().getGivenNames());
        vb.givenNames.setAdapter(givenNameAdapter);
    }

    private void setupRealAuthorField(@NonNull final Context context,
                                      @NonNull final AuthorDao authorDao) {
        if (vm.showRealAuthorName()) {
            vb.lblRealAuthorHeader.setVisibility(View.VISIBLE);
            vb.lblRealAuthor.setVisibility(View.VISIBLE);

            final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                    context, R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    authorDao.getNames(DBKey.AUTHOR.FORMATTED_FULL_NAME));
            vb.realAuthor.setText(vm.getCurrentRealAuthorName(), false);
            vb.realAuthor.setAdapter(realNameAdapter);
            TilUtil.autoRemoveError(vb.realAuthor, vb.lblRealAuthor);

        } else {
            vb.lblRealAuthorHeader.setVisibility(View.GONE);
            vb.lblRealAuthor.setVisibility(View.GONE);
        }
    }

    private void setupBirthDate(@NonNull final Context context) {
        vb.birthDate.setText(dateFieldFormatter.format(
                context, vm.getCurrentEdit().getBirthDate().orElse(null)));
        vb.lblBirthDate.setEndIconOnClickListener(v -> {
            vm.getCurrentEdit().setBirthDate(null);
            vb.birthDate.setText(null);
        });
        vb.birthDate.setOnClickListener(v -> {
            // We're using the extras to pass the field id
            final Bundle extras = new Bundle(1);
            extras.putInt(BKEY_DATE_PICKER_FIELD_ID, vb.birthDate.getId());
            //noinspection DataFlowIssue
            partialDatePickerLauncher.launch(
                    owner.getActivity(),
                    context.getString(R.string.lbl_date_born),
                    null,
                    vm.getCurrentEdit().getBirthDate().orElse(null),
                    extras);
        });
    }

    private void setupDeathDate(@NonNull final Context context) {
        vb.deathDate.setText(dateFieldFormatter.format(
                context, vm.getCurrentEdit().getDeathDate().orElse(null)));
        vb.lblDeathDate.setEndIconOnClickListener(v -> {
            vm.getCurrentEdit().setDeathDate(null);
            vb.deathDate.setText(null);
        });
        vb.deathDate.setOnClickListener(v -> {
            // We're using the extras to pass the field id
            final Bundle extras = new Bundle(1);
            extras.putInt(BKEY_DATE_PICKER_FIELD_ID, vb.deathDate.getId());
            //noinspection DataFlowIssue
            partialDatePickerLauncher.launch(
                    owner.getActivity(),
                    context.getString(R.string.lbl_date_died),
                    null,
                    vm.getCurrentEdit().getDeathDate().orElse(null),
                    extras);
        });
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges(false)) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges(final boolean createRealAuthorIfNeeded) {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        final Author currentEdit = vm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getFamilyName().isEmpty()) {
            vb.lblFamilyName.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // We let this call go ahead even if real-author is switched off by the user
        // so we can clean up as needed.
        if (!vm.validateAndSetRealAuthor(context, locale, createRealAuthorIfNeeded)) {
            warnThatRealAuthorMustBeValid(context);
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        try {
            final Optional<Author> existingEntity = vm.saveIfUnique(context);
            if (existingEntity.isEmpty()) {
                // Success
                EditParcelableLauncher.setEditInPlaceResult(owner, requestKey, vm.getOriginal());
                return true;
            }

            // There is one with the same name; ask whether to merge the 2
            StandardDialogs.askToMerge(context, R.string.confirm_merge_authors,
                                       vm.getOriginal().getLabel(context), () -> {
                        owner.dismiss();
                        try {
                            vm.move(context, existingEntity.get());
                            // return the item which 'lost' it's books
                            EditParcelableLauncher.setEditInPlaceResult(owner, requestKey,
                                                                        vm.getOriginal());
                        } catch (@NonNull final DaoWriteException e) {
                            // log, but ignore - should never happen unless disk full
                            LoggerFactory.getLogger().e(TAG, e, vm.getOriginal());
                        }
                    });
            return false;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, vm.getOriginal());
            return false;
        }
    }

    private void warnThatRealAuthorMustBeValid(@NonNull final Context context) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.vldt_real_author_must_be_valid)
                .setMessage(context.getString(R.string.confirm_create_real_author,
                                              vm.getCurrentRealAuthorName()))
                .setNegativeButton(R.string.action_edit, (d, w) -> vb.lblRealAuthor.setError(
                        context.getString(R.string.vldt_real_author_must_be_valid)))
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    if (saveChanges(true)) {
                        // finish the DialogFragment
                        owner.dismiss();
                    }
                })
                .create()
                .show();
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        final Author currentEdit = vm.getCurrentEdit();

        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());

        // Dates have already been set in the PartialDatePickerLauncher.ResultListener

        if (vm.showRealAuthorName()) {
            vm.setCurrentRealAuthorName(vb.realAuthor.getText().toString().trim());
        }

        currentEdit.setComplete(vb.cbxIsComplete.isChecked());
    }

    private void onDateSet(@NonNull final PartialDate previousSelection,
                           @NonNull final PartialDate currentSelection,
                           @Nullable final Bundle extras) {
        if (extras == null) {
            throw new IllegalArgumentException("No extras?");
        }
        final int fieldId = extras.getInt(BKEY_DATE_PICKER_FIELD_ID, -1);
        if (fieldId == -1) {
            throw new IllegalArgumentException("No fieldId?");
        }

        final Context context = owner.getContext();
        // set BOTH author and fields.
        final Author currentEdit = vm.getCurrentEdit();
        final String isoString = currentSelection.getIsoString();
        //noinspection DataFlowIssue
        final CharSequence display = dateFieldFormatter.format(context, isoString);
        if (fieldId == vb.birthDate.getId()) {
            currentEdit.setBirthDate(isoString);
            vb.birthDate.setText(display);
        } else if (fieldId == vb.deathDate.getId()) {
            currentEdit.setDeathDate(isoString);
            vb.deathDate.setText(display);
        }
    }
}

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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.tag;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditTagContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * This is the editor for the BoB {@link Tag} BooklistGroup.
 * i.e. called from the row-menu of a tag grouping.
 * <p>
 * This allows editing the tag name and potential merging with another tag.
 * <p>
 * IMPORTANT: EditTagDelegate and TagEditorFragment have partially overlapping
 * functionality which should kept in sync.
 */
class EditTagDelegate
        implements FlexDialogDelegate {

    private static final String TAG = "EditTagDelegate";

    private final EditTagViewModel vm;

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    /** View Binding. */
    private DialogEditTagContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    EditTagDelegate(@NonNull final DialogFragment owner,
                    @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(EditTagViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditTagContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_tag, container, false);
        vb = DialogEditTagContentBinding.bind(view.findViewById(R.id.dialog_content));
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
        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
        }

        final Context context = vb.getRoot().getContext();

        final Tag currentEdit = vm.getCurrentEdit();

        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getTagDao().getAll()
                              .stream().map(Tag::getName).collect(Collectors.toList()));

        vb.tagName.setText(currentEdit.getName());
        vb.tagName.setAdapter(nameAdapter);
        TilUtil.autoRemoveError(vb.tagName, vb.lblTagName);

        vb.tagName.requestFocus();
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
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }


    private boolean saveChanges() {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        final Tag currentEdit = vm.getCurrentEdit();
        if (currentEdit.getName().isEmpty()) {
            vb.lblTagName.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        try {
            final Optional<Tag> existingEntity = vm.saveIfUnique(context);
            if (existingEntity.isEmpty()) {
                // Success
                EditParcelableLauncher.setEditInPlaceResult(owner, requestKey, vm.getOriginal());
                return true;
            }

            // There is one with the same name; ask whether to merge the 2
            StandardDialogs.askToMerge(context, R.string.confirm_merge_tags,
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

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        vm.getCurrentEdit().setName(vb.tagName.getText().toString().trim());
    }
}

/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelf;

import android.content.Context;
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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtClearTextEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Bookshelf}.
 * For now this class is not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 * <ul>
 * <li>{@link EditInPlaceParcelableLauncher}</li>
 * <li>Modifications <strong>ARE STORED</strong> in the database</li>
 * <li>Returns the modified item.</li>
 * <li>Supports merging.</li>
 * </ul>
 */
class EditBookshelfDelegate
        implements FlexDialogDelegate {

    private static final String TAG = "EditBookshelfDelegate";
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    private final EditBookshelfViewModel vm;
    /** View Binding. */
    private DialogEditBookshelfContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    /**
     * Constructor.
     * <p>
     * Class output: {@link EditInPlaceParcelableLauncher.Output}.
     *
     * @param owner hosting Fragment
     * @param args  all arguments
     */
    EditBookshelfDelegate(@NonNull final DialogFragment owner,
                          @NonNull final EditParcelableInput<Bookshelf> args) {
        this.owner = owner;
        requestKey = args.getRequestKey();
        vm = new ViewModelProvider(owner).get(EditBookshelfViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditBookshelfContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_bookshelf, container, false);
        vb = DialogEditBookshelfContentBinding.bind(view.findViewById(R.id.dialog_content));
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

        final Bookshelf currentEdit = vm.getCurrentEdit();
        initName(currentEdit);

        vb.bookshelf.requestFocus();
    }

    private void initName(@NonNull final Bookshelf bookshelf) {
        vb.bookshelf.setText(bookshelf.getName());
        TilUtil.autoRemoveError(vb.bookshelf, vb.lblBookshelf);
        ExtClearTextEndIconDelegate.attach(vb.lblBookshelf, null);
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

        if (vm.getCurrentEdit().getName().isEmpty()) {
            vb.lblBookshelf.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }


        try {
            final Optional<Bookshelf> existingEntity = vm.saveIfUnique(context);
            if (existingEntity.isEmpty()) {
                // Success
                new EditInPlaceParcelableLauncher.Output<>(vm.getOriginal())
                        .send(owner, requestKey);
                return true;
            }

            // The logic flow here is different from the default one as used for e.g. an Author.
            // IF the user meant to create a NEW Bookshelf
            // REJECT an already existing Bookshelf with the same name.
            if (vm.getOriginal().getId() == 0) {
                vb.lblBookshelf.setError(context.getString(
                        R.string.warning_x_already_exists,
                        context.getString(R.string.lbl_bookshelf)));
                return false;
            }

            // There is one with the same name; ask whether to merge the 2
            StandardDialogs.askToMerge(context, R.string.confirm_merge_bookshelves,
                                       vm.getOriginal().getLabel(context), () -> {
                        owner.dismiss();
                        try {
                            vm.move(context, existingEntity.get());
                            // return the item which 'lost' it's books
                            new EditInPlaceParcelableLauncher.Output<>(vm.getOriginal())
                                    .send(owner, requestKey);
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
        //noinspection DataFlowIssue
        vm.getCurrentEdit().setName(vb.bookshelf.getText().toString().strip());
    }
}

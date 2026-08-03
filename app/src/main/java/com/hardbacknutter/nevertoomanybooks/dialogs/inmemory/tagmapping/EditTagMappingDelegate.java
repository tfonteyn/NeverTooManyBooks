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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping;

import android.content.Context;
import android.text.Editable;
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
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditTagMappingContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;

class EditTagMappingDelegate
        implements FlexDialogDelegate {

    private final EditTagMappingViewModel vm;

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    private DialogEditTagMappingContentBinding vb;

    @Nullable
    private Toolbar toolbar;

    /**
     * Constructor.
     * <p>
     * Class output: {@link EditTagMappingOutput}.
     *
     * @param owner hosting Fragment
     * @param args  all arguments
     */
    EditTagMappingDelegate(@NonNull final DialogFragment owner,
                           @NonNull final EditTagMappingInput args) {
        this.owner = owner;
        requestKey = args.getRequestKey();

        vm = new ViewModelProvider(owner).get(EditTagMappingViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditTagMappingContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_tag_mapping, container, false);
        vb = DialogEditTagMappingContentBinding.bind(view.findViewById(R.id.dialog_content));
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

        final TagMapping currentEdit = vm.getCurrentValue();

        vb.tagName.setText(currentEdit.getTagName());
        TilUtil.autoRemoveError(vb.tagName, vb.lblTagName);

        vb.tagMapping.setText(currentEdit.getMappings().stream().sorted()
                                         .collect(Collectors.joining("\n")));
        TilUtil.autoRemoveError(vb.tagMapping, vb.lblTagMapping);

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

        final TagMapping currentEdit = vm.getCurrentValue();
        if (currentEdit.getTagName().isEmpty()) {
            vb.lblTagName.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        new EditTagMappingOutput(vm.getOriginal(), vm.getCurrentValue(),
                                 vm.getExtras())
                .send(owner, requestKey);
        return true;
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        Editable text = vb.tagName.getText();
        final TagMapping currentEdit = vm.getCurrentValue();
        currentEdit.setName(text != null ? text.toString().strip() : "");

        text = vb.tagMapping.getText();
        if (text != null) {
            final String s = text.toString().strip();
            if (!s.isEmpty()) {
                final String[] split = s.split("\n");
                currentEdit.setMappings(Set.of(split));
                return;
            }
        }
        currentEdit.setMappings(Set.of());
    }
}

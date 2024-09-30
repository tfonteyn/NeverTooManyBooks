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

/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectMultipleSimpleBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceBottomSheet;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ChecklistRecyclerAdapter;

/**
 * A variation on {@link MultiChoiceBottomSheet} specifically to deal with preferences.
 */
public class Ext2MultiSelectListPreferenceBottomSheet
        extends BottomSheetDialogFragment {

    private Ext2MultiSelectListPreferenceDelegate delegate;
    private DialogSelectMultipleSimpleBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new Ext2MultiSelectListPreferenceDelegate(this, requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogSelectMultipleSimpleBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Ensure the drag handle is visible.
        vb.dragHandle.setVisibility(View.VISIBLE);

        vb.title.setText(delegate.getDialogTitle());
        delegate.bindMessageView(vb.message);

        //noinspection DataFlowIssue
        final ChecklistRecyclerAdapter<CharSequence> adapter = new ChecklistRecyclerAdapter<>(
                getContext(),
                List.of(delegate.getEntryValues()),
                position -> delegate.getEntries()[position],
                delegate.getNewValues(),
                (value, isChecked) -> delegate.onSelect(value, isChecked));

        vb.itemList.setAdapter(adapter);
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        delegate.saveValue();
        super.onDismiss(dialog);
    }
}

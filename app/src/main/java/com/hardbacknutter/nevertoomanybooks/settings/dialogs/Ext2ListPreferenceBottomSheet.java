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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectSingleSimpleBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

/**
 * The layout is hardcoded to {@link DialogSelectSingleSimpleBinding}
 */
public class Ext2ListPreferenceBottomSheet
        extends BottomSheetDialogFragment {

    private Ext2ListPreferenceDelegate delegate;
    private DialogSelectSingleSimpleBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new Ext2ListPreferenceDelegate(this, requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogSelectSingleSimpleBinding.inflate(inflater, container, false);
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
        final RadioGroupRecyclerAdapter<CharSequence> adapter =
                new RadioGroupRecyclerAdapter<>(getContext(),
                                                List.of(delegate.getEntryValues()),
                                                pos -> delegate.getEntries()[pos],
                                                delegate.getInitialSelection(),
                                                value -> delegate.saveValue(value));

        vb.itemList.setAdapter(adapter);
    }
}

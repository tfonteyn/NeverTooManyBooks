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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.SuperscriptSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.databinding.BottomSheetStylePickerBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

public class StylePickerBottomSheet
        extends BottomSheetDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StylePickerDialogFrag";

    /** Adapter for the selection. */
    private RadioGroupRecyclerAdapter<Style> adapter;

    private StylePickerViewModel vm;
    private SpannableString builtinLabelSuffix;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(StylePickerViewModel.class);
        vm.init(requireArguments());

        builtinLabelSuffix = new SpannableString("*");
        builtinLabelSuffix.setSpan(new SuperscriptSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private BottomSheetStylePickerBinding vb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = BottomSheetStylePickerBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb.dialogToolbar.setNavigationOnClickListener(this::onToolbarNavigationClick);
        // Simple menu items; i.e. non-action view.
        vb.dialogToolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);

        // Hookup any/all buttons in the action-view to use #onToolbarButtonClick
        final MenuItem menuItem = vb.dialogToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
        final View actionView = menuItem.getActionView();
        //noinspection DataFlowIssue
        actionView.setOnClickListener(this::onToolbarButtonClick);

        //noinspection DataFlowIssue
        adapter = new RadioGroupRecyclerAdapter<>(getContext(),
                                                  vm.getStyles(),
                                                  this::getLabel,
                                                  vm.getCurrentStyle(),
                                                  style -> vm.setCurrentStyle(style));
        vb.stylesList.setAdapter(adapter);
    }

    /**
     * Called when the user clicks the Navigation icon from the toolbar menu.
     * The default action simply dismisses the dialog.
     *
     * @param v view
     */
    protected void onToolbarNavigationClick(@NonNull final View v) {
        dismiss();
    }

    @SuppressLint("NotifyDataSetChanged")
    protected boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        if (menuItem == null) {
            return false;
        }

        final int menuItemId = menuItem.getItemId();
        if (menuItemId == R.id.MENU_EDIT) {
            onEditStyle();
            return true;

        } else if (menuItemId == R.id.MENU_STYLE_LIST_TOGGLE) {
            if (vm.flipShowAllStyles()) {
                menuItem.setTitle(R.string.action_less_ellipsis);
                menuItem.setIcon(R.drawable.ic_baseline_unfold_less_24);
            } else {
                menuItem.setTitle(R.string.action_more_ellipsis);
                menuItem.setIcon(R.drawable.ic_baseline_unfold_more_24);
            }

            adapter.notifyDataSetChanged();
            return true;
        }
        return false;
    }

    protected boolean onToolbarButtonClick(@Nullable final View button) {

        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_select || id == R.id.btn_positive) {
                onStyleSelected();
                return true;
            }
        }
        return false;
    }

    /**
     * Send the selected style id back.
     */
    private void onStyleSelected() {
        final Style selectedStyle = adapter.getSelection();
        if (selectedStyle == null) {
            // We should never get here.
            return;
        }
        dismiss();

        StylePickerLauncher.setResult(this, vm.getRequestKey(), selectedStyle);
    }

    /**
     * Edit the selected style.
     */
    private void onEditStyle() {
        final Style selectedStyle = adapter.getSelection();
        if (selectedStyle == null) {
            // We should never get here.
            return;
        }
        dismiss();

        // use the activity so we get the results there.
        //noinspection DataFlowIssue
        ((BooksOnBookshelf) getActivity()).editStyle(selectedStyle);
    }

    @NonNull
    private CharSequence getLabel(final int position) {
        final Style style = vm.getStyles().get(position);
        if (style.isPreferred()) {
            //noinspection DataFlowIssue
            return style.getLabel(getContext());
        } else {
            //TODO: maybe move style '*' suffix logic to the style itself and use universally?
            //noinspection DataFlowIssue
            return getString(R.string.a_b, style.getLabel(getContext()), builtinLabelSuffix);
        }
    }
}

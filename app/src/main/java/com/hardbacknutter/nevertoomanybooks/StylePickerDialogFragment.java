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
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.EnumSet;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogStylesMenuContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

public class StylePickerDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StylePickerDialogFrag";

    /** Adapter for the selection. */
    private RadioGroupRecyclerAdapter<String, String> adapter;

    private StylePickerViewModel vm;

    /**
     * No-arg constructor for OS use.
     */
    public StylePickerDialogFragment() {
        super(R.layout.dialog_styles_menu,
              R.layout.dialog_styles_menu_content,
              // Fullscreen on Medium screens
              // for consistency with BookshelfFiltersDialogFragment
              EnumSet.of(WindowSizeClass.Medium),
              EnumSet.of(WindowSizeClass.Medium));
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(StylePickerViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final DialogStylesMenuContentBinding vb = DialogStylesMenuContentBinding.bind(
                view.findViewById(R.id.dialog_content));

        //noinspection DataFlowIssue
        adapter = new RadioGroupRecyclerAdapter<>(getContext(),
                                                  vm.getStyleUuids(), vm.getStyleLabels(),
                                                  vm.getCurrentStyleUuid(),
                                                  uuid -> vm.setCurrentStyleUuid(uuid));
        vb.stylesList.setAdapter(adapter);

        adjustWindowSize(vb.stylesList, 3);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
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

            //noinspection DataFlowIssue
            vm.loadStyles(getContext());
            adapter.notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
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
        final String selectedStyleUuid = adapter.getSelection();
        if (selectedStyleUuid == null) {
            // We should never get here.
            return;
        }
        dismiss();

        Launcher.setResult(this, vm.getRequestKey(), selectedStyleUuid);
    }

    /**
     * Edit the selected style.
     */
    private void onEditStyle() {
        final String selectedStyleUuid = adapter.getSelection();
        if (selectedStyleUuid == null) {
            // We should never get here.
            return;
        }
        dismiss();

        // use the activity so we get the results there.
        //noinspection DataFlowIssue
        ((BooksOnBookshelf) getActivity()).editStyle(vm.findStyle(selectedStyleUuid));
    }

    public static class Launcher
            extends DialogLauncher {

        private static final String TAG = "Launcher";
        static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";

        @NonNull
        private final ResultListener resultListener;

        /**
         * Constructor.
         *
         * @param requestKey     FragmentResultListener request key to use for our response.
         * @param resultListener listener
         */
        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            super(requestKey, StylePickerDialogFragment::new);
            this.resultListener = resultListener;
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment   the calling DialogFragment
         * @param requestKey to use
         * @param uuid       the selected style
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final String uuid) {
            final Bundle result = new Bundle(1);
            result.putString(DBKey.FK_STYLE, uuid);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param currentStyle the currently active style
         * @param all          if {@code true} show all styles, otherwise only the preferred ones.
         */
        public void launch(@NonNull final Style currentStyle,
                           final boolean all) {

            final Bundle args = new Bundle(3);
            args.putString(Style.BKEY_UUID, currentStyle.getUuid());
            args.putBoolean(BKEY_SHOW_ALL_STYLES, all);

            createDialog(args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(Objects.requireNonNull(result.getString(DBKey.FK_STYLE),
                                                           DBKey.FK_STYLE));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler.
             *
             * @param uuid the selected style
             */
            void onResult(@NonNull String uuid);
        }
    }
}

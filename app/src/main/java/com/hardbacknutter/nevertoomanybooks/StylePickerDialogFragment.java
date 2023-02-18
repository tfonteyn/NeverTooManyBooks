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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogStylesMenuContentBinding;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

public class StylePickerDialogFragment
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "StylePickerDialogFrag";

    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";

    /** The list of styles to display. */
    private final List<String> styleUuids = new ArrayList<>();
    private final List<String> styleLabels = new ArrayList<>();

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** Show all styles, or only the preferred styles. */
    private boolean showAllStyles;
    /** Currently selected style. */
    @Nullable
    private String currentStyleUuid;
    /** All styles as loaded from the database. */
    private List<Style> styleList;
    /** Adapter for the selection. */
    private RadioGroupRecyclerAdapter<String, String> adapter;

    /**
     * No-arg constructor for OS use.
     */
    public StylePickerDialogFragment() {
        super(R.layout.dialog_styles_menu, R.layout.dialog_styles_menu_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                            BKEY_REQUEST_KEY);

        if (savedInstanceState == null) {
            currentStyleUuid = SanityCheck.requireValue(args.getString(Style.BKEY_UUID),
                                                        Style.BKEY_UUID);
            showAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);
        } else {
            currentStyleUuid = savedInstanceState.getString(Style.BKEY_UUID);
            showAllStyles = savedInstanceState.getBoolean(BKEY_SHOW_ALL_STYLES);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final DialogStylesMenuContentBinding vb = DialogStylesMenuContentBinding.bind(
                view.findViewById(R.id.dialog_content));

        loadStyles();

        //noinspection ConstantConditions
        adapter = new RadioGroupRecyclerAdapter<>(getContext(), styleUuids, styleLabels,
                                                  currentStyleUuid,
                                                  uuid -> currentStyleUuid = uuid);
        vb.stylesList.setAdapter(adapter);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Style.BKEY_UUID, currentStyleUuid);
        outState.putBoolean(BKEY_SHOW_ALL_STYLES, showAllStyles);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        if (menuItem == null) {
            return false;
        }

        final int itemId = menuItem.getItemId();
        if (itemId == R.id.MENU_EDIT) {
            onEditStyle();
            return true;

        } else if (itemId == R.id.MENU_STYLE_LIST_TOGGLE) {
            showAllStyles = !showAllStyles;
            if (showAllStyles) {
                menuItem.setTitle(R.string.action_less_ellipsis);
                menuItem.setIcon(R.drawable.ic_baseline_unfold_less_24);
            } else {
                menuItem.setTitle(R.string.action_more_ellipsis);
                menuItem.setIcon(R.drawable.ic_baseline_unfold_more_24);
            }
            loadStyles();
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
     * Send the selected style id back. Silently returns if there was nothing selected.
     */
    private void onStyleSelected() {
        currentStyleUuid = adapter.getSelection();
        if (currentStyleUuid == null) {
            return;
        }

        Launcher.setResult(this, requestKey, currentStyleUuid);

        dismiss();
    }

    /**
     * Edit the selected style. Silently returns if there was nothing selected.
     */
    private void onEditStyle() {
        currentStyleUuid = adapter.getSelection();
        if (currentStyleUuid == null) {
            return;
        }
        dismiss();

        final Style selectedStyle =
                styleList.stream()
                         .filter(style -> currentStyleUuid.equalsIgnoreCase(style.getUuid()))
                         .findFirst()
                         .orElseThrow(IllegalStateException::new);

        // use the activity so we get the results there.
        //noinspection ConstantConditions
        ((BooksOnBookshelf) getActivity()).editStyle(selectedStyle, true);
    }

    /**
     * Fetch the styles.
     */
    private void loadStyles() {
        final Context context = getContext();

        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();

        //noinspection ConstantConditions
        styleList = stylesHelper.getStyles(context, showAllStyles);
        if (!showAllStyles && currentStyleUuid != null) {
            // make sure the currently selected style is in the list
            if (styleList
                    .stream()
                    .noneMatch(style -> currentStyleUuid.equalsIgnoreCase(style.getUuid()))) {

                stylesHelper.getStyle(context, currentStyleUuid)
                            .ifPresent(style -> styleList.add(style));
            }
        }

        styleUuids.clear();
        styleLabels.clear();
        styleList.forEach(style -> {
            styleUuids.add(style.getUuid());
            styleLabels.add(style.getLabel(context));
        });
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private String requestKey;
        private FragmentManager fragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final String uuid) {
            final Bundle result = new Bundle(1);
            result.putString(DBKey.FK_STYLE, uuid);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
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
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(Style.BKEY_UUID, currentStyle.getUuid());
            args.putBoolean(BKEY_SHOW_ALL_STYLES, all);

            final DialogFragment frag = new StylePickerDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getString(DBKey.FK_STYLE),
                                            DBKey.FK_STYLE));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param uuid the selected style
         */
        public abstract void onResult(@NonNull String uuid);

    }
}

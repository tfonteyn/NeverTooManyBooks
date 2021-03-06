/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogStylesMenuBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.RadioGroupRecyclerAdapter;

public class StylePickerDialogFragment
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "StylePickerDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";
    /** The styles get transformed into Pair records which are passed to the adapter. */
    private final List<Pair<String, String>> mAdapterItemList = new ArrayList<>();
    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    /** Show all styles, or only the preferred styles. */
    private boolean mShowAllStyles;
    /** Currently selected style. */
    @Nullable
    private String mCurrentStyleUuid;
    /** All styles as loaded from the database. */
    private List<ListStyle> mStyleList;
    /** Adapter for the selection. */
    private RadioGroupRecyclerAdapter<String, String> mAdapter;

    /**
     * No-arg constructor for OS use.
     */
    public StylePickerDialogFragment() {
        super(R.layout.dialog_styles_menu);
        setFloatingDialogMarginBottom(0);
        setFloatingDialogHeight(R.dimen.floating_dialogs_styles_picker_height);
    }


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mCurrentStyleUuid = Objects.requireNonNull(args.getString(ListStyle.BKEY_STYLE_UUID),
                                                   "BKEY_STYLE_UUID");
        mShowAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final DialogStylesMenuBinding vb = DialogStylesMenuBinding.bind(view);

        loadStyles();

        //noinspection ConstantConditions
        mAdapter = new RadioGroupRecyclerAdapter<>(getContext(),
                                                   mAdapterItemList, mCurrentStyleUuid,
                                                   uuid -> mCurrentStyleUuid = uuid);
        vb.styles.setAdapter(mAdapter);
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_ACTION_CONFIRM) {
            onStyleSelected();
            return true;

        } else if (itemId == R.id.MENU_EDIT) {
            onEditStyle();
            return true;

        } else if (itemId == R.id.MENU_LEVEL_TOGGLE) {
            mShowAllStyles = !mShowAllStyles;
            if (mShowAllStyles) {
                item.setTitle(R.string.btn_less_ellipsis);
                item.setIcon(R.drawable.ic_baseline_unfold_less_24);
            } else {
                item.setTitle(R.string.btn_more_ellipsis);
                item.setIcon(R.drawable.ic_baseline_unfold_more_24);
            }
            loadStyles();
            mAdapter.notifyDataSetChanged();
            return true;
        }

        return false;
    }

    /**
     * Send the selected style id back. Silently returns if there was nothing selected.
     */
    private void onStyleSelected() {
        mCurrentStyleUuid = mAdapter.getSelection();
        if (mCurrentStyleUuid == null) {
            return;
        }

        Launcher.setResult(this, mRequestKey, mCurrentStyleUuid);

        dismiss();
    }

    /**
     * Edit the selected style. Silently returns if there was nothing selected.
     */
    private void onEditStyle() {
        mCurrentStyleUuid = mAdapter.getSelection();
        if (mCurrentStyleUuid == null) {
            return;
        }
        dismiss();

        //noinspection OptionalGetWithoutIsPresent
        final ListStyle selectedStyle =
                mStyleList.stream()
                          .filter(style -> mCurrentStyleUuid.equalsIgnoreCase(style.getUuid()))
                          .findFirst()
                          .get();

        // use the activity so we get the results there.
        //noinspection ConstantConditions
        ((BooksOnBookshelf) getActivity()).editStyle(selectedStyle, true);
    }

    /**
     * Fetch the styles.
     */
    private void loadStyles() {
        final Context context = getContext();

        final Styles styles = ServiceLocator.getInstance().getStyles();

        //noinspection ConstantConditions
        mStyleList = styles.getStyles(context, mShowAllStyles);
        if (!mShowAllStyles && mCurrentStyleUuid != null) {
            // make sure the currently selected style is in the list
            if (mStyleList
                    .stream()
                    .noneMatch(style -> mCurrentStyleUuid.equalsIgnoreCase(style.getUuid()))) {

                final ListStyle style = styles.getStyle(context, mCurrentStyleUuid);
                if (style != null) {
                    mStyleList.add(style);
                }
            }
        }

        mAdapterItemList.clear();
        for (final ListStyle style : mStyleList) {
            mAdapterItemList.add(new Pair<>(style.getUuid(), style.getLabel(context)));
        }
    }

    public abstract static class Launcher
            extends FragmentLauncherBase {

        public Launcher(@NonNull final String requestKey) {
            super(requestKey);
        }

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
        public void launch(@NonNull final ListStyle currentStyle,
                           final boolean all) {

            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(ListStyle.BKEY_STYLE_UUID, currentStyle.getUuid());
            args.putBoolean(BKEY_SHOW_ALL_STYLES, all);

            final DialogFragment frag = new StylePickerDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getString(DBKey.FK_STYLE)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param uuid the selected style
         */
        public abstract void onResult(@NonNull String uuid);
    }
}

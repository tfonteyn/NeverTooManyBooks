/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogStylesMenuBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleBaseFragment;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.RadioGroupRecyclerAdapter;

public class StylePickerDialogFragment
        extends BaseDialogFragment {

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
    /** The map with all styles as loaded from the database. */
    private Map<String, BooklistStyle> mBooklistStyles;
    private RadioGroupRecyclerAdapter<String, String> mAdapter;
    /** Currently selected style. */
    @Nullable
    private String mCurrentStyleUuid;

    /**
     * No-arg constructor for OS use.
     */
    public StylePickerDialogFragment() {
        super(R.layout.dialog_styles_menu);
    }

    /**
     * Constructor.
     *
     * @param requestKey   for use with the FragmentResultListener
     * @param currentStyle the currently active style
     * @param all          if {@code true} show all styles, otherwise only the preferred ones.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey,
                                             @NonNull final BooklistStyle currentStyle,
                                             final boolean all) {
        final DialogFragment frag = new StylePickerDialogFragment();
        final Bundle args = new Bundle(3);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putString(BooklistStyle.BKEY_STYLE_UUID, currentStyle.getUuid());
        args.putBoolean(BKEY_SHOW_ALL_STYLES, all);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = args.getString(BKEY_REQUEST_KEY);
        mCurrentStyleUuid = Objects.requireNonNull(args.getString(BooklistStyle.BKEY_STYLE_UUID),
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
        switch (item.getItemId()) {
            case R.id.MENU_ACTION_CONFIRM:
                onStyleSelected();
                return true;
            case R.id.MENU_EDIT:
                onEditStyle();
                return true;
            case R.id.MENU_LEVEL_TOGGLE:
                mShowAllStyles = !mShowAllStyles;
                if (mShowAllStyles) {
                    item.setTitle(R.string.btn_less_ellipsis);
                    item.setIcon(R.drawable.ic_unfold_less);
                } else {
                    item.setTitle(R.string.btn_more_ellipsis);
                    item.setIcon(R.drawable.ic_unfold_more);
                }
                loadStyles();
                mAdapter.notifyDataSetChanged();
                return true;

            default:
                return false;
        }
    }

    /**
     * Send the selected style id back. Silently returns if there was nothing selected.
     */
    private void onStyleSelected() {
        mCurrentStyleUuid = mAdapter.getSelection();
        if (mCurrentStyleUuid == null) {
            return;
        }

        OnResultListener.sendResult(this, mRequestKey, mCurrentStyleUuid);

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

        BooklistStyle style = mBooklistStyles.get(mCurrentStyleUuid);
        //noinspection ConstantConditions
        final long templateId = style.getId();
        if (!style.isUserDefined()) {
            // clone a builtin style first
            //noinspection ConstantConditions
            style = style.clone(getContext());
        }

        // use the activity so we get the results there.
        final Activity activity = getActivity();
        final Intent intent = new Intent(activity, SettingsActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, StyleFragment.TAG)
                .putExtra(BooklistStyle.BKEY_STYLE, style)
                .putExtra(StyleBaseFragment.BKEY_TEMPLATE_ID, templateId);
        //noinspection ConstantConditions
        activity.startActivityForResult(intent, RequestCode.EDIT_STYLE);
    }

    /**
     * Fetch the styles.
     */
    private void loadStyles() {
        final Context context = getContext();

        try (DAO db = new DAO(TAG)) {
            //noinspection ConstantConditions
            mBooklistStyles = BooklistStyle.getStyles(context, db, mShowAllStyles);
        }

        mAdapterItemList.clear();
        for (BooklistStyle style : mBooklistStyles.values()) {
            mAdapterItemList.add(new Pair<>(style.getUuid(), style.getLabel(context)));
        }
    }

    public interface OnResultListener
            extends FragmentResultListener {

        /* private. */ String UUID = "uuid";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @NonNull final String uuid) {
            final Bundle result = new Bundle(1);
            result.putString(UUID, uuid);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getString(UUID)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param uuid the selected style
         */
        void onResult(@NonNull String uuid);
    }
}

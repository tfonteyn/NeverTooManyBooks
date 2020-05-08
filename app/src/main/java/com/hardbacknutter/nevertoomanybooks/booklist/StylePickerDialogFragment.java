/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleBaseFragment;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.RadioGroupRecyclerAdapter;

public class StylePickerDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "StylePickerDialogFrag";

    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";
    private final ArrayList<BooklistStyle> mBooklistStyles = new ArrayList<>();
    private boolean mShowAllStyles;
    private RadioGroupRecyclerAdapter<BooklistStyle> mAdapter;
    /** Currently selected style. */
    @Nullable
    private BooklistStyle mCurrentStyle;

    /** Where to send the result. */
    @Nullable
    private WeakReference<StyleChangedListener> mListener;

    /**
     * Constructor.
     *
     * @param currentStyle the currently active style
     * @param all          if {@code true} show all styles, otherwise only the preferred ones.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final BooklistStyle currentStyle,
                                             final boolean all) {
        final DialogFragment frag = new StylePickerDialogFragment();
        final Bundle args = new Bundle(2);
        args.putParcelable(BooklistStyle.BKEY_STYLE, currentStyle);
        args.putBoolean(BKEY_SHOW_ALL_STYLES, all);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final StyleChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mCurrentStyle = args.getParcelable(BooklistStyle.BKEY_STYLE);
        Objects.requireNonNull(mCurrentStyle, ErrorMsg.ARGS_MISSING_STYLE);
        mShowAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);

        loadStyles();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View root = getLayoutInflater().inflate(R.layout.dialog_styles_menu, null);

        final RecyclerView listView = root.findViewById(R.id.styles);
        listView.setHasFixedSize(true);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        listView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mAdapter = new RadioGroupRecyclerAdapter<>(getContext(),
                                                   mBooklistStyles, mCurrentStyle,
                                                   style -> mCurrentStyle = style);
        listView.setAdapter(mAdapter);

        return new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_select_style)
                .setView(root)
                // We set the OnClickListener in onResume.
                // This allows reloading the list without having the dialog close
                // on us after the user clicks a button.
                .setNeutralButton(getMoreOrLessBtnTxtId(), null)
                .setNegativeButton(R.string.action_edit, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(Dialog.BUTTON_NEUTRAL)
                  .setOnClickListener(v -> {
                      mShowAllStyles = !mShowAllStyles;
                      ((AlertDialog) getDialog()).getButton(Dialog.BUTTON_NEUTRAL)
                                                 .setText(getMoreOrLessBtnTxtId());
                      loadStyles();
                      mAdapter.notifyDataSetChanged();
                  });
            dialog.getButton(Dialog.BUTTON_NEGATIVE)
                  .setOnClickListener(v -> onEditStyle());
            dialog.getButton(Dialog.BUTTON_POSITIVE)
                  .setOnClickListener(v -> onStyleSelected());
        }
    }

    /**
     * Send the selected style back. Silently returns if there was nothing selected.
     */
    private void onStyleSelected() {
        mCurrentStyle = mAdapter.getSelectedItem();
        if (mCurrentStyle == null) {
            return;
        }
        dismiss();

        if (mListener != null && mListener.get() != null) {
            mListener.get().onStyleChanged(mCurrentStyle);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onStyleSelected|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    /**
     * Edit the selected style. Silently returns if there was nothing selected.
     */
    private void onEditStyle() {
        mCurrentStyle = mAdapter.getSelectedItem();
        if (mCurrentStyle == null) {
            return;
        }
        dismiss();

        // use the activity so we get the results there.
        final Activity activity = getActivity();
        final Intent intent = new Intent(activity, SettingsActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, StyleFragment.TAG);

        if (mCurrentStyle.isUserDefined()) {
            intent.putExtra(BooklistStyle.BKEY_STYLE, mCurrentStyle);
        } else {
            // clone builtin style first
            //noinspection ConstantConditions
            intent.putExtra(BooklistStyle.BKEY_STYLE, mCurrentStyle.clone(getContext()));
        }

        intent.putExtra(StyleBaseFragment.BKEY_TEMPLATE_ID, mCurrentStyle.getId());
        //noinspection ConstantConditions
        activity.startActivityForResult(intent, RequestCode.EDIT_STYLE);
    }

    @StringRes
    private int getMoreOrLessBtnTxtId() {
        return mShowAllStyles ? R.string.btn_less_ellipsis
                              : R.string.btn_more_ellipsis;
    }

    /**
     * Fetch the styles from the database.
     */
    private void loadStyles() {
        try (DAO db = new DAO(TAG)) {
            mBooklistStyles.clear();
            //noinspection ConstantConditions
            mBooklistStyles.addAll(BooklistStyle.Helper.getStyles(getContext(), db,
                                                                  mShowAllStyles).values());
        }
    }

    public interface StyleChangedListener {

        void onStyleChanged(@NonNull BooklistStyle style);
    }
}

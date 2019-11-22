/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.widgets.AltIsbnTextWatcher;
import com.hardbacknutter.nevertoomanybooks.widgets.EditIsbn;
import com.hardbacknutter.nevertoomanybooks.widgets.IsbnValidationTextWatcher;

/**
 * <strong>Notes on the virtual keyboard:</strong>
 * <p>
 * Stop if from showing when a field gets the focus.<br>
 * This must be done for <strong>ALL</strong> fields individually
 * <pre>
 * {@code
 *      editText.setShowSoftInputOnFocus(false);
 * }
 * </pre>
 * Hide it when already showing:
 * <pre>
 * {@code
 *      InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
 *      if (imm != null && imm.isActive(this)) {
 *          imm.hideSoftInputFromWindow(getWindowToken(), 0);
 *      }
 * }
 * </pre>
 */
public class BookSearchByIsbnFragment
        extends BookSearchByIsbnBaseFragment {

    public static final String TAG = "BookSearchByIsbnFragment";

    /** User input field. */
    @Nullable
    private EditIsbn mIsbnView;

    @Nullable
    private TextView mAltIsbnView;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_booksearch_by_isbn, container, false);
        mIsbnView = view.findViewById(R.id.isbn);
        mAltIsbnView = view.findViewById(R.id.altIsbn);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = getView();

        // stop lint being very annoying...
        Objects.requireNonNull(mIsbnView);
        Objects.requireNonNull(mAltIsbnView);

        mIsbnView.setText(mSearchCoordinator.getIsbnSearchText());

        //noinspection ConstantConditions
        view.findViewById(R.id.key_0).setOnClickListener(v -> mIsbnView.onKey("0"));
        view.findViewById(R.id.key_1).setOnClickListener(v -> mIsbnView.onKey("1"));
        view.findViewById(R.id.key_2).setOnClickListener(v -> mIsbnView.onKey("2"));
        view.findViewById(R.id.key_3).setOnClickListener(v -> mIsbnView.onKey("3"));
        view.findViewById(R.id.key_4).setOnClickListener(v -> mIsbnView.onKey("4"));
        view.findViewById(R.id.key_5).setOnClickListener(v -> mIsbnView.onKey("5"));
        view.findViewById(R.id.key_6).setOnClickListener(v -> mIsbnView.onKey("6"));
        view.findViewById(R.id.key_7).setOnClickListener(v -> mIsbnView.onKey("7"));
        view.findViewById(R.id.key_8).setOnClickListener(v -> mIsbnView.onKey("8"));
        view.findViewById(R.id.key_9).setOnClickListener(v -> mIsbnView.onKey("9"));
        view.findViewById(R.id.key_X).setOnClickListener(v -> mIsbnView.onKey("X"));

        Button delBtn = view.findViewById(R.id.isbn_del);
        delBtn.setOnClickListener(v -> mIsbnView.onKey(KeyEvent.KEYCODE_DEL));
        delBtn.setOnLongClickListener(v -> {
            mIsbnView.setText("");
            mAltIsbnView.setText("");
            return true;
        });

        mIsbnView.addTextChangedListener(new IsbnValidationTextWatcher(mIsbnView));
        mIsbnView.addTextChangedListener(new AltIsbnTextWatcher(mIsbnView, mAltIsbnView));

        view.findViewById(R.id.btn_search).setOnClickListener(v -> {
            //noinspection ConstantConditions
            prepareSearch(mIsbnView.getText().toString().trim());
        });

        // init the isbn edit field if needed (avoid initializing twice)
        if (mAllowAsin) {
            mIsbnView.setAllowAsin(true);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        //dev note: we could eliminate onPrepareOptionsMenu as invalidateOptionsMenu()
        // MUST be called to make this menu be show for as long there is only this one
        // option in the menu. But... leaving the code as-is, so if/when a second menu
        // item is added, no code changes are needed.
        menu.add(Menu.NONE, R.id.MENU_PREFS_ASIN, 0, R.string.lbl_allow_asin)
            .setCheckable(true)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        // if Amazon is enabled, we show the ASIN option; else make sure it's disabled.
        boolean amazon = mSearchCoordinator.isEnabled(SearchSites.AMAZON);
        MenuItem asin = menu.findItem(R.id.MENU_PREFS_ASIN);
        asin.setVisible(amazon);
        if (!amazon) {
            asin.setChecked(false);
        }

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_ASIN:
                mAllowAsin = !item.isChecked();

                item.setChecked(mAllowAsin);
                //noinspection ConstantConditions
                mIsbnView.setAllowAsin(mAllowAsin);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        mSearchCoordinator.setIsbnSearchText(mIsbnView.getText().toString().trim());
    }

    @Override
    void clearPreviousSearchCriteria() {
        super.clearPreviousSearchCriteria();
        //noinspection ConstantConditions
        mIsbnView.setText("");
    }
}

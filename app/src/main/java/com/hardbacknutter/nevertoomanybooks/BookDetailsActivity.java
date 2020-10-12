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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

/**
 * Hosting activity for showing a book.
 */
public class BookDetailsActivity
        extends BaseActivity {

    /** all registered listeners. */
    private final Collection<View.OnTouchListener> mOnTouchListeners = new ArrayList<>();

    /** The book. Must be in the Activity scope. */
    private BookViewModel mBookViewModel;

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_book_details);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBookViewModel = new ViewModelProvider(this).get(BookViewModel.class);
        mBookViewModel.init(this, getIntent().getExtras(), false);

        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);

        replaceFragment(R.id.main_fragment, BookDetailsFragment.class, BookDetailsFragment.TAG);

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_OK, mBookViewModel.getResultIntent());
        super.onBackPressed();
    }

    public void registerOnTouchListener(@NonNull final View.OnTouchListener listener) {
        synchronized (mOnTouchListeners) {
            mOnTouchListeners.add(listener);
        }
    }

    public void unregisterOnTouchListener(@NonNull final View.OnTouchListener listener) {
        synchronized (mOnTouchListeners) {
            mOnTouchListeners.remove(listener);
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull final MotionEvent ev) {
        synchronized (mOnTouchListeners) {
            for (final View.OnTouchListener listener : mOnTouchListeners) {
                listener.onTouch(null, ev);
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}

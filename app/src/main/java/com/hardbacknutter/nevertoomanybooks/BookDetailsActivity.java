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

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Hosting activity for showing a book <strong>with</strong>
 * a DrawerLayout/NavigationView side panel.
 */
public class BookDetailsActivity
        extends BaseActivity {

    /** all registered listeners. */
    private final Collection<View.OnTouchListener> mOnTouchListeners = new ArrayList<>();

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_book_details);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addFirstFragment(R.id.main_fragment, BookDetailsFragment.class, BookDetailsFragment.TAG);
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

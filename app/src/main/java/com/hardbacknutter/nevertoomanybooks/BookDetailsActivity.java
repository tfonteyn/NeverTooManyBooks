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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * Hosting activity for showing a book.
 */
public class BookDetailsActivity
        extends BaseActivity {

    /** all registered listeners. */
    private final List<View.OnTouchListener> mOnTouchListeners = new ArrayList<>();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        replaceFragment(R.id.main_fragment, BookDetailsFragment.class, BookDetailsFragment.TAG);
    }

    /**
     * Set the current visible book id as the Activity result data.
     */
    @Override
    public void onBackPressed() {
        ActivityResultDataModel model = new ViewModelProvider(this)
                .get(BookBaseFragmentModel.class);
        Intent resultData = model.getActivityResultData();
        setResult(Activity.RESULT_OK, resultData);
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
            for (View.OnTouchListener listener : mOnTouchListeners) {
                listener.onTouch(null, ev);
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}

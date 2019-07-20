/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;

/**
 * Hosting activity for showing a book.
 */
public class BookDetailsActivity
        extends BaseActivity {

    private List<View.OnTouchListener> mOnTouchListeners = new ArrayList<>();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(BookFragment.TAG) == null) {
            Fragment frag = new BookFragment();
            frag.setArguments(getIntent().getExtras());
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .replace(R.id.main_fragment, frag, BookFragment.TAG)
              .commit();
        }
    }
    ;

    public void registerOnTouchListener(@NonNull final View.OnTouchListener listener) {
        mOnTouchListeners.add(listener);
    }

    public void unregisterOnTouchListener(@NonNull final View.OnTouchListener listener) {
        mOnTouchListeners.remove(listener);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull final MotionEvent ev) {
        for (View.OnTouchListener listener : mOnTouchListeners) {
            listener.onTouch(null, ev);
        }
        return super.dispatchTouchEvent(ev);
    }
}

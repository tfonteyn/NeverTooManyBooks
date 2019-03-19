/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.adapters;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.widgets.FastScroller;

/**
 * Cursor adapter for flattened multi-typed ListViews.
 * Simplifies the implementation of such lists.
 * <p>
 * Users of this class need to implement MultiTypeListHandler to manage the creation
 * and display of each view.
 *
 * @author Philip Warner
 */
public class MultiTypeListCursorAdapter
        extends CursorAdapter
        implements FastScroller.SectionIndexerV2 {

    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final MultiTypeListHandler mListHandler;

    public MultiTypeListCursorAdapter(@NonNull final Activity activity,
                                      @NonNull final Cursor cursor,
                                      @NonNull final MultiTypeListHandler handler) {
        super(activity, cursor);
        mInflater = LayoutInflater.from(activity);
        mListHandler = handler;
    }

    /**
     * NOT USED. Should never be called. Die if it is.
     */
    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final Cursor cursor) {
        throw new UnsupportedOperationException();
    }

    /**
     * NOT USED. Should never be called. Die if it is.
     */
    @NonNull
    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getItemViewType(final int position) {
        final Cursor listCursor = getCursor();
        //
        // At least on Android 2.3.4 we see attempts to get item types for cached items beyond the
        // end of empty cursors. This implies a cleanup ordering issue, but has not been confirmed.
        // This code attempts to gather more details of how this error occurs.
        //
        // NOTE: It DOES NOT fix the error; just gathers more debug info
        //
        if (listCursor.isClosed()) {
            throw new IllegalStateException(
                    "Attempt to get type of item on closed cursor (" + listCursor + ')');
        } else if (position >= listCursor.getCount()) {
            throw new IllegalStateException(
                    "Attempt to get type of item beyond end of cursor (" + listCursor + ')');
        } else {
            listCursor.moveToPosition(position);
            return mListHandler.getItemViewType(listCursor);
        }
    }

    @Override
    public int getViewTypeCount() {
        return mListHandler.getViewTypeCount();
    }

    @NonNull
    @Override
    public View getView(final int position,
                        final View convertView,
                        @NonNull final ViewGroup parent) {
        Cursor listCursor = getCursor();
        listCursor.moveToPosition(position);

        return mListHandler.getView(listCursor, mInflater, convertView, parent);
    }

    /**
     * this method gets called by {@link FastScroller}.
     * <p>
     * actual text coming from {@link MultiTypeListHandler#getSectionText(Cursor)}}.
     */
    @Override
    @Nullable
    public String[] getSectionTextForPosition(final int position) {
        //Tracker.info(this, Tracker.State.Enter, "getSectionTextForPosition", position);
        final Cursor listCursor = getCursor();
        if (position < 0 || position >= listCursor.getCount()) {
            return null;
        }

        final int savedPos = listCursor.getPosition();
        listCursor.moveToPosition(position);
        final String[] section = mListHandler.getSectionText(listCursor);
        listCursor.moveToPosition(savedPos);

        if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG) {
            for (String s : section) {
                Logger.info(this, "getSectionTextForPosition","Section: " + s);
            }
        }
        //Tracker.info(this, Tracker.State.Exit, "getSectionTextForPosition");
        return section;
    }
}

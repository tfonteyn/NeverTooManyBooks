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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;

/**
 * Used by the set of fragments that allow viewing and editing a Book.
 * <ul>
 *     <li>Hold the field lists</li>
 *     <li>Keep track of the currently in-action CoverHandler</li>
 * </ul>
 */
public abstract class BookBaseFragmentViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BookBaseFragmentVM";

    /**
     * The fields collection handled in this model. The key is the fragment tag.
     * We're not actually using this as a map, as we're only ever storing one
     * set of fields. The Map setup is a left-over from a previous approach and might
     * make a comeback. So... keeping it for now.
     */
    private final Map<String, Fields> mFieldsMap = new HashMap<>();
    /** Database Access. */
    protected DAO mDb;

    /** <strong>Optionally</strong> passed in via the arguments. */
    @Nullable
    private BooklistStyle mStyle;

    /** Track on which cover view the context menu was used. */
    @IntRange(from = -1)
    private int mCurrentCoverHandlerIndex = -1;

    @Override
    @CallSuper
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    @CallSuper
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            if (args != null) {
                final String styleUuid = args.getString(BooklistStyle.BKEY_STYLE_UUID);
                if (styleUuid != null) {
                    mStyle = BooklistStyle.getStyleOrDefault(context, mDb, styleUuid);
                }
            }
        }
    }

    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>The fragment has no access to the style -> return the global visibility</li>
     *     <li>The global style is set to HIDE -> {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param context     current context
     * @param preferences Global preferences
     * @param cIdx        0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isCoverUsed(@NonNull final Context context,
                               @NonNull final SharedPreferences preferences,
                               @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBDefinitions.isCoverUsed(preferences, cIdx)) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields().isShowCover(context,
                                                                  preferences, cIdx);
        }
    }

    @NonNull
    public Fields getFields(@Nullable final String key) {
        Fields fields;
        synchronized (mFieldsMap) {
            fields = mFieldsMap.get(key);
            if (fields == null) {
                fields = new Fields();
                mFieldsMap.put(key, fields);
            }
        }
        return fields;
    }

    @NonNull
    public List<Bookshelf> getAllBookshelves() {
        // not cached.
        // This allows the user to edit the global list of shelves while editing a book.
        return mDb.getBookshelves();
    }

    /**
     * Retrieve the previously set cover handler index.
     * This is destructive: the value will be reset to -1 immediately.
     *
     * @return the index; will be {@code -1} if none was set.
     */
    @IntRange(from = -1, to = 1)
    public int getAndClearCurrentCoverHandlerIndex() {
        final int current = mCurrentCoverHandlerIndex;
        mCurrentCoverHandlerIndex = -1;

        if (BuildConfig.DEBUG /* always */) {
            if (current == -1) {
                throw new IllegalStateException("getAndClearCurrentCoverHandlerIndex"
                                                + " would return -1");
            }
        }
        return current;
    }

    /**
     * Set the current cover handler index.
     * Call this before starting the camera and similar actions.
     *
     * @param cIdx 0..n image index
     */
    public void setCurrentCoverHandlerIndex(@IntRange(from = 0, to = 1) final int cIdx) {
        mCurrentCoverHandlerIndex = cIdx;
    }
}

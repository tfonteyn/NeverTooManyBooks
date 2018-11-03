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

package com.eleybourn.bookcatalogue.utils;

import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;
import android.view.View;

import java.util.Objects;

/**
 * Using View.setTag(int, Object) causes a memory leak if the tag refers, by a strong reference
 * chain, to the view itself (ie. it uses the 'Holder' pattern).
 * This bug is documented here:
 *
 * http://code.google.com/p/android/issues/detail?id=18273
 *
 * TODO: above bug was fixed in Android 4
 *
 * It seems that an 'interesting' design choice was made to use the view itself as a weak key
 * to the into another collection, which then causes the views to never be GC'd.
 *
 * The work-around is to *not* use strong refs, or use setTag(Object).
 * But we use multiple tags.
 *
 * So this class implements setTag(int, Object) in a non-leaky fashion and is designed
 * to be stored in the tag of a view.
 *
 * @author Philip Warner
 */
public class ViewTagger {
    /** Stores the basic tag referred to without an ID */
    @Nullable
    private Object mBareTag = null;
    @Nullable
    private SparseArray<Object> mTags = null;

    private ViewTagger() {
    }

    /**
     * Internal static method to get (and optionally create) a ViewTagger object
     * on the passed view.
     *
     * @param view       View with tag
     * @param autoCreate Indicates if tagger should be created if not present
     *
     * @return ViewTagger object
     */
    @Nullable
    private static ViewTagger getTagger(final @NonNull View view, final boolean autoCreate) {
        // See if we have one already
        Object tag = view.getTag();
        if (tag == null) {
            ViewTagger tagger = null;
            // Create if requested
            if (autoCreate) {
                tagger = new ViewTagger();
                view.setTag(tagger);
            }
            return tagger;
        } else {
            // Make sure it's a valid object type
            if (!(tag instanceof ViewTagger)) {
                throw new IllegalStateException("View already has a tag that is not a ViewTagger");
            }
            return (ViewTagger) tag;
        }
    }

    /**
     * Static method to get the bare tag from the view.
     * Use this method if you the tag *could* be there
     *
     * @see #getTagOrThrow(View)
     *
     * @param view View from which to retrieve tag
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getTag(final @NonNull View view) {
        ViewTagger tagger = getTagger(view, false);
        if (tagger == null) {
            return null;
        }
        return (T) tagger.get();
    }

    /**
     * Static method to get the bare tag from the view.
     * Use this method if you the tag *should* be there
     *
     * @see #getTag(View)
     *
     * @param view View from which to retrieve tag
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> T getTagOrThrow(final @NonNull View view) {
        return (T) Objects.requireNonNull(getTag(view), "tag  was null");

    }

    /**
     * Static method to get the tag matching the ID from the view
     * Use this method if you the tag *could* be there
     *
     * @param view View from which to retrieve tag
     * @param key  Key of required tag
     *
     * @return Object with specified tag, or null if not found
     *
     * @throws NullPointerException if the tagger was null
     *
     * @see #getTagOrThrow(View, int)
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getTag(final @NonNull View view, final @IdRes int key) {
        ViewTagger tagger = getTagger(view, false);
        Objects.requireNonNull(tagger, "view has no tagger");
        return (T) tagger.get(key);
    }

    /**
     * Static method to get the tag matching the ID from the view.
     * Use this method if you the tag *should* be there
     *
     * @param view View from which to retrieve tag
     * @param key  Key of required tag
     *
     * @return Object with specified tag, never null
     *
     * @throws NullPointerException if the tag was null
     *
     * @see #getTag(View, int)
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> T getTagOrThrow(final @NonNull View view, final @IdRes int key) {
        return (T) Objects.requireNonNull(getTag(view, key), "tag " + key + " was null");
    }

    /**
     * Static method to set the bare tag on the view
     *
     * @param view  View from which to retrieve tag
     * @param value Object to store at specified tag
     */
    public static void setTag(final @NonNull View view, final @Nullable Object value) {
        //noinspection ConstantConditions
        getTagger(view, true).set(value);
    }

    /**
     * Static method to set the tag matching the ID on the view
     *
     * @param view  View from which to retrieve tag
     * @param key   Key of tag to store
     * @param value Object to store at specified tag
     */
    public static void setTag(final @NonNull View view, final @IdRes int key, final @Nullable Object value) {
        //noinspection ConstantConditions
        getTagger(view, true).set(key, value);
    }

    /**
     * Instance method to set the bare tag
     *
     * @param value Value of id-less tag
     */
    public void set(final @Nullable Object value) {
        mBareTag = value;
    }

    /**
     * Instance method to set the specified tag value
     *
     * @param key   Key of new tag
     * @param value Object to store at specified tag
     */
    public void set(final @IdRes int key, final @Nullable Object value) {
        synchronized (this) {
            if (mTags == null) {
                mTags = new SparseArray<>();
            }
            mTags.put(key, value);
        }
    }

    /**
     * Instance method to get the bare tag
     *
     * @return The bare tag object
     */
    @Nullable
    public Object get() {
        return mBareTag;
    }

    /**
     * Instance method to get the specified tag
     *
     * @param key Key of object to retrieve
     *
     * @return Object at specified key
     */
    @Nullable
    public Object get(final int key) {
        synchronized (this) {
            if (mTags == null) {
                return null;
            }
            return mTags.get(key);
        }
    }
}

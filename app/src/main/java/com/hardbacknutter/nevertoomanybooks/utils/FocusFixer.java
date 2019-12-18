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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

/**
 * Ensure that next up/down/left/right View is visible.
 * Sets the nextFocusX attributes on the visible fields.
 */
public final class FocusFixer {

    private static final String TAG = "FocusFixer";

    private FocusFixer() {
    }

    /**
     * Ensure that next up/down/left/right View is visible for all sub-views of the passed view.
     * Sets the nextFocusX attributes on the visible fields.
     *
     * @param rootView to fix
     */
    public static void fix(@NonNull final View rootView) {
        try {
            final INextView getDown = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusDownId();
                }

                @Override
                public void setNext(@NonNull final View v,
                                    @IdRes final int id) {
                    v.setNextFocusDownId(id);
                }
            };
            final INextView getUp = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusUpId();
                }

                @Override
                public void setNext(@NonNull final View v,
                                    @IdRes final int id) {
                    v.setNextFocusUpId(id);
                }
            };
            final INextView getLeft = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusLeftId();
                }

                @Override
                public void setNext(@NonNull final View v,
                                    @IdRes final int id) {
                    v.setNextFocusLeftId(id);
                }
            };
            final INextView getRight = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusRightId();
                }

                @Override
                public void setNext(@NonNull final View v,
                                    @IdRes final int id) {
                    v.setNextFocusRightId(id);
                }
            };

            @SuppressLint("UseSparseArrays")
            Map<Integer, View> vh = new HashMap<>();
            getViews(rootView, vh);

            for (View v : vh.values()) {
                if (v.getVisibility() == View.VISIBLE) {
                    fixNextView(vh, v, getDown);
                    fixNextView(vh, v, getUp);
                    fixNextView(vh, v, getLeft);
                    fixNextView(vh, v, getRight);
                }
            }
        } catch (@NonNull final RuntimeException e) {
            if (BuildConfig.DEBUG /* always */) {
                // Log, but ignore. This is a non-critical feature that prevents crashes
                // when the 'next' key is pressed and some views have been hidden.
                Log.d(TAG, "rootView=" + rootView, e);
            }
        }
    }

    /**
     * Passed a collection of views, a specific View and an INextView, ensure that the
     * currently set 'next' view is actually a visible view, updating it if necessary.
     *
     * @param list   Collection of all views
     * @param view   View to check
     * @param getter Methods to get/set 'next' view
     */
    private static void fixNextView(@NonNull final Map<Integer, View> list,
                                    @NonNull final View view,
                                    @NonNull final INextView getter) {
        int nextId = getter.getNext(view);
        if (nextId != View.NO_ID) {
            int actualNextId = getNextView(list, nextId, getter);
            if (actualNextId != nextId) {
                getter.setNext(view, actualNextId);
            }
        }
    }

    /**
     * Passed a collection of views, a specific view and an INextView object find the
     * first VISIBLE object returned by INextView when called recursively.
     *
     * @param list   Collection of all views
     * @param nextId id of 'next' view to get
     * @param getter Interface to lookup 'next' id given a view
     *
     * @return id if first visible 'next' view
     */
    private static int getNextView(@NonNull final Map<Integer, View> list,
                                   final int nextId,
                                   @NonNull final INextView getter) {
        final View v = list.get(nextId);
        if (v == null) {
            return View.NO_ID;
        }

        if (v.getVisibility() == View.VISIBLE) {
            return nextId;
        }

        return getNextView(list, getter.getNext(v), getter);
    }

    /**
     * Passed a parent view, add it and all children view (if any) to the passed collection.
     *
     * @param parent Parent View
     * @param list   Collection
     */
    private static void getViews(@NonNull final View parent,
                                 @NonNull final Map<Integer, View> list) {
        // Get the view id and add it to collection if not already present.
        @IdRes
        final int id = parent.getId();
        if (id != View.NO_ID && !list.containsKey(id)) {
            list.put(id, parent);
        }
        // If it's a ViewGroup, then process children recursively.
        if (parent instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) parent;
            final int nChildren = g.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                getViews(g.getChildAt(i), list);
            }
        }
    }

    /**
     * Dump an entire view hierarchy to the output.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    static void debugDumpViewTree(final int depth,
                                  @NonNull final View view) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth * 4; i++) {
            sb.append(' ');
        }
        sb.append(view.getClass().getCanonicalName())
          .append(" (").append(view.getId()).append("') ->");

        if (view instanceof TextView) {
            String value = ((TextView) view).getText().toString().trim();
            value = value.substring(0, Math.min(value.length(), 20));
            sb.append(value);
        } else {
            Log.d(TAG, "debugDumpViewTree|" + sb);
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                debugDumpViewTree(depth + 1, g.getChildAt(i));
            }
        }
    }

    private interface INextView {

        int getNext(@NonNull View v);

        void setNext(@NonNull View v,
                     @IdRes int id);
    }
}

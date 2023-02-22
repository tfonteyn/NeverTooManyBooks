/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintHelper;

import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.BaseProgressIndicator;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;

/**
 * Set/fix the nextFocusX attributes on the visible fields.
 */
public final class ViewFocusOrder {

    /** Log tag. */
    private static final String TAG = "ViewFocusOrder";

    private ViewFocusOrder() {
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
            final INextView getForward = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusForwardId();
                }

                @Override
                public void setNext(@NonNull final View v,
                                    @IdRes final int id) {
                    v.setNextFocusForwardId(id);
                }
            };

            final SparseArray<View> vh = new SparseArray<>();
            getViews(rootView, vh);

            for (int i = 0; i < vh.size(); i++) {
                final View v = vh.valueAt(i);
                if (v.getVisibility() == View.VISIBLE) {
                    fixNextView(vh, v, getForward);
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
                LoggerFactory.getLogger().d(TAG, "rootView=" + rootView, e);
            }
        }

        //debugDumpViewTree(rootView, 0, true);
    }

    /**
     * Passed a collection of views, a specific View and an INextView, ensure that the
     * currently set 'next' view is actually a visible view, updating it if necessary.
     *
     * @param list   Collection of all views
     * @param view   View to check
     * @param getter Methods to get/set 'next' view
     */
    private static void fixNextView(@NonNull final SparseArray<View> list,
                                    @NonNull final View view,
                                    @NonNull final INextView getter) {
        @IdRes
        final int nextId = getter.getNext(view);
        if (nextId != View.NO_ID) {
            @IdRes
            final int actualNextId = getNextView(list, nextId, getter);
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
    private static int getNextView(@NonNull final SparseArray<View> list,
                                   @IdRes final int nextId,
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
     * @param view Parent View
     * @param list Collection
     */
    private static void getViews(@NonNull final View view,
                                 @NonNull final SparseArray<View> list) {
        if (view instanceof ConstraintHelper
            || view instanceof BaseProgressIndicator) {
            return;
        }

        // Get the view id and add it to collection if not already present.
        @IdRes
        final int id = view.getId();
        if (id != View.NO_ID && list.get(id) == null) {
            list.put(id, view);
        }
        // If it's a ViewGroup, then process children recursively.
        if (view instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) view;
            final int nChildren = g.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                getViews(g.getChildAt(i), list);
            }
        }
    }

    /**
     * Dump an entire view hierarchy to the output.
     */
    @SuppressLint("LogConditional")
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static void debugDumpViewTree(@Nullable final View view,
                                         final int depth) {
        if (view == null) {
            LoggerFactory.getLogger().d(TAG, "debugDumpViewTree|view==NULL");
            return;
        }

        final StringBuilder sb = new StringBuilder();
        if (depth > 0) {
            for (int i = 0; i < depth * 4; i++) {
                sb.append(' ');
            }
        }

        sb.append(view.getClass().getSimpleName())
          .append(" ('").append(getResName(view, view.getId())).append("') -> ");

        if (view instanceof TextView) {
            final String value = ((TextView) view).getText().toString().trim();
            sb.append("\"");
            if (value.length() > 20) {
                sb.append(value.substring(0, 19)).append("…");
            } else {
                sb.append(value);
            }
            sb.append("\"");
            sb.append(dumpFocus(view));
        } else if (view instanceof Checkable) {
            sb.append(((Checkable) view).isChecked());
            sb.append(dumpFocus(view));
        } else if (view instanceof RatingBar) {
            sb.append(((RatingBar) view).getNumStars());
            sb.append(dumpFocus(view));
        } else if (view instanceof ImageView) {
            final Drawable drawable = ((ImageView) view).getDrawable();
            if (drawable != null) {
                sb.append(drawable.getIntrinsicWidth())
                  .append(" x ")
                  .append(drawable.getIntrinsicHeight());
            } else {
                sb.append("No image");
            }
            sb.append(dumpFocus(view));
        }

        LoggerFactory.getLogger().d(TAG, "debugDumpViewTree|" + sb);

        if (view instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                debugDumpViewTree(g.getChildAt(i), depth + 1);
            }
        }
    }

    @NonNull
    private static CharSequence dumpFocus(@NonNull final View view) {
        return new StringBuilder()
                .append("; F:").append(getResName(view, view.getNextFocusForwardId()))
                .append(", D:").append(getResName(view, view.getNextFocusDownId()))
                .append(", U:").append(getResName(view, view.getNextFocusUpId()))
                .append(", L:").append(getResName(view, view.getNextFocusLeftId()))
                .append(", R:").append(getResName(view, view.getNextFocusRightId()));
    }

    @NonNull
    private static String getResName(@NonNull final View view,
                                     @IdRes final int id) {
        if (id == View.NO_ID) {
            return "-1";
        }
        // our chips is dynamically fried, so they have no res name
        if (view instanceof Chip && view.getId() == id) {
            return String.valueOf(id);
        }
        try {
            final String name = view.getResources().getResourceName(id);
            return name.substring(name.indexOf(':'));
        } catch (@NonNull final Resources.NotFoundException e) {
            return String.valueOf(id);
        }
    }

    private interface INextView {

        @IdRes
        int getNext(@NonNull View v);

        void setNext(@NonNull View v,
                     @IdRes int id);
    }
}

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

import android.view.View;
import android.view.ViewGroup;

import java.util.Hashtable;
import java.util.Map;

public class ViewUtils {
    private ViewUtils() {
    }

    /**
     * Ensure that next up/down/left/right View is visible for all sub-views of the passed view.
     */
    public static void fixFocusSettings(View root) {
        final INextView getDown = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusDownId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusDownId(id);
            }
        };
        final INextView getUp = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusUpId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusUpId(id);
            }
        };
        final INextView getLeft = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusLeftId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusLeftId(id);
            }
        };
        final INextView getRight = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusRightId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusRightId(id);
            }
        };

        Hashtable<Integer, View> vh = getViews(root);

        for (Map.Entry<Integer, View> ve : vh.entrySet()) {
            final View v = ve.getValue();
            if (v.getVisibility() == View.VISIBLE) {
                fixNextView(vh, v, getDown);
                fixNextView(vh, v, getUp);
                fixNextView(vh, v, getLeft);
                fixNextView(vh, v, getRight);
            }
        }
    }

    /**
     * Passed a collection of views, a specific View and an INextView, ensure that the
     * currently set 'next' view is actually a visible view, updating it if necessary.
     *
     * @param vh     Collection of all views
     * @param v      View to check
     * @param getter Methods to get/set 'next' view
     */
    private static void fixNextView(Hashtable<Integer, View> vh, View v, INextView getter) {
        int nextId = getter.getNext(v);
        if (nextId != View.NO_ID) {
            int actualNextId = getNextView(vh, nextId, getter);
            if (actualNextId != nextId)
                getter.setNext(v, actualNextId);
        }
    }

    /**
     * Passed a collection of views, a specific view and an INextView object find the
     * first VISIBLE object returned by INextView when called recursively.
     *
     * @param vh     Collection of all views
     * @param nextId ID of 'next' view to get
     * @param getter Interface to lookup 'next' ID given a view
     *
     * @return ID if first visible 'next' view
     */
    private static int getNextView(Hashtable<Integer, View> vh, int nextId, INextView getter) {
        final View v = vh.get(nextId);
        if (v == null)
            return View.NO_ID;

        if (v.getVisibility() == View.VISIBLE)
            return nextId;

        return getNextView(vh, getter.getNext(v), getter);
    }

    /**
     * Passed a parent View return a collection of all child views that have IDs.
     *
     * @param v Parent View
     *
     * @return Hashtable of descendants with ID != NO_ID
     */
    private static Hashtable<Integer, View> getViews(View v) {
        Hashtable<Integer, View> vh = new Hashtable<>();
        getViews(v, vh);
        return vh;
    }

    /**
     * Passed a parent view, add it and all children view (if any) to the passed collection
     *
     * @param p  Parent View
     * @param vh Collection
     */
    private static void getViews(View p, Hashtable<Integer, View> vh) {
        // Get the view ID and add it to collection if not already present.
        final int id = p.getId();
        if (id != View.NO_ID && !vh.containsKey(id)) {
            vh.put(id, p);
        }
        // If it's a ViewGroup, then process children recursively.
        if (p instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) p;
            final int nChildren = g.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                getViews(g.getChildAt(i), vh);
            }
        }
    }

    private interface INextView {
        int getNext(View v);

        void setNext(View v, int id);
    }

    /*
     * Debug utility to dump an entire view hierarchy to the output.
     *
     * @param depth
     * @param v
     */
    //public static void dumpViewTree(int depth, View v) {
    //	for(int i = 0; i < depth*4; i++)
    //		System.out.print(" ");
    //	System.out.print(v.getClass().getName() + " (" + v.getId() + ")" + (v.getId() == R.id.descriptionLabelzzz? "DESC! ->" : " ->"));
    //	if (v instanceof TextView) {
    //		String s = ((TextView)v).getText().toString();
    //		System.out.println(s.substring(0, Math.min(s.length(), 20)));
    //	} else {
    //		System.out.println();
    //	}
    //	if (v instanceof ViewGroup) {
    //		ViewGroup g = (ViewGroup)v;
    //		for(int i = 0; i < g.getChildCount(); i++) {
    //			dumpViewTree(depth+1, g.getChildAt(i));
    //		}
    //	}
    //}
}

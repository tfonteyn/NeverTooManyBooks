package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;

import com.eleybourn.bookcatalogue.BuildConfig;

public class TouchListViewWithDropListener<T> extends TouchListView implements TouchListView.DropListener{

    public TouchListViewWithDropListener(@NonNull final Context context, @NonNull final AttributeSet attrs) {
        super(context, attrs);
        setDropListener(this);
    }

    public TouchListViewWithDropListener(@NonNull final Context context, @NonNull final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setDropListener(this);
    }

    private void onListChanged() {
    }

    @Override
    public void drop(final int from, final int to) {
        // Check if nothing to do; also avoids the nasty case where list size == 1
        if (from == to) {
            return;
        }

        // before remove/insert
        final int firstPos = getFirstVisiblePosition();

        ArrayAdapter<T> adapter = (ArrayAdapter)getAdapter();
        T item = adapter.getItem(from);
        adapter.remove(item);
        adapter.insert(item, to);
        onListChanged();

        // now after the list has changed
        final int first2 = getFirstVisiblePosition();

        if (BuildConfig.DEBUG) {
            System.out.println(from + " -> " + to + ", first " + firstPos + "(" + first2 + ")");
        }
        final int newFirst = (to > from && from < firstPos) ? (firstPos - 1) : firstPos;

        View firstView = getChildAt(0);
        final int offset = firstView.getTop();
        post(new Runnable() {
            @Override
            public void run() {
                if (BuildConfig.DEBUG) {
                    System.out.println("Positioning to " + newFirst + "+{" + offset + "}");
                }
                requestFocusFromTouch();
                setSelectionFromTop(newFirst, offset);
                post(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; ; i++) {
                            View c = getChildAt(i);
                            if (c == null) {
                                break;
                            }
                            if (getPositionForView(c) == to) {
                                setSelectionFromTop(to, c.getTop());
                                //c.requestFocusFromTouch();
                                break;
                            }
                        }
                    }
                });
            }
        });
    }
}

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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * Base class for {@link BookFragment} and {@link EditBookBaseFragment}.
 * <p>
 * This class supports the loading of a book. See {@link #loadFields}.
 * <p>
 * BookBaseFragment -> BookFragment.
 * BookBaseFragment -> EditBookFragment.
 * BookBaseFragment -> EditBookBaseFragment -> EditBookFieldsFragment
 * BookBaseFragment -> EditBookBaseFragment -> EditBookNotesFragment
 * BookBaseFragment -> EditBookBaseFragment -> EditBookPublicationFragment
 * BookBaseFragment -> EditBookBaseFragment -> EditBookTocFragment
 */
public abstract class BookBaseFragment
        extends Fragment {

    /** The book. Must be in the Activity scope for {@link EditBookActivity#onBackPressed()}. */
    BookBaseFragmentModel mBookModel;

    /**
     * The fields collection.
     * Does not store any context or Views, but does use WeakReferences.
     */
    private Fields mFields;

    /**
     * Set the activity title depending on View or Edit mode.
     */
    private void setActivityTitle() {
        Book book = mBookModel.getBook();

        @SuppressWarnings("ConstantConditions")
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            if (book.getId() > 0) {
                // VIEW or EDIT existing book
                actionBar.setTitle(book.getString(DBDefinitions.KEY_TITLE));
                //noinspection ConstantConditions
                actionBar.setSubtitle(book.getAuthorTextShort(getContext()));
            } else {
                // EDIT NEW book
                actionBar.setTitle(R.string.title_add_book);
                actionBar.setSubtitle(null);
            }
        }
    }

    /**
     * Called if an interaction with Goodreads failed due to authorization issues.
     * Prompts the user to register.
     *
     * @param needs {@code true} if registration is needed
     */
    private void needsGoodreads(@Nullable final Boolean needs) {
        if (needs != null && needs) {
            //noinspection ConstantConditions
            RequestAuthTask.needsRegistration(getContext(), mBookModel.getGoodreadsTaskListener());
        }
    }

    /**
     * Allows the ViewModel to send us a message to display to the user.
     * <p>
     * If the type is {@code Integer} we assume it's a {@code StringRes}
     * else we do a toString() it.
     *
     * @param message to display, either a {@code Integer (StringRes)} or a {@code String}
     */
    private void showUserMessage(@Nullable final Object message) {
        View view = getView();
        if (view != null) {
            if (message instanceof Integer) {
                UserMessage.show(view, (int) message);
            } else if (message != null) {
                UserMessage.show(view, message.toString());
            }
        }
    }

    /**
     * Convenience method.
     *
     * @return the fields collection
     */
    @NonNull
    Fields getFields() {
        return mFields;
    }

    /**
     * Convenience method.
     *
     * @param fieldId field to get
     *
     * @return the field
     */
    @NonNull
    <T> Field<T> getField(@IdRes final int fieldId) {
        return mFields.getField(fieldId);
    }

    /**
     * Add any {@link Field} we need to {@link Fields}.
     * <p>
     * Set corresponding formatter (if any)
     * Set onClickListener etc...
     * <p>
     * Note this is NOT where we set values.
     */
    @CallSuper
    void initFields() {
    }

    /**
     * Load all Fields from the actual data store/manager.
     * <p>
     * Loads the data while preserving the isDirty() status.
     * Normally called from the base onResume, but can also be called after {@link Book#reload}.
     * <p>
     * This is 'final' because we want inheritors to implement {@link #onLoadFieldsFromBook}.
     */
    final void loadFields() {
        // load the book, while disabling the AfterFieldChangeListener
        getFields().setAfterFieldChangeListener(null);
        // preserve the 'dirty' status.
        final boolean wasDirty = mBookModel.isDirty();
        // make it so!
        onLoadFieldsFromBook();
        // get dirty...
        mBookModel.setDirty(wasDirty);
        getFields().setAfterFieldChangeListener((field, newValue) -> mBookModel.setDirty(true));

        // this is a good place to do this, as we use data from the book for the title.
        setActivityTitle();
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     */
    @CallSuper
    void onLoadFieldsFromBook() {
        getFields().setAllFrom(mBookModel.getBook());
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(this, requestCode, resultCode, data);
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    //noinspection unchecked
                    ArrayList<Long> bookIds =
                            (ArrayList<Long>) data.getSerializableExtra(UniqueId.BKEY_ID_LIST);

                    if (bookIds != null && bookIds.size() == 1) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        mBookModel.setBook(bookIds.get(0));
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                            Logger.debug(this, "BookBaseFragment.onActivityResult",
                                         "wasCancelled= " + data.getBooleanExtra(
                                                 UniqueId.BKEY_CANCELED, false));
                        }
                    }
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debugWithStackTrace("BookBaseFragment.onActivityResult",
                                               "NOT HANDLED:",
                                               "requestCode=" + requestCode,
                                               "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);
    }

    /**
     * Registers the {@link Book} as a ViewModel, and load/create the its data as needed.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mBookModel = new ViewModelProvider(getActivity()).get(BookBaseFragmentModel.class);
        mBookModel.init(getArguments());
        mBookModel.getUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mBookModel.getNeedsGoodreads().observe(getViewLifecycleOwner(), this::needsGoodreads);

        mFields = new Fields(this);
        initFields();
    }

    /**
     * Trigger the Fragment to load its Fields from the Book.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Logger.debugEnter(this, "onResume");
        }
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) getActivity();
            if (activity.isGoingToRecreate()) {
                return;
            }
        }

        //noinspection ConstantConditions
        LocaleUtils.insanityCheck(getContext());

        loadFields();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Logger.debugExit(this, "onResume");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Logger.debugExit(this, "onPause");
        }
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        Book book = mBookModel.getBook();

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_UPDATE_FROM_INTERNET:
                ArrayList<Long> bookIds = new ArrayList<>();
                bookIds.add(book.getId());
                Intent intentUpdateFields =
                        new Intent(getContext(), UpdateFieldsFromInternetActivity.class)
                                .putExtra(UniqueId.BKEY_ID_LIST, bookIds)
                                .putExtra(DBDefinitions.KEY_TITLE,
                                          book.getString(DBDefinitions.KEY_TITLE))
                                .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                          book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intentUpdateFields,
                                       UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Hides unused fields if they have no useful data.
     * Should normally be called at the *end* of {@link #onLoadFieldsFromBook}
     *
     * @param hideIfEmpty set to {@code true} when displaying; {@code false} when editing.
     */
    void showOrHideFields(final boolean hideIfEmpty) {

        // do all fields with their related fields
        getFields().resetVisibility(hideIfEmpty);

//        // Hide the baseline for the read labels if both labels are gone.
//        // If both labels are visible, then make the baseline invisible.
//        showOrHide(R.id.lbl_read_start_end_baseline, View.INVISIBLE,
//                              R.id.lbl_read_start, R.id.lbl_read_end);
//        // Hide the baseline for the value field if the labels are gone.
//        showOrHide(R.id.read_start_end_baseline, View.INVISIBLE,
//                              R.id.lbl_read_start_end_baseline);
//
//        showOrHide(R.id.lbl_publication_dates_baseline, View.INVISIBLE,
//                   R.id.lbl_first_publication, R.id.lbl_date_published);
//        // Hide the baseline for the value field if the labels are gone.
//        showOrHide(R.id.publication_dates_baseline, View.INVISIBLE,
//                   R.id.lbl_publication_dates_baseline);
    }

    /**
     * Syntax sugar.
     * <p>
     * If all 'fields' are View.GONE, set 'sectionLabelId' to View.GONE as well.
     * Otherwise, set 'sectionLabelId' to View.VISIBLE.
     *
     * @param sectionLabelId field to set
     * @param fields         to check
     */
    void setSectionLabelVisibility(@SuppressWarnings("SameParameterValue")
                                   @IdRes final int sectionLabelId,
                                   @NonNull @IdRes final int... fields) {
        showOrHide(sectionLabelId, View.VISIBLE, fields);
    }

    /**
     * If all 'fields' are View.GONE, set 'fieldToSet' to View.GONE as well.
     * Otherwise, set 'fieldToSet' to the desired visibility.
     *
     * @param fieldToSet      field to set
     * @param visibilityToSet to use for the fieldToSet
     * @param fields          to test for having the same visibility
     */
    private void showOrHide(@IdRes final int fieldToSet,
                            @SuppressWarnings("SameParameterValue") final int visibilityToSet,
                            @NonNull @IdRes final int... fields) {
        //noinspection ConstantConditions
        View fieldView = getView().findViewById(fieldToSet);
        if (fieldView != null) {
            boolean allGone = hasSameVisibility(View.GONE, fields);
            fieldView.setVisibility(allGone ? View.GONE : visibilityToSet);
        }
    }

    /**
     * Check if all fields have the same visibility.
     *
     * @param visibility to check
     * @param fields     to check
     *
     * @return {@code true} if all fields have the same visibility
     */
    private boolean hasSameVisibility(@SuppressWarnings("SameParameterValue") final int visibility,
                                      @IdRes @NonNull final int[] fields) {
        View view = getView();
        for (int fieldId : fields) {
            //noinspection ConstantConditions
            View field = view.findViewById(fieldId);
            if (field != null && field.getVisibility() != visibility) {
                return false;
            }
        }
        return true;
    }

    static final class FocusSettings {

        private FocusSettings() {
        }

        /**
         * Ensure that next up/down/left/right View is visible for all
         * sub-views of the passed view.
         * Sets the nextFocusX attributes on the visible fields.
         */
        static void fix(@NonNull final View root) {
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
                getViews(root, vh);

                for (View v : vh.values()) {
                    if (v.getVisibility() == View.VISIBLE) {
                        fixNextView(vh, v, getDown);
                        fixNextView(vh, v, getUp);
                        fixNextView(vh, v, getLeft);
                        fixNextView(vh, v, getRight);
                    }
                }
            } catch (@NonNull final RuntimeException e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes
                // when the 'next' key is pressed and some views have been hidden.
                Logger.error(root.getContext(), FocusSettings.class, e);
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
        @SuppressWarnings("unused")
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
                Logger.debug(BookBaseFragment.class, "debugDumpViewTree", sb);
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
}

/*
 * @copyright 2013 Philip Warner
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.DataViewer;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.viewmodels.BookBaseFragmentModel;

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
 *
 * @author pjw
 */
public abstract class BookBaseFragment
        extends Fragment
        implements DataViewer {

    /** The book. Must be in the Activity scope for {@link EditBookActivity#onBackPressed()}. */
    BookBaseFragmentModel mBookBaseFragmentModel;

    /** The fields collection. Does not store any context or Views, but does keep WeakReferences. */
    private Fields mFields;

    private void setActivityTitle() {
        Book book = mBookBaseFragmentModel.getBook();

        @SuppressWarnings("ConstantConditions")
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            if (book.getId() > 0) {
                // EDIT existing book
                actionBar.setTitle(book.getString(DBDefinitions.KEY_TITLE));
                //noinspection ConstantConditions
                actionBar.setSubtitle(book.getAuthorTextShort(getContext()));
            } else {
                // NEW book
                actionBar.setTitle(R.string.title_add_book);
                actionBar.setSubtitle(null);
            }
        }
    }

    //<editor-fold desc="Fragment startup">

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
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

        // Activity scope!
        //noinspection ConstantConditions
        mBookBaseFragmentModel = ViewModelProviders.of(getActivity())
                                                   .get(BookBaseFragmentModel.class);
        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;
        mBookBaseFragmentModel.init(args);

        mFields = new Fields(this);
        initFields();
    }

    /** Convenience method. */
    @NonNull
    protected Fields getFields() {
        return mFields;
    }

    /** Convenience method. */
    @NonNull
    Field getField(@IdRes final int fieldId) {
        return mFields.getField(fieldId);
    }


    /**
     * Add any {@link Field} we need to {@link Fields}.
     * <p>
     * Set corresponding validators/formatters (if any)
     * Set onClickListener etc...
     * <p>
     * Note this is NOT where we set values.
     */
    protected void initFields() {
    }

    /**
     * Trigger the Fragment to load its Fields from the Book.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        loadFields();

        Tracker.exitOnResume(this);
    }

    /**
     * Populate all Fields with the data from the Book.
     * Loads the data while preserving the isDirty() status.
     * Normally called from the base onResume, but can also be called after {@link Book#reload}.
     * <p>
     * This is 'final' because we want inheritors to implement {@link #onLoadFieldsFromBook}.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public final void loadFields() {
        // load the book, while disabling the AfterFieldChangeListener
        getFields().setAfterFieldChangeListener(null);
        // preserve the 'dirty' status.
        final boolean wasDirty = mBookBaseFragmentModel.isDirty();
        // make it so!
        onLoadFieldsFromBook();
        // get dirty...
        mBookBaseFragmentModel.setDirty(wasDirty);
        getFields().setAfterFieldChangeListener(
                (field, newValue) -> mBookBaseFragmentModel.setDirty(true));

        // this is a good place to do this, as we use data from the book for the title.
        setActivityTitle();
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     */
    @CallSuper
    protected void onLoadFieldsFromBook() {
        getFields().setAllFrom(mBookBaseFragmentModel.getBook());
    }

    //</editor-fold>

    //<editor-fold desc="Menu handlers">

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        Book book = mBookBaseFragmentModel.getBook();

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_UPDATE_FROM_INTERNET:
                Intent intentUpdateFields =
                        new Intent(getContext(), UpdateFieldsFromInternetActivity.class)
                                .putExtra(DBDefinitions.KEY_ID, book.getId())
                                .putExtra(DBDefinitions.KEY_TITLE,
                                          book.getString(DBDefinitions.KEY_TITLE))
                                .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                          book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intentUpdateFields,
                                       UniqueId.REQ_UPDATE_BOOK_FIELDS_FROM_INTERNET);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //</editor-fold>

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_UPDATE_BOOK_FIELDS_FROM_INTERNET:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    long bookId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                    if (bookId > 0) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        mBookBaseFragmentModel.setBook(bookId);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                            Logger.debug("BookBaseFragment.onActivityResult",
                                         "wasCancelled= " + data.getBooleanExtra(
                                                 UniqueId.BKEY_CANCELED, false));
                        }
                    }
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.warnWithStackTrace("BookBaseFragment.onActivityResult",
                                              "NOT HANDLED:",
                                              "requestCode=" + requestCode,
                                              "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Hides unused fields if they have no useful data.
     * Should normally be called at the *end* of {@link #onLoadFieldsFromBook}
     * <p>
     * Authors & Title are always visible as they are required fields.
     * <p>
     * Series is done in:
     * {@link EditBookFieldsFragment} #populateSeriesListField}
     * {@link BookFragment}  #populateSeriesListField}
     * <p>
     * Special fields not checked here:
     * - toc
     * - edition
     *
     * @param hideIfEmpty set to {@code true} when displaying; {@code false} when editing.
     */
    void showHideFields(final boolean hideIfEmpty) {
        // reset to user-preferences.
        getFields().resetVisibility();

        // actual book
        showHide(hideIfEmpty, R.id.coverImage);
        showHide(hideIfEmpty, R.id.isbn, R.id.lbl_isbn);
        showHide(hideIfEmpty, R.id.description, R.id.lbl_description);

        showHide(hideIfEmpty, R.id.pages, R.id.lbl_pages);
        showHide(hideIfEmpty, R.id.format, R.id.lbl_format);

        showHide(hideIfEmpty, R.id.genre, R.id.lbl_genre);
        showHide(hideIfEmpty, R.id.language, R.id.lbl_language);
        showHide(hideIfEmpty, R.id.first_publication, R.id.lbl_first_publication);
//        showHide(hideIfEmpty, R.id.toc, R.id.row_toc);

        showHide(hideIfEmpty, R.id.publisher);
        showHide(hideIfEmpty, R.id.date_published);

        // Hide the label if none of the publishing fields are shown.
        setVisibilityGoneOr(R.id.lbl_publication_section, View.VISIBLE,
                            R.id.publisher, R.id.date_published,
                            R.id.price_listed, R.id.first_publication);

        showHide(hideIfEmpty, R.id.price_listed, R.id.price_listed_currency, R.id.lbl_price_listed);

        // personal fields
        showHide(hideIfEmpty, R.id.bookshelves, R.id.name, R.id.lbl_bookshelves);
        showHide(hideIfEmpty, R.id.read);

        //showHide(hideIfEmpty, R.id.edition, R.id.lbl_edition);

        showHide(hideIfEmpty, R.id.notes);
        showHide(hideIfEmpty, R.id.location, R.id.lbl_location, R.id.lbl_location_long);
        showHide(hideIfEmpty, R.id.date_acquired, R.id.lbl_date_acquired);

        showHide(hideIfEmpty, R.id.price_paid, R.id.price_paid_currency, R.id.lbl_price_paid);

        showHide(hideIfEmpty, R.id.read_start, R.id.lbl_read_start);
        showHide(hideIfEmpty, R.id.read_end, R.id.lbl_read_end);
        // Hide the baseline if both fields are gone.
        setVisibilityGoneOr(R.id.lbl_read_start_end_baseline, View.INVISIBLE,
                            R.id.lbl_read_start, R.id.lbl_read_end);
        // Hide the baseline if both fields are gone.
        setVisibilityGoneOr(R.id.read_start_end_baseline, View.INVISIBLE,
                            R.id.lbl_read_start_end_baseline);

        showHide(hideIfEmpty, R.id.signed, R.id.lbl_signed);
        showHide(hideIfEmpty, R.id.rating, R.id.lbl_rating);

        showHide(hideIfEmpty, R.id.loaned_to);

        //NEWKIND: new fields
    }

    /**
     * @param hideIfEmpty   hide the field if it's empty
     * @param fieldId       layout resource id of the field
     * @param relatedFields list of fields whose visibility will also be set, based
     *                      on the first field
     */
    private void showHide(final boolean hideIfEmpty,
                          @IdRes final int fieldId,
                          @NonNull @IdRes final int... relatedFields) {
        View view = requireView().findViewById(fieldId);
        if (view != null) {
            int visibility = view.getVisibility();
            if (hideIfEmpty) {
                // Don't check/show a field if it is already hidden (assumed by user preference)
                if (visibility != View.GONE) {

                    // hide any unchecked Checkable.
                    if (view instanceof Checkable) {
                        visibility = ((Checkable) view).isChecked() ? View.VISIBLE : View.GONE;
                        view.setVisibility(visibility);

                    } else if (!(view instanceof ImageView)) {
                        // don't act on ImageView, but all other fields can be string tested.

                        final String value = getField(fieldId).getValue().toString().trim();
                        visibility = !value.isEmpty() ? View.VISIBLE : View.GONE;
                        view.setVisibility(visibility);
                    }
                }
            }

            setVisibility(visibility, relatedFields);
        }
    }

    /**
     * If all 'fields' are View.GONE, set 'fieldToSet' to View.GONE as well.
     * Otherwise, set 'fieldToSet' to the desired visibility.
     *
     * @param fieldToSet field to set
     * @param visibility to use for the fieldToSet
     * @param fields     to test
     */
    private void setVisibilityGoneOr(@IdRes final int fieldToSet,
                                     final int visibility,
                                     @NonNull @IdRes final int... fields) {
        View baselineField = requireView().findViewById(fieldToSet);
        if (baselineField != null) {
            baselineField.setVisibility(isVisibilityGone(fields) ? View.GONE : visibility);
        }
    }

    /**
     * @param fields to check
     *
     * @return {@code true} if all fields have visibility == View.GONE
     */
    private boolean isVisibilityGone(@IdRes @NonNull final int[] fields) {
        boolean isGone = true;
        for (int fieldId : fields) {
            View field = requireView().findViewById(fieldId);
            if (field != null) {
                // all fields must be gone to result into isGone==true
                isGone = isGone && (field.getVisibility() == View.GONE);
            }
        }
        return isGone;
    }

    /**
     * Set the visibility for a list of fields.
     *
     * @param visibility to use
     * @param fields     list of fields to set visibility on
     */
    protected void setVisibility(final int visibility,
                                 @NonNull @IdRes final int... fields) {
        View view = requireView();
        for (int fieldId : fields) {
            View field = view.findViewById(fieldId);
            if (field != null) {
                field.setVisibility(visibility);
            }
        }
    }

    static final class ViewUtils {

        private ViewUtils() {
        }

        /**
         * Ensure that next up/down/left/right View is visible for all
         * sub-views of the passed view.
         */
        static void fixFocusSettings(@NonNull final View root) {
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
            } catch (RuntimeException e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes
                // when the 'next' key is pressed and some views have been hidden.
                Logger.error(ViewUtils.class, e);
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
         * @param nextId ID of 'next' view to get
         * @param getter Interface to lookup 'next' ID given a view
         *
         * @return ID if first visible 'next' view
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
            // Get the view ID and add it to collection if not already present.
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
              .append(" (").append(view.getId()).append(')')
              .append(" ->");

            if (view instanceof TextView) {
                String s = ((TextView) view).getText().toString().trim();
                s = s.substring(0, Math.min(s.length(), 20));
                sb.append(s);
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

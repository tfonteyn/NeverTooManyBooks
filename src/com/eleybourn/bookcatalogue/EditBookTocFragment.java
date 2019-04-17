/*
 * @copyright 2013 Evan Leybourn
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
import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.PopupMenuDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.isfdb.Editions;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBBook;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.KEY_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.KEY_DATE_FIRST_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.KEY_TITLE;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab.
 * <p>
 * Doesn't use {@link UpdateFieldsFromInternetTask}
 * as this would actually introduce the ManagedTask usage which we want to phase out.
 * <p>
 * The ISFDB direct interaction should however be seen as temporary as this class should not
 * have to know about any specific search web site.
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** Fragment manager t. */
    public static final String TAG = EditBookTocFragment.class.getSimpleName();

    /** the book. */
    private String mIsbn;
    /** primary author of the book. */
    private String mBookAuthor;
    /** checkbox to hide/show the author edit field. */
    private CompoundButton mMultipleAuthorsView;

    /** position of row we're currently editing. */
    @Nullable
    private Integer mEditPosition;

    private ArrayList<TocEntry> mList;
    private ArrayAdapter<TocEntry> mListAdapter;
    private ListView mListView;

    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    private ArrayList<Editions.Edition> mISFDBEditions;

    /* ------------------------------------------------------------------------------------------ */

    @Override
    @NonNull
    protected BookManager getBookManager() {
        return ((EditBookFragment) requireParentFragment()).getBookManager();
    }

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_toc, container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = requireView();

        // Author to use if mMultipleAuthorsView is set to false
        mBookAuthor = getBookManager().getBook().getString(DBDefinitions.KEY_AUTHOR_FORMATTED);

        // used to call Search sites to populate the TOC
        mIsbn = getBookManager().getBook().getString(DBDefinitions.KEY_ISBN);

        view.findViewById(R.id.btn_add).setOnClickListener(v -> newEntry());

        mMultipleAuthorsView = view.findViewById(R.id.multiple_authors);

        mListView = view.findViewById(android.R.id.list);

        // We want context menus on the ListView
        //mListView.setOnCreateContextMenuListener(this);
        // no, we don't, as we'll use long click to bring up custom context menus WITH icons
        mListView.setOnItemLongClickListener(this::onItemLongClick);

        // Handle drop events; also preserves current position.
        ((TouchListView) mListView).setOnDropListener(this::onDrop);
    }

    /**
     * TOMF: code nearly identical with {@link EditObjectListActivity#onDrop(int, int)}
     *
     * @param fromPosition original position of the row
     * @param toPosition   where the row was dropped
     */
    private void onDrop(final int fromPosition,
                        final int toPosition) {
        // Check if nothing to do; also avoids the nasty case where list size == 1
        if (fromPosition == toPosition) {
            return;
        }

        // update the list
        TocEntry item = mListAdapter.getItem(fromPosition);
        mListAdapter.remove(item);
        mListAdapter.insert(item, toPosition);
        onListChanged();

        final ListView listView = getListView();
        final int firstVisiblePosition = listView.getFirstVisiblePosition();
        final int newFirst;
        if (toPosition > fromPosition && fromPosition < firstVisiblePosition) {
            newFirst = firstVisiblePosition - 1;
        } else {
            newFirst = firstVisiblePosition;
        }

        View firstView = listView.getChildAt(0);
        final int offset = firstView.getTop();

        // re-position the list
        listView.post(() -> {
            listView.requestFocusFromTouch();
            listView.setSelectionFromTop(newFirst, offset);
            listView.post(() -> {
                for (int i = 0; ; i++) {
                    View c = listView.getChildAt(i);
                    if (c == null) {
                        break;
                    }
                    if (listView.getPositionForView(c) == toPosition) {
                        listView.setSelectionFromTop(toPosition, c.getTop());
                        //c.requestFocusFromTouch();
                        break;
                    }
                }
            });
        });
    }

    private void onListChanged() {
        //after a drop, don't care for now.
    }

    private ListView getListView() {
        return mListView;
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    @Override
    protected void initFields() {
        super.initFields();

        /* Anthology is provided as a bitmask, see {@link Book#initValidators()}*/
        mFields.add(R.id.is_anthology, Book.HAS_MULTIPLE_WORKS);
        mFields.add(R.id.multiple_authors, Book.HAS_MULTIPLE_AUTHORS);
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        super.onLoadFieldsFromBook(book, setAllFrom);

        // populateFields
        populateContentList();

        // Restore default visibility
        showHideFields(false);
    }

    /**
     * Populate the list view with the book content table.
     */
    private void populateContentList() {
        // Get all of the rows and create the item list
        mList = getBookManager().getBook().getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        // Create a simple array adapter and set it to display
        mListAdapter = new TocListAdapterForEditing(requireContext(), mList);
        mListView.setAdapter(mListAdapter);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    @Override
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        super.onSaveFieldsToBook(book);
        book.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, mList);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu Handlers">

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);
        // don't call super. We don't want the clutter in this tab.
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB:
                //noinspection ConstantConditions
                UserMessage.showUserMessage(getView(),
                                            R.string.progress_msg_connecting_to_web_site);
                new ISFDBGetEditionsTask(mIsbn, this).execute();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Reminder: the item row itself has to have:  android:longClickable="true".
     * Otherwise the click will only work on the 'blank' bits of the row.
     */
    private boolean onItemLongClick(@NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id) {
        TocEntry tocEntry = mListAdapter.getItem(position);

        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(getContext(), null).getMenu();
        menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        // display the menu
        //noinspection ConstantConditions
        String menuTitle = tocEntry.getTitle();
        //noinspection ConstantConditions
        PopupMenuDialog.onCreateListViewContextMenu(getContext(), position, menuTitle, menu,
                                                    this::onListViewContextItemSelected);
        return true;
    }

    /**
     * Using {@link PopupMenuDialog} for context menus.
     */
    private boolean onListViewContextItemSelected(@NonNull final MenuItem menuItem,
                                                  final int position) {
        TocEntry tocEntry1 = mList.get(position);
        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                editEntry(position, tocEntry1);
                return true;

            case R.id.MENU_DELETE:
                mListAdapter.remove(tocEntry1);
                getBookManager().setDirty(true);
                return true;

            default:
                return false;

        }
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="ISFDB interface">

    /**
     * we got one or more editions from ISFDB.
     * Store the url's locally as the user might want to try the next in line
     * <p>
     * ENHANCE: add the url's to the options menu for retry.
     * Remove from menu each time one is tried.
     */
    private void onGotISFDBEditions(@Nullable final ArrayList<Editions.Edition> editions) {
        mISFDBEditions = editions != null ? editions : new ArrayList<>();
        if (!mISFDBEditions.isEmpty()) {
            new ISFDBGetBookTask(mISFDBEditions, false, this).execute();
        } else {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getActivity(), R.string.warning_no_editions);
        }
    }

    /**
     * we got a book.
     *
     * @param bookData our book from ISFDB.
     */
    private void onGotISFDBBook(@Nullable final Bundle bookData) {
        if (bookData == null) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getActivity(), R.string.warning_book_not_found);
            return;
        }

        // update the book with series information that was gathered from the TOC
        List<Series> series = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (series != null && !series.isEmpty()) {
            ArrayList<Series> inBook = getBookManager().getBook()
                                                       .getParcelableArrayList(
                                                               UniqueId.BKEY_SERIES_ARRAY);
            // add, weeding out duplicates
            for (Series s : series) {
                if (!inBook.contains(s)) {
                    inBook.add(s);
                }
            }
            getBookManager().getBook().putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, inBook);
        }

        // update the book with the first publication date that was gathered from the TOC
        final String bookFirstPublication =
                bookData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED);
        if (bookFirstPublication != null) {
            if (getBookManager().getBook()
                                .getString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED).isEmpty()) {
                getBookManager().getBook().putString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED,
                                                     bookFirstPublication);
            }
        }

        // finally the TOC itself; not saved here but only put on display for the user to approve
        ConfirmToc.show(this, bookData, mISFDBEditions.size() > 1);
    }

    /**
     * The user approved, so add the TOC to the list on screen (still not saved to database).
     */
    private void commitISFDBData(final long tocBitMask,
                                 @NonNull final List<TocEntry> tocEntries) {
        if (tocBitMask != 0) {
            Book book = getBookManager().getBook();
            book.putLong(DBDefinitions.KEY_TOC_BITMASK, tocBitMask);
            mFields.getField(R.id.multiple_authors).setValueFrom(book);
        }

        mList.addAll(tocEntries);
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Start a task to get the next edition of this book (that we know of).
     */
    private void getNextEdition() {
        // remove the top one, and try again
        mISFDBEditions.remove(0);
        new ISFDBGetBookTask(mISFDBEditions, false, this).execute();
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Create a new entry.
     */
    private void newEntry() {
        mEditPosition = null;

        EditTocEntry.show(this);
    }

    /**
     * copy the selected entry into the edit fields,
     * and set the confirm button to reflect a save (versus add).
     *
     * @param position of the entry in the list
     * @param item     the entry to edit
     */
    private void editEntry(final int position,
                           @NonNull final TocEntry item) {
        mEditPosition = position;

        EditTocEntry.show(this,
                          mMultipleAuthorsView.isChecked(),
                          item.getAuthor().getDisplayName(),
                          item.getTitle(),
                          item.getFirstPublication());
    }

    /**
     * Add the author/title from the edit fields as a new row in the TOC list.
     */
    private void addOrUpdateEntry(@NonNull final String author,
                                  @NonNull final String title,
                                  @NonNull final String pubDate) {

        Author tocAuthor;
        if (!mMultipleAuthorsView.isChecked()) {
            // ignore the incoming dummy, and use the primary book author.
            tocAuthor = Author.fromString(mBookAuthor);
        } else {
            tocAuthor = Author.fromString(author);
        }

        if (mEditPosition == null) {
            // add the new entry
            mListAdapter.add(new TocEntry(tocAuthor, title, pubDate));
        } else {
            // update the existing entry
            TocEntry tocEntry = mListAdapter.getItem(mEditPosition);
            //noinspection ConstantConditions
            tocEntry.setAuthor(tocAuthor);
            tocEntry.setTitle(title);
            tocEntry.setFirstPublication(pubDate);

            mListAdapter.notifyDataSetChanged();
        }

        getBookManager().setDirty(true);
    }

    /**
     * Will survive a rotation, but not a killed activity.
     * <p>
     * Uses setTargetFragment/getTargetFragment with type {@link EditBookTocFragment}.
     */
    public static class ConfirmToc
            extends DialogFragment {

        /** Fragment manager t. */
        private static final String TAG = ConfirmToc.class.getSimpleName();

        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";

        /**
         * (syntax sugar for newInstance)
         */
        public static void show(@NonNull final Fragment target,
                                @NonNull final Bundle bookData,
                                final boolean hasOtherEditions) {
            FragmentManager fm = target.requireFragmentManager();
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(target, bookData, hasOtherEditions).show(fm, TAG);
            }
        }

        /**
         * Constructor.
         *
         * @return the instance
         */
        public static ConfirmToc newInstance(@NonNull final Fragment target,
                                             @NonNull final Bundle bookData,
                                             final boolean hasOtherEditions) {
            ConfirmToc frag = new ConfirmToc();
            bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);
            frag.setTargetFragment(target, 0);
            frag.setArguments(bookData);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final EditBookTocFragment targetFragment = (EditBookTocFragment) getTargetFragment();
            Objects.requireNonNull(targetFragment);
            Bundle args = requireArguments();
            boolean hasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS);
            final long tocBitMask = args.getLong(DBDefinitions.KEY_TOC_BITMASK);
            ArrayList<TocEntry> tocEntries =
                    args.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
            boolean hasToc = tocEntries != null && !tocEntries.isEmpty();

            StringBuilder msg = new StringBuilder();
            if (hasToc) {
                msg.append(getString(R.string.warning_toc_confirm)).append("\n\n");
                for (TocEntry t : tocEntries) {
                    msg.append(t.getTitle()).append(", ");
                }
            } else {
                msg.append(getString(R.string.error_auto_toc_population_failed));
            }

            TextView content = new TextView(getContext());
            content.setText(msg);

            // we read the value from the attr/style in pixels
            //noinspection ConstantConditions
            content.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                                App.getTextAppearanceSmallTextSizeInPixels(getContext()));
            //API: 23:
            //content.setTextAppearance(android.R.style.TextAppearance_Small);
            //API: 26 ?
            //content.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);

            final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setView(content)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .create();

            if (hasToc) {
                final List<TocEntry> finalTocEntryList = tocEntries;
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 (d, which) -> targetFragment.commitISFDBData(tocBitMask,
                                                                              finalTocEntryList));
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (hasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                                 (d, which) -> targetFragment.getNextEdition());
            }

            return dialog;
        }
    }

    public static class EditTocEntry
            extends DialogFragment {

        /** Fragment manager t. */
        private static final String TAG = EditTocEntry.class.getSimpleName();

        private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";

        private AutoCompleteTextView mAuthorTextView;
        private EditText mTitleTextView;
        private EditText mPubDateTextView;

        /**
         * (syntax sugar for newInstance)
         * <p>
         * Show dialog for a new entry.
         */
        public static void show(@NonNull final Fragment target) {
            FragmentManager fm = target.requireFragmentManager();
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(target, false, "", "", "")
                        .show(fm, TAG);
            }
        }

        /**
         * (syntax sugar for newInstance)
         * <p>
         * Show dialog for an existing entry.
         */
        public static void show(@NonNull final Fragment target,
                                final boolean hasMultipleAuthors,
                                @NonNull final String author,
                                @NonNull final String title,
                                @NonNull final String firstPublication) {
            FragmentManager fm = target.requireFragmentManager();
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(target, hasMultipleAuthors, author, title, firstPublication)
                        .show(fm, TAG);
            }
        }

        /**
         * Constructor.
         *
         * @return the instance
         */
        public static EditTocEntry newInstance(@NonNull final Fragment target,
                                               final boolean hasMultipleAuthors,
                                               @NonNull final String author,
                                               @NonNull final String title,
                                               @NonNull final String firstPublication) {
            EditTocEntry frag = new EditTocEntry();
            frag.setTargetFragment(target, 0);
            Bundle args = new Bundle();
            args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
            args.putString(KEY_AUTHOR, author);
            args.putString(KEY_TITLE, title);
            args.putString(KEY_DATE_FIRST_PUBLISHED, firstPublication);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

            final EditBookTocFragment targetFragment = (EditBookTocFragment) getTargetFragment();
            Objects.requireNonNull(targetFragment);
            Bundle args = savedInstanceState != null ? savedInstanceState : requireArguments();

            @SuppressLint("InflateParams")
            final View root = requireActivity().getLayoutInflater()
                                               .inflate(R.layout.dialog_edit_book_toc, null);

            // Author AutoCompleteTextView
            mAuthorTextView = root.findViewById(R.id.author);
            if (args.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false)) {
                ArrayAdapter<String> authorAdapter =
                        new ArrayAdapter<>(requireContext(),
                                           android.R.layout.simple_dropdown_item_1line,
                                           targetFragment.mDb.getAuthorsFormattedName());
                mAuthorTextView.setAdapter(authorAdapter);

                mAuthorTextView.setText(args.getString(KEY_AUTHOR));
                mAuthorTextView.setVisibility(View.VISIBLE);
            } else {
                mAuthorTextView.setVisibility(View.GONE);
            }

            mTitleTextView = root.findViewById(R.id.title);
            mTitleTextView.setText(args.getString(KEY_TITLE));

            mPubDateTextView = root.findViewById(R.id.first_publication);
            mPubDateTextView.setText(args.getString(KEY_DATE_FIRST_PUBLISHED));

            return new AlertDialog.Builder(requireContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setView(root)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok,
                                       (d, which) -> targetFragment.addOrUpdateEntry(
                                               mAuthorTextView.getText().toString().trim(),
                                               mTitleTextView.getText().toString().trim(),
                                               mPubDateTextView.getText().toString().trim()))
                    .create();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(KEY_AUTHOR, mAuthorTextView.getText().toString().trim());
            outState.putString(KEY_TITLE, mTitleTextView.getText().toString().trim());
            outState.putString(KEY_DATE_FIRST_PUBLISHED,
                               mPubDateTextView.getText().toString().trim());
        }
    }

    private static class ISFDBGetEditionsTask
            extends AsyncTask<Void, Void, ArrayList<Editions.Edition>> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final EditBookTocFragment mCallback;

        /**
         * Constructor.
         *
         * @param isbn     to search for
         * @param callback to send results to
         */
        @UiThread
        ISFDBGetEditionsTask(@NonNull final String isbn,
                             @NonNull final EditBookTocFragment callback) {
            mIsbn = isbn;
            mCallback = callback;
        }

        @Override
        @Nullable
        @WorkerThread
        protected ArrayList<Editions.Edition> doInBackground(final Void... params) {
            try {
                return new Editions().fetch(mIsbn);
            } catch (SocketTimeoutException e) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    Logger.warn(this, "doInBackground", e.getLocalizedMessage());
                }
                return null;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(final ArrayList<Editions.Edition> result) {
            // always send result, even if empty
            mCallback.onGotISFDBEditions(result);
        }
    }

    private static class ISFDBGetBookTask
            extends AsyncTask<Void, Void, Bundle> {

        @NonNull
        private final EditBookTocFragment mCallback;

        @NonNull
        private final List<Editions.Edition> mEditions;
        private final boolean mFetchThumbnail;

        /**
         * Constructor.
         *
         * @param editions       List of ISFDB native ids
         * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
         * @param callback       where to send the results to
         */
        @UiThread
        ISFDBGetBookTask(@NonNull final List<Editions.Edition> editions,
                         final boolean fetchThumbnail,
                         @NonNull final EditBookTocFragment callback) {
            mEditions = editions;
            mFetchThumbnail = fetchThumbnail;
            mCallback = callback;
        }

        @Override
        @Nullable
        @WorkerThread
        protected Bundle doInBackground(final Void... params) {
            try {
                return new ISFDBBook().fetch(mEditions, new Bundle(), mFetchThumbnail);
            } catch (SocketTimeoutException e) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    Logger.warn(this, "doInBackground", e.getLocalizedMessage());
                }
                return null;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Bundle result) {
            // always send result, even if empty
            mCallback.onGotISFDBBook(result);
        }
    }

    private class TocListAdapterForEditing
            extends SimpleListAdapter<TocEntry> {

        /**
         * Constructor.
         */
        TocListAdapterForEditing(@NonNull final Context context,
                                 @NonNull final ArrayList<TocEntry> items) {
            super(context, R.layout.row_edit_toc_entry, items);
        }

        /**
         * We're dirty...
         */
        @Override
        public void onListChanged() {
            getBookManager().setDirty(true);
        }

        @Override
        public void onGetView(@NonNull final View convertView,
                              @NonNull final TocEntry item) {

            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                holder = new Holder(convertView);
            }

            holder.titleView.setText(item.getTitle());
            holder.authorView.setText(item.getAuthor().getDisplayName());

            String year = item.getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                holder.firstPublicationView.setText(getString(R.string.brackets, year));
            }
        }

        /**
         * Holder pattern for each row.
         */
        private class Holder {

            @NonNull
            final TextView titleView;
            @NonNull
            final TextView authorView;
            @NonNull
            final TextView firstPublicationView;

            Holder(@NonNull final View rowView) {
                titleView = rowView.findViewById(R.id.title);
                authorView = rowView.findViewById(R.id.author);
                firstPublicationView = rowView.findViewById(R.id.year);

                rowView.setTag(this);
            }
        }
    }
}

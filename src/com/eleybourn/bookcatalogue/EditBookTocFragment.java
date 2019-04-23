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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
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
import com.eleybourn.bookcatalogue.widgets.TouchRecyclerViewCFS;
import com.eleybourn.bookcatalogue.widgets.ViewHolderBase;

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

    /** Fragment manager tag. */
    public static final String TAG = EditBookTocFragment.class.getSimpleName();

    /** The book. */
    private String mIsbn;
    /** primary author of the book. */
    private Author mBookAuthor;
    /** checkbox to hide/show the author edit field. */
    private CompoundButton mMultipleAuthorsView;

    private ArrayList<TocEntry> mList;
    private TocListAdapterForEditing mListAdapter;
    private TouchRecyclerViewCFS mListView;

    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    private ArrayList<Editions.Edition> mISFDBEditions;
    private Integer mEditPosition;

    @Override
    @NonNull
    protected BookManager getBookManager() {
        return ((EditBookFragment) requireParentFragment()).getBookManager();
    }

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
     * <li>{@link BookManager#getBook()} on the hosting Activity
     * <li>{@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * <li>{@link #onSaveFieldsToBook(Book)} from base class onPause
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Book book = getBookManager().getBook();

        // Author to use if mMultipleAuthorsView is set to false
        List<Author> authorList = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        mBookAuthor = authorList.get(0);

        // used to call Search sites to populate the TOC
        mIsbn = book.getString(DBDefinitions.KEY_ISBN);

        ViewUtils.fixFocusSettings(requireView());
    }

    //</editor-fold>

    //<editor-fold desc="Populate">

    @Override
    protected void initFields() {
        super.initFields();

        Fields.Field field;
        // Anthology is provided as a bitmask, see {@link Book#initValidators()}
        mFields.add(R.id.is_anthology, Book.HAS_MULTIPLE_WORKS)
               .getView().setOnClickListener((v) -> {
            // enable controls as applicable.
            mMultipleAuthorsView.setEnabled(((Checkable) v).isChecked());
        });

        field = mFields.add(R.id.multiple_authors, Book.HAS_MULTIPLE_AUTHORS);
        mMultipleAuthorsView = field.getView();

        View view = requireView();
        // adding a new TOC entry
        view.findViewById(R.id.btn_add).setOnClickListener(v -> newItem());

        mListView = view.findViewById(android.R.id.list);
        mListView.setLayoutManager(new LinearLayoutManager(getContext()));
        // TouchList; allow re-ordering entries.
        mListView.setOnDropListener(((fromPosition, toPosition) -> {
            // Check if nothing to do
            if (fromPosition == toPosition) {
                return;
            }

            // update the list
            TocEntry item = mList.get(fromPosition);
            mList.remove(item);
            mList.add(toPosition, item);
            onListChanged();
        }));
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        super.onLoadFieldsFromBook(book, setAllFrom);

        mMultipleAuthorsView.setEnabled(getBookManager().getBook()
                                                        .getBoolean(Book.HAS_MULTIPLE_WORKS));

        // Populate the list view with the book content table.
        mList = getBookManager().getBook().getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        mListAdapter = new TocListAdapterForEditing(requireContext(), mList);
        mListView.setAdapter(mListAdapter);

        // Restore default visibility
        showHideFields(false);
    }

    //</editor-fold>

    //<editor-fold desc="Fragment shutdown">

    /**
     * The toc list is not a 'real' field. Hence the need to store it manually here.
     *
     * @param book field content (toc list) will be copied to this object
     */
    @Override
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        super.onSaveFieldsToBook(book);
        book.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, mList);
    }

    //</editor-fold>

    //<editor-fold desc="Menu Handlers">

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
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

    private boolean onCreateContextMenu(final int position) {

        TocEntry item = mList.get(position);
        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(getContext(), null).getMenu();
        menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        // display the menu
        String menuTitle = item.getTitle();
        //noinspection ConstantConditions
        PopupMenuDialog.showContextMenu(getContext(), menuTitle, menu, position,
                                        this::onContextItemSelected);

        return true;
    }

    /**
     * Using {@link PopupMenuDialog} for context menus.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final Integer position) {
        TocEntry tocEntry = mList.get(position);

        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                editItem(position);
                return true;

            case R.id.MENU_DELETE:
                mList.remove(tocEntry);
                onListChanged();
                return true;

            default:
                return false;
        }
    }

    //</editor-fold>

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
        onListChanged();
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

    /**
     * Create a new entry.
     */
    private void newItem() {
        mEditPosition = null;

        EditTocEntry.show(this, mMultipleAuthorsView.isChecked(),
                          new TocEntry(mBookAuthor, "", ""));
    }

    /**
     * copy the selected entry into the edit fields,
     * and set the confirm button to reflect a save (versus add).
     */
    private void editItem(final int position) {
        mEditPosition = position;

        EditTocEntry.show(this, mMultipleAuthorsView.isChecked(), mList.get(position));
    }

    /**
     * Add the author/title from the edit fields as a new row in the TOC list.
     */
    private void addOrUpdateEntry(@NonNull final TocEntry tocEntry) {

        if (mEditPosition == null) {
            // add the new entry
            mList.add(tocEntry);
        } else {
            // find and update
            TocEntry original = mList.get(mEditPosition);
            original.copyFrom(tocEntry);
        }

        onListChanged();
    }

    private void onListChanged() {
        mListAdapter.notifyDataSetChanged();
        getBookManager().setDirty(true);
    }

    /**
     * Will survive a rotation, but not a killed activity.
     * <p>
     * Uses setTargetFragment/getTargetFragment with type {@link EditBookTocFragment}.
     */
    public static class ConfirmToc
            extends DialogFragment {

        /** Fragment manager tag. */
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

    /**
     * Dialog to add a new TOCEntry, or edit an existing one.
     */
    public static class EditTocEntry
            extends DialogFragment {

        /** Fragment manager tag. */
        private static final String TAG = EditTocEntry.class.getSimpleName();

        private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
        private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";

        private AutoCompleteTextView mAuthorTextView;
        private EditText mTitleTextView;
        private EditText mPubDateTextView;

        private boolean mHasMultipleAuthors;
        private TocEntry mTocEntry;

        /**
         * (syntax sugar for newInstance)
         * <p>
         * Show dialog for an existing entry.
         */
        public static void show(@NonNull final Fragment target,
                                final boolean hasMultipleAuthors,
                                @NonNull final TocEntry tocEntry) {
            FragmentManager fm = target.requireFragmentManager();
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(target, hasMultipleAuthors, tocEntry)
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
                                               TocEntry tocEntry) {
            EditTocEntry frag = new EditTocEntry();
            frag.setTargetFragment(target, 0);
            Bundle args = new Bundle();
            args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
            args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

            final EditBookTocFragment targetFragment = (EditBookTocFragment) getTargetFragment();
            Objects.requireNonNull(targetFragment);

            @SuppressLint("InflateParams")
            final View root = requireActivity().getLayoutInflater()
                                               .inflate(R.layout.dialog_edit_book_toc, null);

            mAuthorTextView = root.findViewById(R.id.author);
            mTitleTextView = root.findViewById(R.id.title);
            mPubDateTextView = root.findViewById(R.id.first_publication);


            Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;

            mTocEntry = args.getParcelable(BKEY_TOC_ENTRY);
            mHasMultipleAuthors = args.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false);

            if (mHasMultipleAuthors) {
                ArrayAdapter<String> authorAdapter =
                        new ArrayAdapter<>(requireContext(),
                                           android.R.layout.simple_dropdown_item_1line,
                                           targetFragment.mDb.getAuthorsFormattedName());
                mAuthorTextView.setAdapter(authorAdapter);

                mAuthorTextView.setText(mTocEntry.getAuthor().getLabel());
                mAuthorTextView.setVisibility(View.VISIBLE);
            } else {
                mAuthorTextView.setVisibility(View.GONE);
            }

            mTitleTextView.setText(mTocEntry.getTitle());
            mPubDateTextView.setText(mTocEntry.getFirstPublication());

            return new AlertDialog.Builder(requireContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setView(root)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok,
                                       (dialog, which) -> {
                                           getFields();
                                           targetFragment.addOrUpdateEntry(mTocEntry);
                                       })
                    .create();
        }

        @Override
        public void onPause() {
            getFields();
            super.onPause();
        }

        private void getFields() {
            mTocEntry.setTitle(mTitleTextView.getText().toString().trim());
            mTocEntry.setFirstPublication(mPubDateTextView.getText().toString().trim());
            if (mHasMultipleAuthors) {
                mTocEntry.setAuthor(Author.fromString(mAuthorTextView.getText().toString().trim()));
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);
            outState.putParcelable(BKEY_TOC_ENTRY, mTocEntry);
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
         * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
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
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final List<TocEntry> mList;

        /**
         * Constructor.
         *
         * @param context caller context
         * @param objects the list
         */
        TocListAdapterForEditing(@NonNull final Context context,
                                 @NonNull final List<TocEntry> objects) {

            mInflater = LayoutInflater.from(context);
            mList = objects;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View view = mInflater.inflate(R.layout.row_edit_toc_entry, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            holder.setItem(mList.get(position));

            holder.titleView.setText(holder.getItem().getTitle());
            holder.authorView.setText(holder.getItem().getAuthor().getLabel());

            String year = holder.getItem().getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                holder.firstPublicationView.setText(getString(R.string.brackets, year));
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    /**
     * Holder pattern for each row.
     */
    private class Holder
            extends ViewHolderBase<TocEntry> {

        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;
        @NonNull
        final TextView firstPublicationView;

        Holder(@NonNull final View rowView) {
            super(rowView);

            if (mDeleteButton != null) {
                mDeleteButton.setOnClickListener(v -> {
                    mList.remove(item);
                    onListChanged();
                });
            }

            titleView = rowDetailsView.findViewById(R.id.title);
            authorView = rowDetailsView.findViewById(R.id.author);
            firstPublicationView = rowDetailsView.findViewById(R.id.year);

            // click -> edit
            rowDetailsView.setOnClickListener((v) -> editItem(getAdapterPosition()));

            // This is overkill.... user already has a delete button and can click-to-edit.
            // long-click -> menu
            rowDetailsView.setOnLongClickListener((v) -> onCreateContextMenu(getAdapterPosition()));
        }
    }
}

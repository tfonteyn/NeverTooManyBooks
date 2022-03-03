/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * The ISFDB direct interaction should be seen as temporary as this class
 * should not have to know about any specific search web site.
 * <p>
 * This is still not obsolete as the standard search engines can only return a
 * single book, and hence a single TOC. The interaction here with ISFDB allows
 * the user to reject the first (book)TOC found, and get the next one (etc...).
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookTocFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_TOC = TAG + ":rk:" + EditTocEntryDialogFragment.TAG;
    /** FragmentResultListener request key. */
    private static final String RK_CONFIRM_TOC = TAG + ":rk:" + ConfirmTocDialogFragment.TAG;

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mVm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @NonNull
    private final List<Edition> mIsfdbEditions = new ArrayList<>();
    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;
    /** the rows. A reference to the parcelled list in the Book. */
    private ArrayList<TocEntry> mList;
    /** View Binding. */
    private FragmentEditBookTocBinding mVb;
    /** The adapter for the list. */
    private TocListEditAdapter mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    private ExtArrayAdapter<String> mAuthorAdapter;
    /**
     * Stores the item position in the list while we're editing that item.
     * Editing is done using a dialog, so no need to store it more permanently.
     */
    @Nullable
    private Integer mEditPosition;
    /** Listen for the results of the entry edit-dialog. */
    private final EditTocEntryDialogFragment.Launcher mEditTocEntryLauncher =
            new EditTocEntryDialogFragment.Launcher(RK_EDIT_TOC) {
                @Override
                public void onResult(@NonNull final TocEntry tocEntry,
                                     final boolean isAnthology) {
                    onEntryUpdated(tocEntry, isAnthology);
                }
            };
    private EditBookTocViewModel mEditTocVm;
    private final ConfirmTocDialogFragment.Launcher mConfirmTocResultsLauncher =
            new ConfirmTocDialogFragment.Launcher(RK_CONFIRM_TOC) {
                @Override
                public void onResult(@NonNull final Book.ContentType contentType,
                                     @NonNull final List<TocEntry> tocEntries) {
                    onIsfdbDataConfirmed(contentType, tocEntries);
                }

                @Override
                public void searchNextEdition() {
                    searchIsfdb();
                }
            };
    private ExtPopupMenu mContextMenu;

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Toc;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEditTocVm = new ViewModelProvider(this).get(EditBookTocViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        mEditTocEntryLauncher.registerForFragmentResult(fm, this);
        mConfirmTocResultsLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookTocBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        final Context context = getContext();
        //noinspection ConstantConditions
        mVm.initFields(context, FragmentId.Toc, FieldGroup.Toc);

        mEditTocVm.onIsfdbEditions().observe(getViewLifecycleOwner(), this::onIsfdbEditions);
        mEditTocVm.onIsfdbBook().observe(getViewLifecycleOwner(), this::onIsfdbBook);

        mEditTocVm.onIsfdbEditionsCancelled().observe(getViewLifecycleOwner(), message ->
                message.getData().ifPresent(data -> Snackbar
                        .make(mVb.getRoot(), R.string.cancelled,
                              Snackbar.LENGTH_LONG).show()));
        mEditTocVm.onIsfdbEditionsFailure().observe(getViewLifecycleOwner(), message ->
                message.getData().ifPresent(data -> Snackbar
                        .make(mVb.getRoot(), R.string.warning_no_editions,
                              Snackbar.LENGTH_LONG).show()));

        mEditTocVm.onIsfdbBookCancelled().observe(getViewLifecycleOwner(), message ->
                message.getData().ifPresent(data -> Snackbar
                        .make(mVb.getRoot(), R.string.cancelled,
                              Snackbar.LENGTH_LONG).show()));
        mEditTocVm.onIsfdbBookFailure().observe(getViewLifecycleOwner(), message ->
                message.getData().ifPresent(data -> Snackbar
                        .make(mVb.getRoot(), R.string.warning_search_failed,
                              Snackbar.LENGTH_LONG).show()));

        mVb.tocList.addItemDecoration(new DividerItemDecoration(context, RecyclerView.VERTICAL));
        mVb.tocList.setHasFixedSize(true);

        mList = mVm.getBook().getToc();
        mListAdapter = new TocListEditAdapter(context, mList,
                                              vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.tocList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.tocList);

        mVb.rbIsCollection.setOnClickListener(v -> {
            mVm.getBook().setStage(EntityStage.Stage.Dirty);
            updateAnthology();
        });
        mVb.rbIsAnthology.setOnClickListener(v -> {
            mVm.getBook().setStage(EntityStage.Stage.Dirty);
            updateAnthology();
        });
        mVb.rbIsNeither.setOnClickListener(v -> {
            mVm.getBook().setStage(EntityStage.Stage.Dirty);
            updateAnthology();
        });

        mVb.btnAdd.setOnClickListener(v -> onAdd());

        mContextMenu = new ExtPopupMenu(context);
        final Resources res = getResources();
        final Menu menu = mContextMenu.getMenu();
        menu.add(Menu.NONE, R.id.MENU_EDIT, res.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.action_edit_ellipsis)
            .setIcon(R.drawable.ic_baseline_edit_24);
        menu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_baseline_delete_24);

        // ready for user input
        if (mVb.author.getVisibility() == View.VISIBLE) {
            mVb.author.requestFocus();
        } else {
            mVb.title.requestFocus();
        }
    }

    private void onEntryUpdated(@NonNull final TocEntry tocEntry,
                                final boolean isAnthology) {
        if (isAnthology) {
            mVb.rbIsAnthology.setChecked(true);
        }
        updateAnthology();

        if (mEditPosition == null) {
            // It's a new entry for the list.
            addNewEntry(tocEntry);

        } else {
            // It's an existing entry in the list, find it and update with the new data
            final TocEntry original = mList.get(mEditPosition);
            original.copyFrom(tocEntry);
            mListAdapter.notifyItemChanged(mEditPosition);
        }
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        populateTocBits(book.getContentType());

        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        //noinspection ConstantConditions
        fields.forEach(field -> field.setVisibility(global, getView(), false, false));
    }

    private void populateTocBits(@NonNull final Book.ContentType contentType) {
        switch (contentType) {
            case Collection:
                mVb.rbIsCollection.setChecked(true);
                break;

            case Anthology:
                mVb.rbIsAnthology.setChecked(true);
                break;

            case Book:
            default:
                mVb.rbIsNeither.setChecked(true);
                break;
        }

        updateAnthology();
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        if (mVb.rbIsCollection.isChecked()) {
            book.setContentType(Book.ContentType.Collection);
        } else if (mVb.rbIsAnthology.isChecked()) {
            book.setContentType(Book.ContentType.Anthology);
        } else {
            book.setContentType(Book.ContentType.Book);
        }
    }

    @Override
    public boolean hasUnfinishedEdits() {
        // We only check the title field; disregarding the author and first-publication fields.
        //noinspection ConstantConditions
        return !mVb.title.getText().toString().isEmpty();
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_EDIT) {
            editEntry(position);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            deleteEntry(position);
            return true;
        }
        return false;
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param position the position of the item
     */
    private void editEntry(final int position) {
        mEditPosition = position;

        final TocEntry tocEntry = mList.get(position);
        mEditTocEntryLauncher.launch(mVm.getBook(), tocEntry, mVb.rbIsAnthology.isChecked());
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final TocEntry tocEntry = mList.get(position);
        if (tocEntry.getId() == 0) {
            // It's a newly added entry, not saved; just remove it from the list.
            mList.remove(tocEntry);
            mListAdapter.notifyItemRemoved(position);

        } else {
            final Context context = getContext();
            if (tocEntry.getBookCount() == 1) {
                // The entry is saved, but only occurs in this single book.
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.action_delete)
                        .setMessage(context.getString(R.string.confirm_remove_toc_entry,
                                                      tocEntry.getTitle(),
                                                      tocEntry.getPrimaryAuthor()
                                                              .getLabel(context)))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(R.string.action_delete, (d, w) -> {
                            // We don't actually delete anything here as the user must be
                            // able to cancel the edit. So just remove it from the list.
                            mList.remove(tocEntry);
                            mListAdapter.notifyItemRemoved(position);
                        })
                        .create()
                        .show();

            } else {
                // The entry is saved and occurs in multiple books.
                // Offer deleting from this book only (i.e. 'remove') or an actual delete from
                // all books.
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.action_delete)
                        .setMessage(context.getString(R.string.confirm_scope_for_delete,
                                                      tocEntry.getTitle(),
                                                      tocEntry.getPrimaryAuthor()
                                                              .getLabel(context),
                                                      context.getString(R.string.btn_all_books)))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setNeutralButton(R.string.btn_this_book, (d, w) -> {
                            // We don't actually delete anything here as the user must be
                            // able to cancel the edit. So just remove it from the list.
                            mList.remove(tocEntry);
                            mListAdapter.notifyItemRemoved(position);
                        })
                        .setPositiveButton(R.string.btn_all_books, (d, w) -> {
                            // This is a hard delete and done immediately.
                            if (mVm.deleteTocEntry(context, tocEntry)) {
                                mList.remove(tocEntry);
                                mListAdapter.notifyItemRemoved(position);
                            }
                        })
                        .create()
                        .show();
            }
        }
    }

    /**
     * If the radiobutton 'anthology' is on, then we need to show the Author field.
     * Otherwise, hide it.
     */
    private void updateAnthology() {
        if (mVb.rbIsAnthology.isChecked()) {
            if (mAuthorAdapter == null) {
                //noinspection ConstantConditions
                mAuthorAdapter = new ExtArrayAdapter<>(
                        getContext(), R.layout.popup_dropdown_menu_item,
                        ExtArrayAdapter.FilterType.Diacritic, mVm.getAllAuthorNames());
                mVb.author.setAdapter(mAuthorAdapter);
            }

            //noinspection ConstantConditions
            final Author author = mVm.getPrimaryAuthor(getContext());
            mVb.author.setText(author.getLabel(getContext()));
            mVb.author.selectAll();
            mVb.lblAuthor.setVisibility(View.VISIBLE);
            mVb.author.setVisibility(View.VISIBLE);
            mVb.author.requestFocus();
        } else {
            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.author.setVisibility(View.GONE);
            mVb.title.requestFocus();
        }
    }

    /**
     * Add a new entry to the list based on the on-screen fields. (i.e. not from the edit-dialog).
     */
    private void onAdd() {
        // clear any previous error
        mVb.lblTitle.setError(null);

        //noinspection ConstantConditions
        final String title = mVb.title.getText().toString().trim();
        if (title.isEmpty()) {
            mVb.title.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Author author;
        if (mVb.rbIsAnthology.isChecked()) {
            author = Author.from(mVb.author.getText().toString().trim());
        } else {
            //noinspection ConstantConditions
            author = mVm.getPrimaryAuthor(getContext());
        }
        //noinspection ConstantConditions
        final TocEntry newTocEntry = new TocEntry(author, mVb.title.getText().toString().trim(),
                                                  mVb.firstPublication.getText().toString().trim()
        );
        addNewEntry(newTocEntry);
    }

    /**
     * Add a new entry to the list.
     * Called either by {@link #onAdd} or from the edit-dialog listener.
     *
     * @param tocEntry to add
     */
    private void addNewEntry(@NonNull final TocEntry tocEntry) {
        // see if it already exists
        //noinspection ConstantConditions
        mVm.fixId(getContext(), tocEntry);
        // and check it's not already in the list.
        if (mList.contains(tocEntry)) {
            mVb.lblTitle.setError(getString(R.string.warning_already_in_list));
        } else {
            mList.add(tocEntry);
            // clear the form for next entry and scroll to the new item
            if (mVb.rbIsAnthology.isChecked()) {
                final Author author = mVm.getPrimaryAuthor(getContext());
                mVb.author.setText(author.getLabel(getContext()));
                mVb.author.selectAll();
            }
            mVb.title.setText("");
            mVb.firstPublication.setText("");
            mVb.title.requestFocus();

            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.tocList.scrollToPosition(mListAdapter.getItemCount() - 1);
        }
    }

    /**
     * We got one or more editions from ISFDB.
     * <p>
     * Stores the urls locally as the user might want to try the next in line.
     */
    private void onIsfdbEditions(@NonNull final LiveDataEvent<TaskResult<List<Edition>>> message) {
        message.getData().ifPresent(data -> {
            final List<Edition> result = data.getResult();

            mIsfdbEditions.clear();
            if (result != null) {
                mIsfdbEditions.addAll(result);
            }
            searchIsfdb();
        });
    }

    private void onIsfdbBook(@NonNull final LiveDataEvent<TaskResult<Bundle>> message) {
        message.getData().ifPresent(data -> {
            final Bundle result = data.getResult();

            if (result == null) {
                Snackbar.make(mVb.getRoot(), R.string.warning_book_not_found,
                              Snackbar.LENGTH_LONG).show();
                return;
            }

            final Book book = mVm.getBook();

            // update the book with Series information that was gathered from the TOC
            final List<Series> series = result.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            if (series != null && !series.isEmpty()) {
                final ArrayList<Series> inBook = book.getSeries();
                // add, weeding out duplicates
                for (final Series s : series) {
                    if (!inBook.contains(s)) {
                        inBook.add(s);
                    }
                }
            }

            // update the book with the first publication date that was gathered from the TOC
            final String bookFirstPublication = result.getString(DBKey.DATE_FIRST_PUBLICATION);
            if (bookFirstPublication != null) {
                if (book.getString(DBKey.DATE_FIRST_PUBLICATION).isEmpty()) {
                    book.putString(DBKey.DATE_FIRST_PUBLICATION, bookFirstPublication);
                }
            }

            // finally the TOC itself:  display it for the user to approve
            // If there are more editions, the neutral button will allow to fetch the next one.
            mConfirmTocResultsLauncher.launch(result, !mIsfdbEditions.isEmpty());
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onIsfdbDataConfirmed(@NonNull final Book.ContentType contentType,
                                      @NonNull final Collection<TocEntry> tocEntries) {
        if (contentType != Book.ContentType.Book) {
            final Book book = mVm.getBook();
            book.setContentType(contentType);
            populateTocBits(book.getContentType());
        }

        // append the new data
        // can create duplicates if the user mixes manual input with automatic (or 2 x automatic...)
        // They will get weeded out when saved to the DAO
        mList.addAll(tocEntries);
        mListAdapter.notifyDataSetChanged();
    }

    private void searchIsfdb() {
        if (mIsfdbEditions.isEmpty()) {
            Snackbar.make(mVb.getRoot(), R.string.warning_no_editions,
                          Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mEditTocVm.searchEdition(mIsfdbEditions.get(0));
            mIsfdbEditions.remove(0);
        }
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     */
    public static class ConfirmTocDialogFragment
            extends DialogFragment {

        /** Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "ConfirmTocDialogFrag";
        private static final String BKEY_REQUEST_KEY = TAG + ":rk";
        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";
        /** FragmentResultListener request key to use for our response. */
        private String mRequestKey;
        private boolean mHasOtherEditions;
        private Book.ContentType mTocBitMask;
        private ArrayList<TocEntry> mTocEntries;

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                 "BKEY_REQUEST_KEY");
            mTocEntries = Objects.requireNonNull(args.getParcelableArrayList(Book.BKEY_TOC_LIST),
                                                 "BKEY_TOC_LIST");

            mTocBitMask = Book.ContentType.getType(args.getLong(DBKey.BITMASK_TOC));
            mHasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS, false);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final View rootView = getLayoutInflater()
                    .inflate(R.layout.dialog_toc_confirm, null, false);
            final TextView contentView = rootView.findViewById(R.id.content);

            final boolean hasToc = mTocEntries != null && !mTocEntries.isEmpty();
            if (hasToc) {
                //noinspection ConstantConditions
                final StringBuilder message =
                        new StringBuilder(getString(R.string.warning_toc_confirm))
                                .append("\n\n")
                                .append(mTocEntries.stream()
                                                   .map(entry -> entry.getLabel(getContext()))
                                                   .collect(Collectors.joining(", ")));
                contentView.setText(message);

            } else {
                contentView.setText(R.string.error_auto_toc_population_failed);
            }

            //noinspection ConstantConditions
            final AlertDialog dialog =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_baseline_warning_24)
                            .setView(rootView)
                            .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 (d, which) -> Launcher.setResult(this, mRequestKey,
                                                                  mTocBitMask, mTocEntries));
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (mHasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_retry),
                                 (d, which) -> Launcher.searchNextEdition(this, mRequestKey));
            }

            return dialog;
        }

        public abstract static class Launcher
                extends FragmentLauncherBase {

            private static final String SEARCH_NEXT_EDITION = "searchNextEdition";
            private static final String TOC_BIT_MASK = "tocBitMask";
            private static final String TOC_LIST = "tocEntries";

            public Launcher(@NonNull final String requestKey) {
                super(requestKey);
            }

            @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
            static void setResult(@NonNull final Fragment fragment,
                                  @NonNull final String requestKey,
                                  @NonNull final Book.ContentType tocBitMask,
                                  @NonNull final ArrayList<TocEntry> tocEntries) {
                final Bundle result = new Bundle(2);
                result.putLong(TOC_BIT_MASK, tocBitMask.value);
                result.putParcelableArrayList(TOC_LIST, tocEntries);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
            static void searchNextEdition(@NonNull final Fragment fragment,
                                          @NonNull final String requestKey) {
                final Bundle result = new Bundle(1);
                result.putBoolean(SEARCH_NEXT_EDITION, true);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            /**
             * Launch the dialog.
             *
             * @param bookData         the result of the search
             * @param hasOtherEditions flag
             */
            public void launch(@NonNull final Bundle bookData,
                               final boolean hasOtherEditions) {

                bookData.putString(BKEY_REQUEST_KEY, RK_CONFIRM_TOC);
                bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);

                final DialogFragment fragment = new ConfirmTocDialogFragment();
                fragment.setArguments(bookData);
                fragment.show(mFragmentManager, TAG);
            }

            @Override
            public void onFragmentResult(@NonNull final String requestKey,
                                         @NonNull final Bundle result) {
                if (result.getBoolean(SEARCH_NEXT_EDITION)) {
                    searchNextEdition();
                } else {
                    onResult(Book.ContentType.getType(result.getLong(TOC_BIT_MASK)),
                             Objects.requireNonNull(result.getParcelableArrayList(TOC_LIST),
                                                    TOC_LIST));
                }
            }

            /**
             * Callback handler.
             *
             * @param contentType bit flags
             * @param tocEntries  the list of entries
             */
            public abstract void onResult(@NonNull final Book.ContentType contentType,
                                          @NonNull List<TocEntry> tocEntries);

            /**
             * Callback handler.
             */
            public abstract void searchNextEdition();
        }
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;
        @NonNull
        final TextView firstPublicationView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            titleView = rowDetailsView.findViewById(R.id.title);
            authorView = rowDetailsView.findViewById(R.id.author);
            firstPublicationView = rowDetailsView.findViewById(R.id.year);
        }
    }

    private class TocListEditAdapter
            extends RecyclerViewAdapterBase<TocEntry, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of TocEntry's
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        TocListEditAdapter(@NonNull final Context context,
                           @NonNull final List<TocEntry> items,
                           @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_toc_entry, parent, false);
            final Holder holder = new Holder(view);

            // click -> edit
            holder.rowDetailsView.setOnClickListener(
                    v -> editEntry(holder.getBindingAdapterPosition()));

            holder.rowDetailsView.setOnLongClickListener(v -> {
                mContextMenu.showAsDropDown(v, menuItem ->
                        onMenuItemSelected(menuItem, holder.getBindingAdapterPosition()));
                return true;
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final TocEntry tocEntry = getItem(position);

            holder.titleView.setText(tocEntry.getTitle());
            holder.authorView.setText(tocEntry.getPrimaryAuthor().getLabel(getContext()));

            final PartialDate date = tocEntry.getFirstPublicationDate();
            if (date.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                // cut the date to just the year.
                holder.firstPublicationView.setText(getString(R.string.brackets,
                                                              String.valueOf(date.getYearValue())));
            }
        }

        @Override
        protected void onDelete(final int adapterPosition,
                                @NonNull final TocEntry item) {
            deleteEntry(adapterPosition);
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(R.id.MENU_POPULATE_TOC_FROM_ISFDB, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0,
                     R.string.isfdb_menu_populate_toc);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {

            if (menuItem.getItemId() == R.id.MENU_POPULATE_TOC_FROM_ISFDB) {
                final Book book = mVm.getBook();
                final long isfdbId = book.getLong(DBKey.SID_ISFDB);
                if (isfdbId != 0) {
                    Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    mEditTocVm.searchBook(isfdbId);
                    return true;
                }

                final String isbnStr = book.getString(DBKey.KEY_ISBN);
                if (!isbnStr.isEmpty()) {
                    final ISBN isbn = ISBN.createISBN(isbnStr);
                    if (isbn.isValid(true)) {
                        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                                      Snackbar.LENGTH_LONG).show();
                        mEditTocVm.searchByIsbn(isbn);
                        return true;
                    }
                }
                Snackbar.make(mVb.getRoot(), R.string.warning_requires_isbn,
                              Snackbar.LENGTH_LONG).show();
                return true;
            }
            return false;
        }
    }
}

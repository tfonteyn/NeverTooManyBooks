/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.AuthorWorksModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.widgets.fastscroller.FastScroller;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 *
 * <strong>Note:</strong> when an item is click we start a <strong>NEW</strong> Activity.
 * Doing a 'back' will then get the user back here.
 * This is intentionally different from the behaviour of {@link FTSSearchActivity}.
 */
public class AuthorWorksFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "AuthorWorksFragment";

    /** Optional. Show the TOC. Defaults to {@code true}. */
    public static final String BKEY_WITH_TOC = TAG + ":tocs";
    /** Optional. Show the books. Defaults to {@code true}. */
    public static final String BKEY_WITH_BOOKS = TAG + ":books";

    /** The ViewModel. */
    private AuthorWorksModel mModel;
    /** The Adapter. */
    private TocAdapter mAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(AuthorWorksModel.class);
        //noinspection ConstantConditions
        mModel.init(getContext(), requireArguments());
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
            Log.d(getClass().getName(), "onAttachFragment: " + childFragment.getTag());
        }
        super.onAttachFragment(childFragment);

        if (childFragment instanceof MenuPickerDialogFragment) {
            ((MenuPickerDialogFragment) childFragment).setListener(this::onContextItemSelected);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_author_works, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        updateScreenTitle();

        final RecyclerView listView = view.findViewById(R.id.author_works);
        listView.setHasFixedSize(true);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        listView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        listView.addItemDecoration(
                new DividerItemDecoration(context, linearLayoutManager.getOrientation()));

        FastScroller.init(listView);

        mAdapter = new TocAdapter(context);
        listView.setAdapter(mAdapter);

        if (savedInstanceState == null) {
            TipManager.display(context, R.string.tip_authors_works, null);
        }
    }

    private void updateScreenTitle() {
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            //noinspection ConstantConditions
            actionBar.setTitle(mModel.getScreenTitle(getContext()));
            actionBar.setSubtitle(mModel.getScreenSubtitle());
        }
    }

//    @Override
//    @CallSuper
//    public void onResume() {
//        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
//            Log.d(TAG, "ENTER|onResume");
//        }
//        super.onResume();
//        if (getActivity() instanceof BaseActivity) {
//            BaseActivity activity = (BaseActivity) getActivity();
//            if (activity.maybeRecreate()) {
//                return;
//            }
//        }
//        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
//            Log.d(TAG, "EXIT|onResume");
//        }
//    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.author_works, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        MenuItem all = menu.findItem(R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES);
        // hide if a specific INITIAL bookshelf was set.
        all.setVisible(mModel.getBookshelfId() != Bookshelf.ALL_BOOKS);
        // check if the user overrules the initial
        all.setChecked(mModel.isAllBookshelves());
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        switch (item.getItemId()) {
            case R.id.MENU_AUTHOR_WORKS_ALL: {
                item.setChecked(true);
                mModel.reloadTocEntries(true, true);
                mAdapter.notifyDataSetChanged();
                return true;
            }
            case R.id.MENU_AUTHOR_WORKS_TOC: {
                item.setChecked(true);
                mModel.reloadTocEntries(true, false);
                mAdapter.notifyDataSetChanged();
                return true;
            }
            case R.id.MENU_AUTHOR_WORKS_BOOKS: {
                item.setChecked(true);
                mModel.reloadTocEntries(false, true);
                mAdapter.notifyDataSetChanged();
                return true;
            }

            case R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES: {
                final boolean checked = !item.isChecked();
                item.setChecked(checked);
                mModel.setAllBookshelves(checked);
                mModel.reloadTocEntries();
                mAdapter.notifyDataSetChanged();
                updateScreenTitle();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        if (MenuPicker.__COMPILE_TIME_USE_FRAGMENT) {
            onCreateContextMenu2(position);
            return;
        }

        final Resources r = getResources();
        final TocEntry item = mModel.getTocEntries().get(position);

        //noinspection ConstantConditions
        final Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_DELETE,
                 r.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_delete);

        final String title = item.getLabel(getContext());
        new MenuPicker(getContext(), title, menu, position, this::onContextItemSelected)
                .show();
    }

    private void onCreateContextMenu2(final int position) {
        final Resources r = getResources();
        final TocEntry item = mModel.getTocEntries().get(position);

        final ArrayList<MenuPickerDialogFragment.Pick> menu = new ArrayList<>();
        menu.add(new MenuPickerDialogFragment.Pick(R.id.MENU_DELETE,
                                                   r.getInteger(R.integer.MENU_ORDER_DELETE),
                                                   getString(R.string.action_delete),
                                                   R.drawable.ic_delete));

        //noinspection ConstantConditions
        final String title = item.getLabel(getContext());
        MenuPickerDialogFragment.newInstance(title, null, menu, position)
                                .show(getChildFragmentManager(), MenuPickerDialogFragment.TAG);
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {
        final TocEntry item = mModel.getTocEntries().get(position);

        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem) {
            case R.id.MENU_DELETE:
                switch (item.getType()) {
                    case Book:
                        //noinspection ConstantConditions
                        StandardDialogs.deleteBook(getContext(), item.getLabel(getContext()),
                                                   item.getAuthors(), () -> {
                                    mModel.delTocEntry(getContext(), item);
                                    mAdapter.notifyItemRemoved(position);
                                });
                        return true;

                    case Toc:
                        //noinspection ConstantConditions
                        StandardDialogs.deleteTocEntry(getContext(), item, () -> {
                            mModel.delTocEntry(getContext(), item);
                            mAdapter.notifyItemRemoved(position);
                        });
                        return true;
                }
                break;

            default:
                return false;
        }

        return false;
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     *
     * @param item the TocEntry or Book
     */
    private void gotoBook(@NonNull final TocEntry item) {
        switch (item.getType()) {
            case Book: {
                // open new activity to show the book, 'back' will return to this one.
                Intent intent = new Intent(getContext(), BookDetailsActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, item.getId());
                startActivity(intent);
                break;
            }
            case Toc: {
                final ArrayList<Long> bookIdList = mModel.getBookIds(item);
                if (bookIdList.size() == 1) {
                    // open new activity to show the book, 'back' will return to this one.
                    Intent intent = new Intent(getContext(), BookDetailsActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, bookIdList.get(0));
                    startActivity(intent);
                    break;

                } else {
                    // multiple books, open the list as a NEW ACTIVITY
                    Intent intent = new Intent(getContext(), BooksOnBookshelf.class)
                            .putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList)
                            // Open the list expanded, as otherwise you end up with
                            // the author as a single line, and no books shown at all,
                            // which can be quite confusing to the user.
                            .putExtra(BooksOnBookshelfModel.BKEY_LIST_STATE,
                                      BooklistBuilder.PREF_REBUILD_ALWAYS_EXPANDED);


                    startActivity(intent);
                    break;
                }
            }
        }
    }

    /**
     * Holder pattern for {@link TocAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView titleView;
        @Nullable
        final TextView authorView;
        @Nullable
        final TextView firstPublicationView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title);
            // optional
            authorView = itemView.findViewById(R.id.author);
            // optional
            firstPublicationView = itemView.findViewById(R.id.year);
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<Holder>
            implements FastScroller.PopupTextProvider {

        /** Caching the inflater. */
        private final LayoutInflater mInflater;
        /** Row indicator icon. */
        private final ColorStateList mDrawableOn;
        /** Row indicator icon. */
        private final ColorStateList mDrawableOff;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        TocAdapter(@NonNull final Context context) {
            super();
            mInflater = LayoutInflater.from(context);
            mDrawableOn = context.getResources().getColorStateList(
                    AttrUtils.getResId(context, R.attr.appIconTint),
                    context.getTheme());
            mDrawableOff = context.getResources().getColorStateList(
                    android.R.color.transparent,
                    context.getTheme());
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final TocEntry.Type type = TocEntry.Type.get((char) viewType);
            final View itemView;
            switch (type) {
                case Toc:
                    itemView = mInflater.inflate(R.layout.row_toc_entry, parent, false);
                    break;
                case Book:
                    itemView = mInflater.inflate(R.layout.row_toc_entry_book, parent, false);
                    break;
                default:
                    throw new UnexpectedValueException(viewType);
            }

            return new Holder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final TocEntry tocEntry = mModel.getTocEntries().get(position);

            final Context context = getContext();

            //noinspection ConstantConditions
            holder.titleView.setText(tocEntry.getLabel(context));

            // optional
            if (holder.authorView != null) {
                holder.authorView.setText(tocEntry.getAuthor().getLabel(context));
            }
            // optional
            if (holder.firstPublicationView != null) {
                String date = tocEntry.getFirstPublication();
                // "< 4" covers empty and illegal dates
                if (date.length() < 4) {
                    holder.firstPublicationView.setVisibility(View.GONE);
                } else {
                    // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
                    // so cut the date to just the year.
                    String fp = context.getString(R.string.brackets, date.substring(0, 4));
                    holder.firstPublicationView.setText(fp);
                    holder.firstPublicationView.setVisibility(View.VISIBLE);
                }
            }

            if (tocEntry.getType().equals(TocEntry.Type.Toc)) {
                if (tocEntry.getBookCount() > 1) {
                    holder.titleView.setCompoundDrawableTintList(mDrawableOn);
                } else {
                    holder.titleView.setCompoundDrawableTintList(mDrawableOff);
                }
            }

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(tocEntry));

            holder.itemView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getBindingAdapterPosition());
                return true;
            });
        }

        @Override
        public int getItemViewType(final int position) {
            return mModel.getTocEntries().get(position).getType().getChar();
        }

        @Override
        public int getItemCount() {
            return mModel.getTocEntries().size();
        }

        @NonNull
        @Override
        public String[] getPopupText(final int position) {
            final String title = mModel.getTocEntries().get(position)
                                       .getLabel(mInflater.getContext());
            return new String[]{title};
        }
    }
}

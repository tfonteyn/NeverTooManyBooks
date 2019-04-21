package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.widgets.SectionIndexerV2;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 * <p>
 * ENHANCE: we currently only display the TOC entries. We should add books without TOC as well.
 * i.e. the toc of a book without a toc, is the book title itself. (makes sense?)
 */
public class AuthorWorksFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = AuthorWorksFragment.class.getSimpleName();

    private DBA mDb;

    private ArrayList<TocEntry> mTocEntries;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_author_works, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        long authorId = getArguments().getLong(DBDefinitions.KEY_ID, 0);

        //noinspection ConstantConditions
        mDb = new DBA(getContext());
        final Author author = mDb.getAuthor(authorId);
        // the list of TOC entries.
        //noinspection ConstantConditions
        mTocEntries = mDb.getTocEntryByAuthor(author, true);

        //noinspection ConstantConditions
        getActivity().setTitle(author.getLabel() + '[' + mTocEntries.size() + ']');

//        // for testing.
//        for (int i=0; i < 300; i++) {
//            mTocEntries.add(new TocEntry(author,"blah " + i,"1978"));
//        }

        //noinspection ConstantConditions
        RecyclerView listView = getView().findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        TocAdapter adapter = new TocAdapter();
        listView.setAdapter(adapter);
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     */
    private void gotoBook(@NonNull final TocEntry item) {
        Intent intent;
        switch (item.getType()) {
            case 'T':
                // see note on dba method about Integer vs. Long
                final ArrayList<Integer> books = mDb.getBookIdsByTocEntry(item.getId());
                intent = new Intent(getContext(), BooksOnBookshelf.class)
                        // clear the back-stack. We want to keep BooksOnBookshelf on top
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // bring up list, filtered on the book id's
                        .putExtra(UniqueId.BKEY_ID_LIST, books);
                startActivity(intent);
                break;

            case 'B':
                intent = new Intent(getContext(), BookDetailsActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, item.getId());
                startActivity(intent);
                break;

            default:
                throw new IllegalArgumentException("type=" + item.getType());
        }

        //noinspection ConstantConditions
        getActivity().finish();
    }

    /**
     * Holder pattern for each row.
     */
    private static class TocViewHolder
            extends RecyclerView.ViewHolder {

        /** It's a book. */
        private static Drawable sBookIndicator;
        /** It's not a book. e.g. a short story... */
        private static Drawable sStoryIndicator;

        @NonNull
        final TextView titleView;
        @Nullable
        final TextView authorView;
        @Nullable
        final TextView firstPublicationView;

        TocViewHolder(@NonNull final View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title);
            // optional
            authorView = itemView.findViewById(R.id.author);
            // optional
            firstPublicationView = itemView.findViewById(R.id.year);

            if (sBookIndicator == null) {
                sBookIndicator = itemView.getContext().getDrawable(R.drawable.ic_book);
                sStoryIndicator = itemView.getContext().getDrawable(R.drawable.ic_lens);
            }
        }

        void bind(@NonNull final TocEntry item) {
            // decorate the row depending on toc entry or actual book
            switch (item.getType()) {
                case 'T':
                    titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            sStoryIndicator, null, null, null);
                    break;

                case 'B':
                    titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            sBookIndicator, null, null, null);
                    break;

                default:
                    throw new IllegalArgumentException("type=" + item.getType());
            }

            titleView.setText(item.getTitle());
            // optional
            if (authorView != null) {
                authorView.setText(item.getAuthor().getLabel());
            }
            // optional
            if (firstPublicationView != null) {
                String date = item.getFirstPublication();
                if (date.isEmpty()) {
                    firstPublicationView.setVisibility(View.GONE);
                } else {
                    firstPublicationView.setVisibility(View.VISIBLE);
                    firstPublicationView.setText(
                            firstPublicationView.getContext().getString(R.string.brackets, date));
                }
            }
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<TocViewHolder>
            implements SectionIndexerV2 {

        @NonNull
        @Override
        public TocViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                final int viewType) {

            View itemView = getLayoutInflater().inflate(R.layout.row_toc_entry, parent, false);
            return new TocViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull final TocViewHolder holder,
                                     final int position) {
            holder.bind(mTocEntries.get(position));
            holder.itemView.setOnClickListener(v -> gotoBook(mTocEntries.get(position)));
        }

        @Override
        public int getItemCount() {
            return mTocEntries.size();
        }

        @Nullable
        @Override
        public String[] getSectionTextForPosition(final int position) {
            // make sure it's still in range.
            int index = MathUtils.clamp(position, 0, mTocEntries.size() - 1);

            return new String[]{mTocEntries.get(index).getTitle().substring(0, 1).toUpperCase()};
        }
    }
}

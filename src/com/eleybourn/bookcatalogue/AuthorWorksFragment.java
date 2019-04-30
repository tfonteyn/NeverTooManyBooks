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
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.widgets.SectionIndexerV2;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 */
public class AuthorWorksFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = AuthorWorksFragment.class.getSimpleName();

    /** Optional. Also show the authors book. Defaults to {@code true}. */
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_WITH_BOOKS = TAG + ":withBooks";

    private DBA mDb;

    private AuthorWorksModel mModel;

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
        boolean withBooks = getArguments().getBoolean(BKEY_WITH_BOOKS, true);

        mDb = new DBA();

        mModel = ViewModelProviders.of(this).get(AuthorWorksModel.class);
        mModel.init(mDb, authorId, withBooks);

        String title = mModel.getAuthor().getLabel() + '[' + mModel.getTocEntries().size() + ']';
        //noinspection ConstantConditions
        getActivity().setTitle(title);

        //noinspection ConstantConditions
        RecyclerView listView = getView().findViewById(android.R.id.list);
        listView.setHasFixedSize(true);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        TocAdapter adapter = new TocAdapter();
        listView.setAdapter(adapter);
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     */
    private void gotoBook(@NonNull final TocEntry item) {
        Intent intent;
        switch (item.getTocType()) {
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
                throw new IllegalArgumentException("type=" + item.getTocType());
        }

        //noinspection ConstantConditions
        getActivity().finish();
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        /** Icon to show for a book. */
        private static Drawable sBookIndicator;
        /** Icon to show for not a book. e.g. a short story... */
        private static Drawable sStoryIndicator;

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

            // static initializer
            if (sBookIndicator == null) {
                sBookIndicator = itemView.getContext().getDrawable(R.drawable.ic_book);
                sStoryIndicator = itemView.getContext().getDrawable(R.drawable.ic_lens);
            }
        }

        void bind(@NonNull final TocEntry item) {
            // decorate the row depending on toc entry or actual book
            switch (item.getTocType()) {
                case 'T':
                    titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            sStoryIndicator, null, null, null);
                    break;

                case 'B':
                    titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            sBookIndicator, null, null, null);
                    break;

                default:
                    throw new IllegalArgumentException("type=" + item.getTocType());
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
            extends RecyclerView.Adapter<Holder>
            implements SectionIndexerV2 {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View itemView = getLayoutInflater().inflate(R.layout.row_toc_entry, parent, false);
            return new Holder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            TocEntry tocEntry = mModel.getTocEntries().get(position);
            holder.bind(tocEntry);
            // click -> open book details.
            holder.itemView.setOnClickListener(v -> gotoBook(tocEntry));
        }

        @Override
        public int getItemCount() {
            return mModel.getTocEntries().size();
        }

        @Nullable
        @Override
        public String[] getSectionTextForPosition(final int position) {
            // make sure it's still in range.
            int index = MathUtils.clamp(position, 0, mModel.getTocEntries().size() - 1);

            return new String[]{mModel.getTocEntries().get(index).getTitle().substring(0,
                                                                                       1).toUpperCase()};
        }
    }
}

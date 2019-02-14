package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.EditBookActivity;
import com.eleybourn.bookcatalogue.EditBookFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.BindableItemCursor;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Event;
import com.eleybourn.bookcatalogue.tasks.taskqueue.EventsCursor;
import com.eleybourn.bookcatalogue.tasks.taskqueue.GoodreadsTask;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class SendBooksTask
        extends GoodreadsTask {

    private static final long serialVersionUID = -8519158637447641604L;
    /** wait time before declaring network failure. */
    private static final int FIVE_MINUTES = 300;
    /** Number of books with no ISBN. */
    int mNoIsbn;
    /** Number of books that had ISBN but could not be found. */
    int mNotFound;
    /** Number of books successfully sent. */
    int mSent;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    SendBooksTask(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return <tt>false</tt> to requeue, <tt>true</tt> for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {
        boolean result = false;

        // ENHANCE: Work out a way of checking if GR site is up
        //if (!Utils.hostIsAvailable(context, "www.goodreads.com")) {
        //  throw new IOException();
        //}

        if (NetworkUtils.isNetworkAvailable(context)) {
            GoodreadsManager grManager = new GoodreadsManager();
            // Ensure we are allowed
            if (grManager.hasValidCredentials()) {
                result = send(queueManager, context, grManager);
            } else {
                Logger.error("no valid credentials");
            }
        } else {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
            Logger.error("network not available");
        }

        return result;
    }

    /**
     * @return <tt>false</tt> to requeue, <tt>true</tt> for success
     */
    protected abstract boolean send(@NonNull final QueueManager queueManager,
                                    @NonNull final Context context,
                                    @NonNull final GoodreadsManager grManager);

    /**
     * Try to export one book.
     *
     * @return <tt>false</tt> on failure, <tt>true</tt> on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsManager grManager,
                        @NonNull final DBA db,
                        @NonNull final BookCursorRow bookCursorRow) {

        GoodreadsManager.ExportDisposition disposition;
        Exception exportException = null;
        try {
            disposition = grManager.sendOneBook(db, bookCursorRow);
        } catch (BookNotFoundException | AuthorizationException | IOException e) {
            disposition = GoodreadsManager.ExportDisposition.error;
            exportException = e;
        }

        long bookId = bookCursorRow.getId();

        // Handle the result
        switch (disposition) {
            case sent:
                // Record the change
                db.setGoodreadsSyncDate(bookId);
                mSent++;
                break;

            case noIsbn:
                storeEvent(new GrNoIsbnEvent(context, bookId));
                mNoIsbn++;
                break;

            case notFound:
                storeEvent(new GrNoMatchEvent(context, bookId));
                mNotFound++;
                break;

            case error:
                setException(exportException);
                queueManager.updateTask(this);
                return false;

            case networkError:
                // Only wait 5 minutes on network errors.
                if (getRetryDelay() > FIVE_MINUTES) {
                    setRetryDelay(FIVE_MINUTES);
                }
                queueManager.updateTask(this);
                return false;
        }
        return true;
    }

    /**
     * All Event objects resulting from sending books to goodreads.
     *
     * @author Philip Warner
     */
    private static class GrSendBookEvent
            extends Event {

        /**
         * Retry sending a book to goodreads.
         */
        @Nullable
        static final View.OnClickListener mRetryButtonListener = new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                BookEventHolder holder = (BookEventHolder) v.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
                holder.event.retry();
            }
        };
        private static final long serialVersionUID = 8056090158313083738L;
        private final long mBookId;

        /**
         * Constructor.
         *
         * @param bookId    ID of related book.
         * @param messageId Description of this event.
         */
        GrSendBookEvent(@NonNull final Context context,
                        final long bookId,
                        @StringRes final int messageId) {
            super(context.getResources().getString(messageId));
            mBookId = bookId;
        }

        /**
         * Get the related Book ID.
         *
         * @return ID of related book.
         */
        public long getBookId() {
            return mBookId;
        }

        /**
         * Resubmit this book and delete this event.
         */
        public void retry() {
            QueueManager qm = QueueManager.getQueueManager();
            SendOneBookTask task = new SendOneBookTask(mBookId);
            // TODO: MAKE IT USE THE SAME QUEUE? Why????
            qm.enqueueTask(task, QueueManager.QUEUE_SMALL_JOBS);
            qm.deleteEvent(getId());
        }

        /**
         * Return a view capable of displaying basic book event details,
         * ideally usable by all BookEvent subclasses.
         * This method also prepares the BookEventHolder object for the View.
         */
        @Override
        public View newListItemView(@NonNull final Context context,
                                    @NonNull final BindableItemCursor cursor,
                                    @NonNull final ViewGroup parent) {
            View view = LayoutInflater.from(context)
                                      .inflate(R.layout.row_book_event_info, parent, false);
            view.setTag(R.id.TAG_EVENT, this);
            BookEventHolder holder = new BookEventHolder();
            holder.event = this;
            holder.rowId = cursor.getId();

            holder.authorView = view.findViewById(R.id.author);
            holder.buttonView = view.findViewById(R.id.checked);
            holder.dateView = view.findViewById(R.id.date);
            holder.errorView = view.findViewById(R.id.error);
            holder.retryView = view.findViewById(R.id.retry);
            holder.titleView = view.findViewById(R.id.title);

            view.setTag(R.id.TAG_BOOK_EVENT_HOLDER, holder);
            holder.buttonView.setTag(R.id.TAG_BOOK_EVENT_HOLDER, holder);
            holder.retryView.setTag(R.id.TAG_BOOK_EVENT_HOLDER, holder);

            return view;
        }

        /**
         * Display the related book details in the passed View object.
         */
        @Override
        public void bindView(@NonNull final View view,
                             @NonNull final Context context,
                             @NonNull final BindableItemCursor cursor,
                             @NonNull final DBA db) {
            final EventsCursor eventsCursor = (EventsCursor) cursor;

            // Update event info binding; the Views in the holder are unchanged,
            // but when it is reused the Event and ID will change.
            BookEventHolder holder = (BookEventHolder) view.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
            holder.event = this;
            holder.rowId = eventsCursor.getId();

            ArrayList<Author> authors = db.getAuthorsByBookId(mBookId);
            String author;
            if (!authors.isEmpty()) {
                author = authors.get(0).getDisplayName();
                if (authors.size() > 1) {
                    author = author + ' ' + BookCatalogueApp.getResString(R.string.and_others);
                }
            } else {
                author = context.getString(R.string.unknown_uc);
            }

            String title = db.getBookTitle(mBookId);
            if (title == null) {
                title = context.getString(R.string.warning_book_was_deleted_uc);
            }

            holder.titleView.setText(title);
            holder.authorView.setText(
                    String.format(context.getString(R.string.lbl_by_authors), author));
            holder.errorView.setText(getDescription());

            String date = String.format(context.getString(R.string.gr_tq_occurred_at),
                                        DateUtils.toPrettyDateTime(eventsCursor.getEventDate()));
            holder.dateView.setText(date);

            holder.retryView.setVisibility(View.GONE);

            holder.buttonView.setChecked(eventsCursor.isSelected());
            holder.buttonView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull final CompoundButton buttonView,
                                             final boolean isChecked) {
                    BookEventHolder holder = (BookEventHolder)
                            buttonView.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
                    eventsCursor.setSelected(holder.rowId, isChecked);
                }
            });

            // get book details
            String isbn = db.getBookIsbn(mBookId);
            // Hide parts of view based on current book having an isbn or not..
            if (isbn != null && !isbn.isEmpty()) {
                holder.retryView.setVisibility(View.VISIBLE);
                holder.retryView.setTag(this);
                holder.retryView.setOnClickListener(mRetryButtonListener);
                return;
            }
            holder.retryView.setVisibility(View.GONE);
        }

        /**
         * Add ContextDialogItems relevant for the specific book the selected View is
         * associated with.
         * Subclass can override this and add items at end/start or just replace these completely.
         */
        @Override
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final AdapterView<?> parent,
                                        @NonNull final View view,
                                        final int position,
                                        final long id,
                                        @NonNull final List<ContextDialogItem> items,
                                        @NonNull final DBA db) {

            // EDIT BOOK
            items.add(new ContextDialogItem(
                    context.getString(R.string.menu_edit_book),
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GrSendBookEvent event =
                                        (GrSendBookEvent) view.getTag(R.id.TAG_EVENT);
                                Intent intent = new Intent(context, EditBookActivity.class);
                                intent.putExtra(UniqueId.KEY_ID, event.getBookId());
                                intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB,
                                                EditBookFragment.TAB_EDIT);
                                context.startActivity(intent);
                            } catch (RuntimeException ignore) {
                                // not a book event?
                            }
                        }
                    }));

            // ENHANCE: Reinstate goodreads search when goodreads work.editions API is available
//            // SEARCH GOODREADS
//            items.add(new ContextDialogItem(
//                    context.getString(R.string.gr_search_goodreads),
//                    new Runnable() {
//                        @Override
//                        public void run() {
//                            BookEventHolder holder = (BookEventHolder)
//                                    view.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
//                            Intent intent = new Intent(context,
//                                                       GoodreadsSearchCriteriaActivity.class);
//                            intent.putExtra(
//                                    GoodreadsSearchCriteriaActivity.REQUEST_BKEY_BOOK_ID,
//                                    holder.event.getId());
//                            context.startActivity(intent);
//                        }
//                    }));

            // DELETE EVENT
            items.add(
                    new ContextDialogItem(
                            context.getString(R.string.gr_tq_menu_delete_event),
                            new Runnable() {
                                @Override
                                public void run() {
                                    QueueManager.getQueueManager().deleteEvent(id);
                                }
                            }));

            // RETRY EVENT
            String isbn = db.getBookIsbn(mBookId);
            if (isbn != null && !isbn.isEmpty()) {
                items.add(
                        new ContextDialogItem(
                                context.getString(R.string.retry),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            GrSendBookEvent event =
                                                    (GrSendBookEvent) view.getTag(R.id.TAG_EVENT);
                                            event.retry();
                                            QueueManager.getQueueManager().deleteEvent(id);
                                        } catch (RuntimeException ignore) {
                                            // not a book event?
                                        }
                                    }
                                }));
            }
        }

        /**
         * Class to implement the 'holder' model for view we create.
         */
        static class BookEventHolder {

            long rowId;
            GrSendBookEvent event;
            TextView titleView;
            TextView authorView;
            TextView errorView;
            TextView dateView;
            Button retryView;
            CompoundButton buttonView;
        }

    }

    /**
     * Exception indicating the book's ISBN could not be found at Goodreads.
     */
    private static class GrNoMatchEvent
            extends GrSendBookEvent
            implements HintManager.HintOwner {

        private static final long serialVersionUID = -7684121345325648066L;

        GrNoMatchEvent(@NonNull final Context context,
                       final long bookId) {
            super(context, bookId, R.string.warning_no_matching_book_found);
        }

        @Override
        @StringRes
        public int getHint() {
            return R.string.gr_explain_goodreads_no_match;
        }

    }

    /**
     * Exception indicating the book's ISBN was blank.
     */
    private static class GrNoIsbnEvent
            extends GrSendBookEvent
            implements HintManager.HintOwner {

        private static final long serialVersionUID = 7260496259505914311L;

        GrNoIsbnEvent(@NonNull final Context context,
                      final long bookId) {
            super(context, bookId, R.string.warning_no_isbn_stored_for_book);
        }

        @Override
        @StringRes
        public int getHint() {
            return R.string.gr_explain_goodreads_no_isbn;
        }

    }
}

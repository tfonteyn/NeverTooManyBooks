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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BooleanIndicatorAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

public class ShowBookDetailsViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    private final MutableLiveData<BookMessage> mBookLd = new MutableLiveData<>();
    /** Database Access. */
    private BookDao mBookDao;
    /** <strong>Optionally</strong> passed. */
    @Nullable
    private ListStyle mStyle;
    private Book mBook;

    /** The fields used. */
    private Fields mFieldsMap;

    private boolean mUseLoanee;
    private boolean mUseToc;

    /**
     * <ul>
     * <li>{@link DBKey#PK_ID}  book id</li>
     * <li>{@link Entity#BKEY_DATA_MODIFIED}      boolean</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        if (mBook != null) {
            mResultIntent.putExtra(DBKey.PK_ID, mBook.getId());
        }
        return mResultIntent;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {

        if (mBookDao == null) {
            mBookDao = ServiceLocator.getInstance().getBookDao();

            final String styleUuid = args.getString(ListStyle.BKEY_STYLE_UUID);
            if (styleUuid != null) {
                mStyle = ServiceLocator.getInstance().getStyles()
                                       .getStyleOrDefault(context, styleUuid);
            }

            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);

            mUseLoanee = DBKey.isUsed(global, DBKey.KEY_LOANEE);
            mUseToc = DBKey.isUsed(global, DBKey.BITMASK_TOC);

            mFieldsMap = initFields(context);

            final long bookId = args.getLong(DBKey.PK_ID, 0);
            SanityCheck.requirePositiveValue(bookId, DBKey.PK_ID);
            mBook = Book.from(bookId, mBookDao);
        }
    }

    @NonNull
    public Fields getFields() {
        return mFieldsMap;
    }

    @NonNull
    private Fields initFields(@NonNull final Context context) {

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        // These FieldFormatters can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale);
        final FieldFormatter<String> htmlFormatter = new HtmlFormatter<>(true, true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> languageFormatter = new LanguageFormatter(userLocale);

        final Fields fields = new Fields();

        // book fields
        fields.add(R.id.title, new TextViewAccessor<>(), DBKey.KEY_TITLE);

        fields.add(R.id.author, new TextViewAccessor<>(
                           new AuthorListFormatter(Author.Details.Full, false, true)),
                   Book.BKEY_AUTHOR_LIST, DBKey.FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author);

        fields.add(R.id.series_title, new TextViewAccessor<>(
                           new SeriesListFormatter(Series.Details.Full, false, true)),
                   Book.BKEY_SERIES_LIST, DBKey.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.isbn, new TextViewAccessor<>(), DBKey.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.description, new TextViewAccessor<>(htmlFormatter),
                   DBKey.KEY_DESCRIPTION);

        fields.add(R.id.genre, new TextViewAccessor<>(), DBKey.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        fields.add(R.id.language, new TextViewAccessor<>(languageFormatter),
                   DBKey.KEY_LANGUAGE)
              .setRelatedFields(R.id.lbl_language);

        fields.add(R.id.pages, new TextViewAccessor<>(new PagesFormatter()),
                   DBKey.KEY_PAGES);
        fields.add(R.id.format, new TextViewAccessor<>(), DBKey.KEY_FORMAT);
        fields.add(R.id.color, new TextViewAccessor<>(), DBKey.KEY_COLOR);
        fields.add(R.id.publisher, new TextViewAccessor<>(new CsvFormatter()),
                   Book.BKEY_PUBLISHER_LIST, DBKey.KEY_PUBLISHER_NAME);

        fields.add(R.id.date_published, new TextViewAccessor<>(dateFormatter),
                   DBKey.DATE_BOOK_PUBLICATION)
              .setRelatedFields(R.id.lbl_date_published);

        fields.add(R.id.first_publication, new TextViewAccessor<>(dateFormatter),
                   DBKey.DATE_FIRST_PUBLICATION)
              .setRelatedFields(R.id.lbl_first_publication);

        fields.add(R.id.print_run, new TextViewAccessor<>(), DBKey.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.price_listed, new TextViewAccessor<>(moneyFormatter),
                   DBKey.PRICE_LISTED)
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        // Personal fields
        fields.add(R.id.bookshelves, new EntityListChipGroupAccessor(
                           () -> new ArrayList<>(
                                   ServiceLocator.getInstance().getBookshelfDao().getAll()),
                           false), Book.BKEY_BOOKSHELF_LIST,
                   DBKey.FK_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        fields.add(R.id.date_acquired, new TextViewAccessor<>(dateFormatter),
                   DBKey.DATE_ACQUIRED)
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.add(R.id.edition,
                   new BitmaskChipGroupAccessor(Book.Edition::getEditions, false),
                   DBKey.BITMASK_EDITION)
              .setRelatedFields(R.id.lbl_edition);

        fields.add(R.id.location, new TextViewAccessor<>(), DBKey.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.add(R.id.rating, new RatingBarAccessor(false), DBKey.KEY_RATING);

        fields.add(R.id.condition, new TextViewAccessor<>(
                           new StringArrayResFormatter(context, R.array.conditions_book)),
                   DBKey.KEY_BOOK_CONDITION)
              .setRelatedFields(R.id.lbl_condition);
        fields.add(R.id.condition_cover, new TextViewAccessor<>(
                           new StringArrayResFormatter(context, R.array.conditions_dust_cover)),
                   DBKey.KEY_BOOK_CONDITION_COVER)
              .setRelatedFields(R.id.lbl_condition_cover);

        fields.add(R.id.notes, new TextViewAccessor<>(htmlFormatter),
                   DBKey.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        fields.add(R.id.read_start, new TextViewAccessor<>(dateFormatter),
                   DBKey.DATE_READ_START)
              .setRelatedFields(R.id.lbl_read_start);
        fields.add(R.id.read_end, new TextViewAccessor<>(dateFormatter),
                   DBKey.DATE_READ_END)
              .setRelatedFields(R.id.lbl_read_end);

        fields.add(R.id.icon_read, new BooleanIndicatorAccessor(), DBKey.BOOL_READ);

        fields.add(R.id.signed, new BooleanIndicatorAccessor(), DBKey.BOOL_SIGNED);

        fields.add(R.id.price_paid, new TextViewAccessor<>(moneyFormatter),
                   DBKey.PRICE_PAID)
              .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);

        return fields;
    }

    @NonNull
    public MutableLiveData<BookMessage> onBookLoaded() {
        return mBookLd;
    }

    void reloadBook() {
        Objects.requireNonNull(mBook, "Book not loaded yet");
        mBook = Book.from(mBook.getId(), mBookDao);
        mBookLd.setValue(new BookMessage(mBook, -1));
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Book getBook() {
        Objects.requireNonNull(mBook, "Book not loaded yet");
        return mBook;
    }

    public boolean useLoanee() {
        return mUseLoanee;
    }

    public boolean useToc() {
        return mUseToc;
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @return {@code true} if the book is available for lending.
     */
    boolean isAvailable() {
        return mBook.getLoanee().isEmpty();
    }

    /**
     * The book was returned, remove the loanee.
     */
    void deleteLoan() {
        mBook.remove(DBKey.KEY_LOANEE);
        ServiceLocator.getInstance().getLoaneeDao().setLoanee(mBook, null);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean toggleRead() {
        if (mBook.toggleRead()) {
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the current book.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook() {
        if (mBookDao.delete(mBook)) {
            //noinspection ConstantConditions
            mBook = null;
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>The fragment has no access to the style -> return the global visibility</li>
     *     <li>The global style is set to HIDE -> {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param global Global preferences
     * @param cIdx   0..n image index
     *
     * @return {@code true} if in use
     */
    boolean isCoverUsed(@NonNull final SharedPreferences global,
                        @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx])) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields().isShowCover(global, cIdx);
        }
    }

    public static class BookMessage
            implements LiveDataEvent {

        public final Book book;
        public int tracker;
        /** {@link LiveDataEvent}. */
        private boolean mHasBeenHandled;

        public BookMessage(@NonNull final Book book,
                           final int tracker) {
            this.book = book;
            this.tracker = tracker;
        }

        @Override
        public boolean isNewEvent() {
            final boolean isNew = !mHasBeenHandled;
            mHasBeenHandled = true;
            return isNew;
        }
    }
}

/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.BookReadStatusViewModel;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.fields.BooleanIndicatorField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.RatingBarField;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.BitmaskFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.ListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Shared data between details and Read fragments.
 */
public class ShowBookDetailsViewModel
        extends ViewModel
        implements BookReadStatusViewModel {

    private static final String BOOK_NOT_LOADED_YET = "Book not loaded yet";

    private final MutableLiveData<Book> onBookLoaded = new MutableLiveData<>();
    private final MutableLiveData<Void> onReadStatusChanged = new MutableLiveData<>();

    /** the list with all fields. */
    private final List<Field<?, ? extends View>> fields = new ArrayList<>();
    @Nullable
    private Book book;
    private boolean embedded;

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     * @param style   to apply
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args,
                     @NonNull final Style style) {
        if (book == null) {
            embedded = args.getBoolean(ShowBookDetailsFragment.BKEY_EMBEDDED, false);

            book = Book.from(args.getLong(DBKey.FK_BOOK, 0));

            initFields(context, style, ServiceLocator.getInstance().getLanguages());
        }

        updateUI();
    }

    /**
     * Observable - triggers a UI update for the given {@link Book}.
     *
     * @return book
     */
    @NonNull
    MutableLiveData<Book> onBookLoaded() {
        return onBookLoaded;
    }

    private void updateUI() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        onBookLoaded.setValue(book);
        onReadStatusChanged.setValue(null);
    }

    /**
     * Are we running in embedded mode.
     *
     * @return flag
     */
    public boolean isEmbedded() {
        return embedded;
    }

    /**
     * Load the data for the given book id and trigger a UI update.
     *
     * @param bookId to display
     */
    public void displayBook(final long bookId) {
        book = Book.from(bookId);
        updateUI();
    }

    /**
     * Reload the data for the current book and trigger a UI update.
     */
    void displayBook() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        displayBook(book.getId());
    }

    /**
     * Get the currently displayed book.
     *
     * @return the book
     */
    @NonNull
    public Book getBook() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return book;
    }

    /**
     * The book was returned, remove the loanee.
     * <p>
     * <strong>Important:</strong> we're not using {@link #onBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteLoan() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return ServiceLocator.getInstance().getLoaneeDao().delete(book);
    }

    /**
     * Delete the current book.
     * <p>
     * <strong>Important:</strong> we're not using {@link #onBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);

        if (ServiceLocator.getInstance().getBookDao().delete(book)) {
            book = null;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isRead() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return book.isRead();
    }

    @Override
    public void setReadNow(final boolean read) {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        ServiceLocator.getInstance().getBookDao().setRead(book, read);
        onReadStatusChanged.setValue(null);
    }

    @Override
    public void readStatusChanged() {
        onReadStatusChanged.setValue(null);
    }

    @Override
    @NonNull
    public ReadingProgress getReadingProgress() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return book.getReadingProgress();
    }

    @Override
    public void setReadingProgress(@NonNull final ReadingProgress readingProgress) {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        ServiceLocator.getInstance().getBookDao().setReadingProgress(book, readingProgress);
        onReadStatusChanged.setValue(null);
    }

    @Override
    @NonNull
    public MutableLiveData<Void> onReadStatusChanged() {
        return onReadStatusChanged;
    }

    @NonNull
    List<Field<?, ? extends View>> getFields() {
        return fields;
    }

    @NonNull
    <T, V extends View> Optional<Field<T, V>> getField(@IdRes final int id) {
        //noinspection unchecked
        return fields.stream()
                     .filter(field -> field.getFieldViewId() == id)
                     .map(field -> (Field<T, V>) field)
                     .findFirst();
    }

    private void initFields(@NonNull final Context context,
                            @NonNull final Style style,
                            @NonNull final Languages languages) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        // These FieldFormatters are shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale, false);
        final FieldFormatter<String> dateUtcFormatter = new DateFieldFormatter(userLocale, true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> notesFormatter =
                new HtmlFormatter<>(true, true);
        final FieldFormatter<String> languageFormatter =
                new LanguageFormatter(userLocale, languages);
        final ListFormatter<Entity> normalDetailListFormatter =
                new ListFormatter<>(Details.Normal, style);
        final ListFormatter<Entity> fullDetailListFormatter =
                new ListFormatter<>(Details.Full, style);

        // book fields
        fields.add(new TextViewField<>(FragmentId.Main, R.id.title, DBKey.TITLE));
        fields.add(new TextViewField<>(FragmentId.Main, R.id.original_title,
                                       DBKey.TITLE_ORIGINAL_LANG));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.author, Book.BKEY_AUTHOR_LIST,
                                       DBKey.FK_AUTHOR,
                                       fullDetailListFormatter)
                           .addRelatedViews(R.id.lbl_author));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.series_title, Book.BKEY_SERIES_LIST,
                                       DBKey.FK_SERIES,
                                       fullDetailListFormatter)
                           .addRelatedViews(R.id.lbl_series));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.isbn, DBKey.BOOK_ISBN)
                           .addRelatedViews(R.id.lbl_isbn));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.description, DBKey.DESCRIPTION,
                                       notesFormatter)
                           // The description_scroller is not present on all devices.
                           // Do NOT replace it with "description_layout" !!!
                           .addRelatedViews(R.id.description_scroller));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.genre, DBKey.GENRE)
                           .addRelatedViews(R.id.lbl_genre));


        fields.add(new TextViewField<>(FragmentId.Main, R.id.language, DBKey.LANGUAGE,
                                       languageFormatter)
                           .addRelatedViews(R.id.lbl_language));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.pages, DBKey.PAGE_COUNT,
                                       new PagesFormatter()));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.format, DBKey.FORMAT));
        fields.add(new TextViewField<>(FragmentId.Main, R.id.color, DBKey.COLOR));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                       DBKey.FK_PUBLISHER,
                                       normalDetailListFormatter));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.date_published,
                                       DBKey.BOOK_PUBLICATION__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_date_published));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.first_publication,
                                       DBKey.FIRST_PUBLICATION__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_first_publication));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.edition, DBKey.EDITION__BITMASK,
                                       new BitmaskFormatter(Details.Normal, Book.Edition::getAll))
                           .addRelatedViews(R.id.lbl_edition));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.print_run, DBKey.PRINT_RUN));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.price_listed, DBKey.PRICE_LISTED,
                                       moneyFormatter)
                           .addRelatedViews(R.id.lbl_price_listed));


        // Personal fields

        fields.add(new TextViewField<>(FragmentId.Main, R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                       DBKey.FK_BOOKSHELF,
                                       normalDetailListFormatter)
                           .addRelatedViews(R.id.lbl_bookshelves));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.date_acquired,
                                       DBKey.DATE_ACQUIRED,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_date_acquired));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.location,
                                       DBKey.LOCATION)
                           .addRelatedViews(R.id.lbl_location, R.id.lbl_location_long));

        fields.add(new RatingBarField(FragmentId.Main, R.id.rating,
                                      DBKey.RATING));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.condition,
                                       DBKey.BOOK_CONDITION,
                                       new StringArrayResFormatter(
                                               context, R.array.conditions_book))
                           .addRelatedViews(R.id.lbl_condition));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.condition_cover,
                                       DBKey.BOOK_CONDITION_COVER,
                                       new StringArrayResFormatter(
                                               context, R.array.conditions_dust_cover))
                           .addRelatedViews(R.id.lbl_condition_cover));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.notes,
                                       DBKey.PERSONAL_NOTES,
                                       notesFormatter)
                           .addRelatedViews(R.id.lbl_notes));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.read_start,
                                       DBKey.READ_START__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_read_start));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.read_end,
                                       DBKey.READ_END__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_read_end));

        fields.add(new BooleanIndicatorField(FragmentId.Main, R.id.signed,
                                             DBKey.SIGNED__BOOL));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.price_paid,
                                       DBKey.PRICE_PAID,
                                       moneyFormatter)
                           .addRelatedViews(R.id.lbl_price_paid));


        fields.add(new TextViewField<>(FragmentId.Main, R.id.date_added,
                                       DBKey.DATE_ADDED__UTC,
                                       dateUtcFormatter)
                           .addRelatedViews(R.id.lbl_date_added));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.date_last_updated,
                                       DBKey.DATE_LAST_UPDATED__UTC,
                                       dateUtcFormatter)
                           .addRelatedViews(R.id.lbl_date_last_updated));
    }
}

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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;
import com.hardbacknutter.nevertoomanybooks.network.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;

public class ImportCollection {

    /**
     * param 1: userId
     * param 2: page number
     * param 3: binary flags; see {@link #mFlags}.
     * <p>
     * suffix: "_search_%4s" : with a search-filter... not fully sure what fields are searched.
     * => not supported for now.
     * <p>
     * The suffix "_search-" is always added by the site form; so we add it as well.
     */
    private static final String URL_MY_BOOKS =
            "https://www.stripinfo.be/userCollection/index/%1$s/0/%2$d/%3$s_search-";

    /** A full page contains 25 rows. */
    private static final int MAX_PAGE_SIZE = 25;

    private static final String ATTR_CHECKED = "checked";
    private static final String ROW_ID_ATTR = "showIfInCollection-";
    private static final int ROW_ID_ATTR_LEN = ROW_ID_ATTR.length();

    /**
     * Filters.
     * <ul>Each flag can be:
     * <li>0: don't care</li>
     * <li>1: Yes</li>
     * <li>2: No</li>
     * </ul>
     * <ul>
     *     <li>1000: books I own ("in bezit")</li>
     *     <li>0100: in wishlist ("verlanglijst")</li>
     *     <li>0010: read ("gelezen")</li>
     *     <li>0001: which I rated ("met score")</li>
     * </ul>
     * <p>
     * Examples: 1020: all books I own but have not yet read. Don't care about wishlist/rating.
     * <p>
     * For now hardcoded to getting all books owned.
     */
    private static final String mFlags = "1000";

    /** Responsible for loading and parsing the web page. */
    @NonNull
    private final JsoupLoader mJsoupLoader;
    /** Internal id from the website; used in the auth Cookie and links. */
    @NonNull
    private final String mUserId;

    /**
     * Constructor
     *
     * @param userId as extracted from the auth Cookie.
     */
    public ImportCollection(@NonNull final String userId) {
        mUserId = userId;
        mJsoupLoader = new JsoupLoader();
    }


    @SuppressLint("DefaultLocale")
    @WorkerThread
    @NonNull
    public List<ColData> fetch(@NonNull final Context context)
            throws IOException {

        final List<ColData> collection = new ArrayList<>();

        List<ColData> pageList = null;
        String url;
        Document document;

        final Function<String, Optional<TerminatorConnection>> conCreator = (String u) -> {
            try {
                final SearchEngineConfig config = SearchEngineRegistry
                        .getInstance().getByEngineId(SearchSites.STRIP_INFO_BE);

                final TerminatorConnection con = new TerminatorConnection(u)
                        .setConnectTimeout(config.getConnectTimeoutInMs())
                        .setReadTimeout(config.getReadTimeoutInMs());

                return Optional.of(con);
            } catch (@NonNull final IOException ignore) {
                return Optional.empty();
            }
        };

        int page = 0;
        do {
            page++;
            url = String.format(URL_MY_BOOKS, mUserId, page, mFlags);
            document = loadDocument(context, url, conCreator);
            if (document != null) {
                pageList = parse(document);
                collection.addAll(pageList);
            }
        } while (document != null && pageList.size() == MAX_PAGE_SIZE);

        return collection;
    }

    /**
     * Load the url into a parsed {@link org.jsoup.nodes.Document}.
     *
     * @param url to load
     *
     * @return the document, or {@code null} if it failed to load while NOT causing a real error.
     * e.g. the website said 404
     *
     * @throws IOException on any failure except a FileNotFoundException.
     */
    @WorkerThread
    @Nullable
    private Document loadDocument(@NonNull final Context context,
                                  @NonNull final String url,
                                  @NonNull
                                  final Function<String, Optional<TerminatorConnection>> conCreator)
            throws IOException {
        try {
            return mJsoupLoader.loadDocument(context, url, conCreator);

        } catch (@NonNull final FileNotFoundException e) {
            // we couldn't load the page
            return null;
        }
    }

    @NonNull
    public List<ColData> parse(@NonNull final Document document) {

        final List<ColData> collection = new ArrayList<>();

        final Element root = document.getElementById("collectionContent");
        if (root == null) {
            return collection;
        }

        final Elements rows = root.select("div.collectionRow");
        if (rows == null) {
            return collection;
        }

        for (final Element row : rows) {
            final String idAttr = row.id();
            if (!idAttr.isEmpty() && idAttr.startsWith(ROW_ID_ATTR)) {
                try {
                    // ok, we should have a book.
                    final long bookId = Long.parseLong(idAttr.substring(ROW_ID_ATTR_LEN));
                    final Element mine = row.getElementById("stripCollectie-" + bookId);
                    if (mine != null) {
                        final long myId = Long.parseLong(mine.val());
                        final ColData myBook = new ColData(bookId, myId);

                        myBook.setOwned(getBooleanValue(row, "bezit-" + myId));
                        myBook.setRead(getBooleanValue(row, "gelezen-" + myId));
                        myBook.setWanted(getBooleanValue(row, "wishlist-" + myId));

                        myBook.setRating(getIntValue(row, "score-" + myId));
                        myBook.setPrice(getDoubleValue(row, "prijs-" + myId));

                        myBook.setDateAcquired(getStringValue(row, "aankoopdatum-" + myId));
                        myBook.setEdition(getIntValue(row, "druk-" + myId));
                        myBook.setAmount(getIntValue(row, "aantal-" + myId));
                        myBook.setLocation(getStringValue(row, "locatie-" + myId));

                        myBook.setNotes(getStringValue(row, "opmerking-" + myId));

                        collection.add(myBook);
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }

        return collection;
    }

    /**
     * Get the String value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return String
     */
    @Nullable
    private String getStringValue(@NonNull final Element root,
                                  @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            return element.val();
        }
        return null;
    }

    /**
     * Get the Integer value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return Integer
     */
    @Nullable
    private Integer getIntValue(@NonNull final Element root,
                                @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    return Integer.parseInt(val);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return null;
    }

    /**
     * Get the Double value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return Double
     */
    @Nullable
    private Double getDoubleValue(@NonNull final Element root,
                                  @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    return Double.parseDouble(val);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return null;
    }

    /**
     * Get the boolean value, or {@code false} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return boolean
     */
    private boolean getBooleanValue(@NonNull final Element root,
                                    @NonNull final String id) {
        final Element checkbox = root.getElementById(id);
        if (checkbox != null && "checkbox".equalsIgnoreCase(checkbox.attr("type"))) {
            return ATTR_CHECKED.equalsIgnoreCase(checkbox.attr(ATTR_CHECKED));
        }
        return false;
    }

    /**
     * Value class with all available information for each book in the collection.
     */
    public static class ColData {

        private final long mBookId;
        private final long mMyCollectionId;

        private boolean mOwned;
        private boolean mRead;
        private boolean mWanted;

        /**
         * Site.
         * 10 - Subliem!
         * 9 - Uitstekend
         * 8 - Zeer goed
         * 7 - Bovengemiddeld
         * 6 - Goed, maar niet bijzonder
         * 5 - Matig
         * 4 - Zwak
         * 3 - Zeer zwak
         * 2 - Bijzonder zwak
         * 1 - Waarom is deze strip ooit gemaakt?
         */
        @Nullable
        private Integer mRating;

        @Nullable
        private Double mPrice;
        @Nullable
        private String mDateAcquired;
        @Nullable
        private Integer mEdition;
        @Nullable
        private Integer mAmount;
        @Nullable
        private String mLocation;
        @Nullable
        private String mNotes;

        public ColData(final long bookId,
                       final long myCollectionId) {
            mBookId = bookId;
            mMyCollectionId = myCollectionId;
        }

        public long getBookId() {
            return mBookId;
        }

        public long getMyCollectionId() {
            return mMyCollectionId;
        }

        public boolean getOwned() {
            return mOwned;
        }

        public void setOwned(final boolean owned) {
            mOwned = owned;
        }

        public boolean getRead() {
            return mRead;
        }

        public void setRead(final boolean read) {
            mRead = read;
        }

        public boolean getWanted() {
            return mWanted;
        }

        public void setWanted(final boolean wanted) {
            mWanted = wanted;
        }

        /**
         * Site ratings go from 1..10; or {@code null} for not set.
         *
         * @return rating
         */
        @Nullable
        public Integer getRating() {
            return mRating;
        }

        public void setRating(@Nullable final Integer rating) {
            mRating = rating;
        }

        /**
         * Price.
         *
         * @return price in EUR
         */
        @Nullable
        public Double getPrice() {
            return mPrice;
        }

        public void setPrice(@Nullable final Double price) {
            mPrice = price;
        }

        /**
         * Get the date acquired.
         *
         * @return site specific format {@code "DD/MM/YYYY"}, or {@code ""}.
         */
        @Nullable
        public String getDateAcquired() {
            return mDateAcquired;
        }

        /**
         * Incoming value attribute is in the format "DD/MM/YYYY".
         *
         * @param dateAcquired value attribute
         */
        public void setDateAcquired(@Nullable final String dateAcquired) {
            mDateAcquired = dateAcquired;
        }

        /**
         * Get the date acquired.
         *
         * @return ISO formatted {@code "YYYY-MM-DD"}, or {@code null}.
         */
        @Nullable
        public String getISODateAcquired() {
            if (mDateAcquired != null && mDateAcquired.length() == 10) {
                // we could use the date parser... but that would be overkill for this website
                return mDateAcquired.substring(6, 10)
                       + '-' + mDateAcquired.substring(3, 5)
                       + '-' + mDateAcquired.substring(0, 2);
            } else {
                return null;
            }
        }

        @Nullable
        public Integer getEdition() {
            return mEdition;
        }

        public void setEdition(@Nullable final Integer edition) {
            mEdition = edition;
        }

        @Nullable
        public Integer getAmount() {
            return mAmount;
        }

        public void setAmount(@Nullable final Integer amount) {
            mAmount = amount;
        }

        @Nullable
        public String getLocation() {
            return mLocation;
        }

        public void setLocation(@Nullable final String location) {
            mLocation = location;
        }

        @Nullable
        public String getNotes() {
            return mNotes;
        }

        public void setNotes(@Nullable final String notes) {
            mNotes = notes;
        }

        @NonNull
        @Override
        public String toString() {
            return "ColData{" +
                   "bookId=" + mBookId +
                   ", myCollectionId=" + mMyCollectionId +
                   ", owned=" + mOwned +
                   ", read=" + mRead +
                   ", wanted=" + mWanted +
                   ", rating=" + mRating +
                   ", price='" + mPrice + '\'' +
                   ", dateAcquired='" + mDateAcquired + '\'' +
                   ", edition='" + mEdition + '\'' +
                   ", amount=" + mAmount +
                   ", location='" + mLocation + '\'' +
                   ", notes='" + mNotes + '\'' +
                   '}';
        }
    }
}

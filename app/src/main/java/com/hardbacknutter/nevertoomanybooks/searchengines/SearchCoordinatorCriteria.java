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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;

import java.util.StringJoiner;

/**
 * A data class with all values potentially supported by {@link SearchEngine.ByText}.
 * <p>
 * All values are 'raw', i.e. exactly as entered by the user in a form.
 */
public class SearchCoordinatorCriteria {

    @NonNull
    private String title = "";
    @NonNull
    private String author = "";
    @NonNull
    private String series = "";
    @NonNull
    private String seriesNr = "";
    @NonNull
    private String publisher = "";

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull final String title) {
        this.title = title;
    }

    @NonNull
    public String getAuthor() {
        return author;
    }

    public void setAuthor(@NonNull final String author) {
        this.author = author;
    }

    @NonNull
    public String getSeries() {
        return series;
    }

    public void setSeries(@NonNull final String series) {
        this.series = series;
    }

    @NonNull
    public String getSeriesNr() {
        return seriesNr;
    }

    public void setSeriesNr(@NonNull final String seriesNr) {
        this.seriesNr = seriesNr;
    }

    @NonNull
    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(@NonNull final String publisher) {
        this.publisher = publisher;
    }

    public void clear() {
        title = "";
        author = "";
        series = "";
        seriesNr = "";
        publisher = "";
    }

    /**
     * Check if at least one value is set.
     *
     * @return flag
     */
    public boolean isEmpty() {
        return title.isEmpty()
               && author.isEmpty()
               && series.isEmpty()
               && seriesNr.isEmpty()
               && publisher.isEmpty();
    }

    /**
     * Simple concatenation of all the values into a single String.
     *
     * @param delimiter to use
     *
     * @return a StringJoiner ready to concat more options to
     */
    @NonNull
    public StringJoiner concat(@NonNull final String delimiter) {
        final StringJoiner words = new StringJoiner(delimiter);

        if (!title.isEmpty()) {
            words.add(title);
        }
        if (!author.isEmpty()) {
            words.add(author);
        }
        if (!series.isEmpty()) {
            words.add(series);
        }
        if (!seriesNr.isEmpty()) {
            words.add(seriesNr);
        }
        if (!publisher.isEmpty()) {
            words.add(publisher);
        }

        return words;
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchCoordinatorCriteria{"
               + "title='" + title + '\''
               + ", author='" + author + '\''
               + ", series='" + series + '\''
               + ", seriesNr='" + seriesNr + '\''
               + ", publisher='" + publisher + '\''
               + '}';
    }


}

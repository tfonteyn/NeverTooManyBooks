/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Value class to report back what was read/imported.
 * Used by both backup and sync packages.
 * <p>
 * Backup import classes extend this class.
 * Sync import uses this class directly.
 */
public class ReaderResults
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ReaderResults> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ReaderResults createFromParcel(@NonNull final Parcel in) {
            return new ReaderResults(in);
        }

        @Override
        @NonNull
        public ReaderResults[] newArray(final int size) {
            return new ReaderResults[size];
        }
    };

    /** The total #books that were present in the import data. */
    public int booksProcessed;
    /** #books we created. */
    public int booksCreated;
    /** #books we updated. */
    public int booksUpdated;
    /** #books we deleted. */
    public int booksDeleted;
    /** #books we skipped for NON-failure reasons. */
    public int booksSkipped;
    /** #books which explicitly failed. */
    public int booksFailed;

    /** The total #covers that were present in the import data. */
    public int coversProcessed;
    /** #covers we created. */
    public int coversCreated;
    /** #covers we updated. */
    public int coversUpdated;
    /** #covers we deleted. */
    public int coversDeleted;
    /** #covers we skipped for NON-failure reasons. */
    public int coversSkipped;
    /** # covers which explicitly failed. */
    public int coversFailed;

    /**
     * Constructor.
     */
    public ReaderResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    public ReaderResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksCreated = in.readInt();
        booksUpdated = in.readInt();
        booksDeleted = in.readInt();
        booksSkipped = in.readInt();
        booksFailed = in.readInt();

        coversProcessed = in.readInt();
        coversCreated = in.readInt();
        coversUpdated = in.readInt();
        coversDeleted = in.readInt();
        coversSkipped = in.readInt();
        coversFailed = in.readInt();
    }

    /**
     * Accumulate the results.
     *
     * @param results to add
     */
    public void add(@NonNull final ReaderResults results) {
        booksProcessed += results.booksProcessed;
        booksCreated += results.booksCreated;
        booksUpdated += results.booksUpdated;
        booksDeleted += results.booksDeleted;
        booksSkipped += results.booksSkipped;
        booksFailed += results.booksFailed;

        coversProcessed += results.coversProcessed;
        coversCreated += results.coversCreated;
        coversUpdated += results.coversUpdated;
        coversDeleted += results.coversDeleted;
        coversSkipped += results.coversSkipped;
        coversFailed += results.coversFailed;
    }

    /**
     * Create a single String line with a report how many books were create/updated/...
     *
     * @param context Current context
     *
     * @return info or {@code ""} if none found
     *
     * @see #createReport(Context)
     */
    @NonNull
    public String createBooksSummaryLine(@NonNull final Context context) {
        final StringJoiner parts = new StringJoiner(", ");
        if (booksCreated > 0) {
            parts.add(context.getString(R.string.progress_msg_x_created, booksCreated));
        }
        if (booksUpdated > 0) {
            parts.add(context.getString(R.string.progress_msg_x_updated, booksUpdated));
        }
        if (booksDeleted > 0) {
            parts.add(context.getString(R.string.progress_msg_x_deleted, booksDeleted));
        }
        if (booksSkipped > 0) {
            parts.add(context.getString(R.string.progress_msg_x_skipped, booksSkipped));
        }
        if (parts.length() > 0) {
            return context.getString(R.string.name_colon_value,
                                     context.getString(R.string.lbl_books),
                                     parts.toString());
        } else {
            return "";
        }
    }

    /**
     * Create a single String line with a report how many covers were create/updated/...
     *
     * @param context Current context
     *
     * @return info or {@code ""} if none found
     *
     * @see #createReport(Context)
     */
    @NonNull
    public String createCoversSummaryLine(@NonNull final Context context) {
        final StringJoiner parts = new StringJoiner(", ");
        if (coversCreated > 0) {
            parts.add(context.getString(R.string.progress_msg_x_created, coversCreated));
        }
        if (coversUpdated > 0) {
            parts.add(context.getString(R.string.progress_msg_x_updated, coversUpdated));
        }
        if (coversDeleted > 0) {
            parts.add(context.getString(R.string.progress_msg_x_deleted, coversDeleted));
        }
        if (coversSkipped > 0) {
            parts.add(context.getString(R.string.progress_msg_x_skipped, coversSkipped));
        }
        if (parts.length() > 0) {
            return context.getString(R.string.name_colon_value,
                                     context.getString(R.string.lbl_covers),
                                     parts.toString());
        } else {
            return "";
        }
    }

    /**
     * Create suitable lines/strings for book and cover results to show
     * in a report to the user.
     * <p>
     * Example - parts which are {@code 0} are not added
     * <pre>
     *     • Books: 4 created, 78 updated, 5 deleted
     *     • Covers: 32 created, 4 updated, 21 skipped
     * </pre>
     *
     * @param context Current context
     *
     * @return 0, 1 or 2 'bullet' lines with book and cover results.
     */
    @NonNull
    public List<String> createReport(@NonNull final Context context) {
        final List<String> lines = new ArrayList<>();

        final String booksSummaryLine = createBooksSummaryLine(context);
        if (!booksSummaryLine.isEmpty()) {
            lines.add(context.getString(R.string.list_element, booksSummaryLine));
        }

        final String coversSummaryLine = createCoversSummaryLine(context);
        if (!coversSummaryLine.isEmpty()) {
            lines.add(context.getString(R.string.list_element, coversSummaryLine));
        }

        return lines;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksCreated);
        dest.writeInt(booksUpdated);
        dest.writeInt(booksDeleted);
        dest.writeInt(booksSkipped);
        dest.writeInt(booksFailed);

        dest.writeInt(coversProcessed);
        dest.writeInt(coversCreated);
        dest.writeInt(coversUpdated);
        dest.writeInt(coversDeleted);
        dest.writeInt(coversSkipped);
        dest.writeInt(coversFailed);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + "booksProcessed=" + booksProcessed
               + ", booksCreated=" + booksCreated
               + ", booksUpdated=" + booksUpdated
               + ", booksDeleted=" + booksDeleted
               + ", booksSkipped=" + booksSkipped
               + ", booksFailed=" + booksFailed

               + ", coversProcessed=" + coversProcessed
               + ", coversCreated=" + coversCreated
               + ", coversUpdated=" + coversUpdated
               + ", coversDeleted=" + coversDeleted
               + ", coversSkipped=" + coversSkipped
               + ", coversFailed=" + coversFailed
               + '}';
    }
}

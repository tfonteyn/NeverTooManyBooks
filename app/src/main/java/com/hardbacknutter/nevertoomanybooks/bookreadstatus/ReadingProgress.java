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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * A data class to facilitate encoding/decoding the database String value
 * and passing these values around.
 */
public final class ReadingProgress
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ReadingProgress> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ReadingProgress createFromParcel(@NonNull final Parcel in) {
            return new ReadingProgress(in);
        }

        @Override
        @NonNull
        public ReadingProgress[] newArray(final int size) {
            return new ReadingProgress[size];
        }
    };
    private static final String JSON_PCT = "pct";
    private static final String JSON_CURRENT_PAGE = "cp";
    private static final String JSON_TOTAL_PAGES = "tp";
    private static final String TAG = "ReadingProgress";
    private int percentage;
    private int currentPage;
    private int totalPages = 1;
    private boolean asPercentage;

    private ReadingProgress(@IntRange(from = 0, to = 100) final int percentage) {
        asPercentage = true;
        setPercentage(percentage);
    }

    private ReadingProgress(@IntRange(from = 0) final int currentPage,
                            @IntRange(from = 1) final int totalPages) {
        asPercentage = false;
        setPages(currentPage, totalPages);
    }

    private ReadingProgress(@NonNull final Parcel in) {
        percentage = in.readInt();
        currentPage = in.readInt();
        totalPages = in.readInt();
        asPercentage = in.readByte() != 0;
    }

    /**
     * Constructor.
     *
     * @param read flag
     *
     * @return new instance with percentage set to either {@code 100} or {@code 0}.
     */
    @NonNull
    public static ReadingProgress finished(final boolean read) {
        return new ReadingProgress(read ? 100 : 0);
    }

    /**
     * Constructor.
     *
     * @param s to decode
     *
     * @return new instance
     */
    @NonNull
    public static ReadingProgress fromJson(@NonNull final String s) {
        if (s.isEmpty()) {
            // Unread
            return new ReadingProgress(0);
        }

        try {
            final JSONObject json = new JSONObject(s);
            if (json.has(JSON_PCT)) {
                return new ReadingProgress(json.getInt(JSON_PCT));
            } else {
                return new ReadingProgress(
                        json.has(JSON_CURRENT_PAGE) ? json.optInt(JSON_CURRENT_PAGE) : 0,
                        json.has(JSON_TOTAL_PAGES) ? json.optInt(JSON_TOTAL_PAGES) : 1);

            }
        } catch (@NonNull final JSONException e) {
            LoggerFactory.getLogger().e(TAG, e, "ps=" + s);
        }

        // Unexpected decoding error
        return new ReadingProgress(0);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(percentage);
        dest.writeInt(currentPage);
        dest.writeInt(totalPages);
        dest.writeByte((byte) (asPercentage ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Encode to a JSON string.
     *
     * @return JSON string <strong>or {@code ""}</strong>
     */
    @NonNull
    public String toJson() {
        if (asPercentage() && percentage > 0) {
            //noinspection DataFlowIssue
            return new JSONObject().put(JSON_PCT, percentage).toString();

        } else if (currentPage > 0 && totalPages > 1) {
            //noinspection DataFlowIssue
            return new JSONObject()
                    .put(JSON_CURRENT_PAGE, currentPage)
                    .put(JSON_TOTAL_PAGES, totalPages)
                    .toString();
        }

        // Not set; just return the empty String.
        return "";
    }

    /**
     * Check if this object represents a percentage.
     *
     * @return {@code true} if this represents a percentage.
     *         {@code false} if it's a "page x of y" value.
     */
    public boolean asPercentage() {
        return asPercentage;
    }

    /**
     * Set this object to represent a percentage.
     *
     * @param asPercentage {@code true} if this represents a percentage.
     *                     {@code false} if it's a "page x of y" value.
     */
    void setAsPercentage(final boolean asPercentage) {
        this.asPercentage = asPercentage;
    }

    /**
     * Check if this object represents a finished/read book.
     *
     * @return flag
     */
    public boolean isRead() {
        if (asPercentage) {
            return percentage == 100;
        } else {
            return Objects.equals(currentPage, totalPages);
        }
    }

    /**
     * Get either the raw percentage if set, or calculate it from the pages.
     *
     * @return percentage; can be {@code 0} if there is not enough data to calculate it
     */
    @IntRange(from = 0, to = 100)
    public int getPercentage() {
        if (asPercentage) {
            return percentage;

        } else if (currentPage > 0 && totalPages > 1) {
            return (int) (((float) currentPage / totalPages) * 100);
        }
        // fallback
        return 0;
    }

    /**
     * Set the raw value for the percentage.
     * Calling this method does <strong>NOT</strong> flag
     * this object as being a percentage value.
     *
     * @param percentage to set
     *
     * @see #setAsPercentage(boolean)
     */
    public void setPercentage(@IntRange(from = 0, to = 100) final int percentage) {
        this.percentage = MathUtils.clamp(percentage, 0, 100);
    }

    /**
     * Get the raw value for the current page.
     *
     * @return page
     */
    @SuppressWarnings("WeakerAccess")
    @IntRange(from = 0)
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Get the raw value for the total number of pages.
     *
     * @return pages
     */
    @IntRange(from = 1)
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * Set the raw value for the total number of pages.
     * Calling this method does <strong>NOT</strong> flag
     * this object as being a "page x of y" value.
     *
     * @param totalPages to set
     *
     * @see #setAsPercentage(boolean)
     */
    public void setTotalPages(@IntRange(from = 1) final int totalPages) {
        this.totalPages = totalPages;
    }

    /**
     * Set the raw value for the total number of pages.
     * Calling this method does <strong>NOT</strong> flag
     * this object as being a "page x of y" value.
     *
     * @param currentPage to set
     * @param totalPages  to set
     *
     * @see #setAsPercentage(boolean)
     */
    public void setPages(@IntRange(from = 0) final int currentPage,
                         @IntRange(from = 1) final int totalPages) {
        this.currentPage = currentPage < 0 ? 0 : currentPage;
        this.totalPages = totalPages < 1 ? 1 : totalPages;

        if (currentPage > totalPages) {
            this.totalPages = currentPage;
        }
    }

    /**
     * Get the formatted string to display to the user describing their progress.
     *
     * @param context Current context
     *
     * @return localized string to display
     */
    @NonNull
    public String toFormattedText(@NonNull final Context context) {
        if (asPercentage) {
            switch (percentage) {
                case 100:
                    return context.getString(R.string.lbl_read);
                case 0:
                    return context.getString(R.string.lbl_unread);
                default:
                    return context.getString(R.string.info_progress_x_percent,
                                             getPercentage());
            }
        } else {
            return context.getString(R.string.info_progress_page_x_of_y,
                                     getCurrentPage(),
                                     getTotalPages());
        }
    }
}

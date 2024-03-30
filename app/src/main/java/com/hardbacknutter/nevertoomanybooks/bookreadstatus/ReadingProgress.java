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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

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
        public ReadingProgress createFromParcel(@NonNull final Parcel in) {
            return new ReadingProgress(in);
        }

        @Override
        public ReadingProgress[] newArray(final int size) {
            return new ReadingProgress[size];
        }
    };

    private static final String JSON_PCT = "pct";
    private static final String JSON_CURRENT_PAGE = "cp";
    private static final String JSON_TOTAL_PAGES = "tp";
    private static final String TAG = "ReadingProgress";
    @Nullable
    private Integer percentage;
    @Nullable
    private Integer currentPage;
    @Nullable
    private Integer totalPages;
    private boolean asPercentage;

    private ReadingProgress(@Nullable final Integer percentage) {
        asPercentage = true;
        this.percentage = percentage;
    }

    private ReadingProgress(@Nullable final Integer currentPage,
                            @Nullable final Integer totalPages) {
        asPercentage = false;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        sanitizePages();
    }

    private ReadingProgress(@NonNull final Parcel in) {
        asPercentage = in.readInt() != 0;

        if (in.readByte() == 0) {
            percentage = null;
        } else {
            percentage = in.readInt();
        }
        if (in.readByte() == 0) {
            currentPage = null;
        } else {
            currentPage = in.readInt();
        }
        if (in.readByte() == 0) {
            totalPages = null;
        } else {
            totalPages = in.readInt();
        }
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
                        json.has(JSON_CURRENT_PAGE) ? json.optInt(JSON_CURRENT_PAGE) : null,
                        json.has(JSON_TOTAL_PAGES) ? json.optInt(JSON_TOTAL_PAGES) : null);

            }
        } catch (@NonNull final JSONException e) {
            LoggerFactory.getLogger().e(TAG, e, "ps=" + s);
        }

        // Unexpected decoding error
        return new ReadingProgress(0);
    }

    /**
     * Encode to a JSON string.
     *
     * @return JSON string <strong>or {@code ""}</strong>
     */
    @NonNull
    public String toJson() {
        if (asPercentage()) {
            if (percentage != null) {
                //noinspection DataFlowIssue
                return new JSONObject().put(JSON_PCT, percentage)
                                       .toString();
            }
        } else {
            final JSONObject out = new JSONObject();
            if (currentPage != null) {
                out.put(JSON_CURRENT_PAGE, (int) currentPage);
            }
            if (totalPages != null) {
                out.put(JSON_TOTAL_PAGES, (int) totalPages);
            }
            if (!out.isEmpty()) {
                //noinspection DataFlowIssue
                return out.toString();
            }
        }

        // Don't return an empty JSON string, just a normal empty string!
        return "";
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(asPercentage ? 1 : 0);

        if (percentage == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(percentage);
        }
        if (currentPage == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(currentPage);
        }
        if (totalPages == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(totalPages);
        }
    }

    @Override
    public int describeContents() {
        return 0;
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
        if (percentage != null) {
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
    public int getPercentage() {
        if (asPercentage) {
            if (percentage != null) {
                return percentage;
            }
        } else if (currentPage != null && totalPages != null) {
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
    public void setPercentage(@Nullable final Integer percentage) {
        this.percentage = percentage;
    }

    /**
     * Get the raw value for the current page.
     *
     * @return page; or {@code 0} if unknown
     */
    @SuppressWarnings("WeakerAccess")
    public int getCurrentPage() {
        if (currentPage != null) {
            return currentPage;
        }
        return 0;
    }

    /**
     * Get the raw value for the total number of pages.
     *
     * @return pages; or {@code 0} if unknown
     */
    public int getTotalPages() {
        if (totalPages != null) {
            return totalPages;
        }
        return 0;
    }

    /**
     * Set the raw value for the total number of pages.
     * Calling this method does <strong>NOT</strong> flag
     * this object as being a "page x of y" value.
     * <p>
     * The value set <strong>will</strong> be adjusted upwards if it's smaller
     * than the current-page value.
     *
     * @param totalPages to set
     *
     * @see #setAsPercentage(boolean)
     */
    public void setTotalPages(@Nullable final Integer totalPages) {
        this.totalPages = totalPages;
        sanitizePages();
    }

    /**
     * Set the raw value for the total number of pages.
     * Calling this method does <strong>NOT</strong> flag
     * this object as being a "page x of y" value.
     *
     * @param currentPage to set; can be {@code null}
     * @param totalPages  to set; can be {@code null}
     *
     * @see #setAsPercentage(boolean)
     */
    public void setPages(@Nullable final Integer currentPage,
                         @Nullable final Integer totalPages) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        sanitizePages();
    }

    private void sanitizePages() {
        if (currentPage == null || totalPages == null) {
            return;
        }

        // If the current-page is larger .... then adjust the total-pages silently
        // (and NOT the other way around)
        if (currentPage > totalPages) {
            totalPages = currentPage;
        }
    }
}

/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;

/** Encapsulate the Book fields which can be shown on the Book-list screen. */
public class ListScreenBookFields
        extends BookFields {

    /** Show the cover image (front only) for each book on the list screen. */
    public static final String PK_COVERS = "style.booklist.show.thumbnails";
    /** Show author for each book. */
    public static final String PK_AUTHOR = "style.booklist.show.author";
    /** Show publisher for each book. */
    public static final String PK_PUBLISHER = "style.booklist.show.publisher";
    /** Show publication date for each book. */
    public static final String PK_PUB_DATE = "style.booklist.show.publication.date";
    /** Show format for each book. */
    public static final String PK_FORMAT = "style.booklist.show.format";
    /** Show location for each book. */
    public static final String PK_LOCATION = "style.booklist.show.location";
    /** Show rating for each book. */
    public static final String PK_RATING = "style.booklist.show.rating";
    /** Show list of bookshelves for each book. */
    public static final String PK_BOOKSHELVES = "style.booklist.show.bookshelves";
    /** Show ISBN for each book. */
    public static final String PK_ISBN = "style.booklist.show.isbn";

    /** Thumbnails in the list view. Only used when {@link #PK_COVERS} is set. */
    public static final String PK_COVER_SCALE = "style.booklist.scale.thumbnails";

    /** Scale factor to apply for thumbnails. */
    private final PInteger mThumbnailScale;

    /**
     * Constructor.
     *
     * @param stylePrefs    the SharedPreferences for the style
     * @param isUserDefined flag
     */
    ListScreenBookFields(@NonNull final SharedPreferences stylePrefs,
                         final boolean isUserDefined) {

        mThumbnailScale = new PInteger(stylePrefs, isUserDefined, PK_COVER_SCALE,
                                       BooklistStyle.IMAGE_SCALE_DEFAULT);

        mFields.put(PK_COVERS,
                    new PBoolean(stylePrefs, isUserDefined, PK_COVERS, true));

        mFields.put(PK_AUTHOR,
                    new PBoolean(stylePrefs, isUserDefined, PK_AUTHOR));

        mFields.put(PK_PUBLISHER,
                    new PBoolean(stylePrefs, isUserDefined, PK_PUBLISHER));

        mFields.put(PK_PUB_DATE,
                    new PBoolean(stylePrefs, isUserDefined, PK_PUB_DATE));

        mFields.put(PK_ISBN,
                    new PBoolean(stylePrefs, isUserDefined, PK_ISBN));

        mFields.put(PK_FORMAT,
                    new PBoolean(stylePrefs, isUserDefined, PK_FORMAT));

        mFields.put(PK_LOCATION,
                    new PBoolean(stylePrefs, isUserDefined, PK_LOCATION));

        mFields.put(PK_RATING,
                    new PBoolean(stylePrefs, isUserDefined, PK_RATING));

        mFields.put(PK_BOOKSHELVES,
                    new PBoolean(stylePrefs, isUserDefined, PK_BOOKSHELVES));
    }

    /**
     * Get the scale <strong>identifier</strong> for the thumbnail size preferred.
     *
     * @param context Current context
     *
     * @return scale id
     */
    @BooklistStyle.CoverScale
    public int getCoverScale(@NonNull final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (isShowField(context, prefs, PK_COVERS)) {
            return mThumbnailScale.getValue(context);
        }
        return BooklistStyle.IMAGE_SCALE_0_NOT_DISPLAYED;
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the cover scale factor.
     *
     * @param context Current context
     *
     * @return summary text
     */
    public String getCoverScaleSummaryText(@NonNull final Context context) {
        final int scale = getCoverScale(context);
        return context.getResources().getStringArray(R.array.pe_bob_thumbnail_scale)[scale];
    }

    /**
     * Get the list of in-use book-detail-field names in a human readable format.
     * This is used to set the summary of the PreferenceScreen.
     * <p>
     * Dev. note: don't micro-optimize this method with a map which would use more memory...
     *
     * @param context Current context
     *
     * @return list of labels, can be empty, but never {@code null}
     */
    @NonNull
    private List<String> getLabels(@NonNull final Context context) {
        final List<String> labels = new ArrayList<>();

        //noinspection ConstantConditions
        if (mFields.get(PK_COVERS).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_covers));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_AUTHOR).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_author));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_PUBLISHER).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_publisher));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_PUB_DATE).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_date_published));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_ISBN).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_isbn));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_FORMAT).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_format));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_LOCATION).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_location));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_RATING).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_rating));
        }
        //noinspection ConstantConditions
        if (mFields.get(PK_BOOKSHELVES).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_bookshelves_long));
        }

        Collections.sort(labels);
        return labels;
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the book fields to show in lists.
     *
     * @param context Current context
     *
     * @return summary text
     */
    public String getSummaryText(@NonNull final Context context) {
        final List<String> labels = getLabels(context);
        if (labels.isEmpty()) {
            return context.getString(R.string.none);
        } else {
            return TextUtils.join(", ", labels);
        }
    }

    /**
     * Add all filters (both active and non-active) to the given map.
     *
     * @param map to add to
     */
    void addToMap(@NonNull final Map<String, PPref> map) {
        super.addToMap(map);
        map.put(mThumbnailScale.getKey(), mThumbnailScale);
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    public void set(@NonNull final Parcel in) {
        super.set(in);
        mThumbnailScale.set(in);
    }

    /**
     * Write the <strong>value</strong> to the Parcel.
     *
     * @param dest parcel to write to
     */
    public void writeToParcel(@NonNull final Parcel dest) {
        super.writeToParcel(dest);
        mThumbnailScale.writeToParcel(dest);
    }

    @NonNull
    @Override
    public String toString() {
        return "ListScreenBookFields{"
               + "mThumbnailScale=" + mThumbnailScale
               + ", mFields=" + mFields
               + '}';
    }

    @StringDef({PK_COVERS,
                PK_AUTHOR,
                PK_PUBLISHER,
                PK_PUB_DATE,
                PK_ISBN,
                PK_FORMAT,
                PK_LOCATION,
                PK_RATING,
                PK_BOOKSHELVES})
    @Retention(RetentionPolicy.SOURCE)
    @interface Key {

    }
}

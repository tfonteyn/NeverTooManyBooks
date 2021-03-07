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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;

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
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     */
    ListScreenBookFields(final boolean isPersistent,
                         @NonNull final StylePersistenceLayer persistenceLayer) {

        mThumbnailScale = new PInteger(isPersistent, persistenceLayer, PK_COVER_SCALE,
                                       ListStyle.IMAGE_SCALE_DEFAULT);

        addField(new PBoolean(isPersistent, persistenceLayer, PK_COVERS, true));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_AUTHOR));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_PUBLISHER));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_PUB_DATE));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_ISBN));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_FORMAT));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_LOCATION));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_RATING));
        addField(new PBoolean(isPersistent, persistenceLayer, PK_BOOKSHELVES));
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param bookFields       to copy from
     */
    ListScreenBookFields(final boolean isPersistent,
                         @NonNull final StylePersistenceLayer persistenceLayer,
                         @NonNull final ListScreenBookFields bookFields) {
        super(isPersistent, persistenceLayer, bookFields);
        mThumbnailScale = new PInteger(isPersistent, persistenceLayer, bookFields.mThumbnailScale);
    }

    /**
     * Get the scale <strong>identifier</strong> for the thumbnail size preferred.
     *
     * @return scale id
     */
    @ListStyle.CoverScale
    public int getCoverScale() {
        final SharedPreferences global = ServiceLocator.getGlobalPreferences();

        if (isShowField(global, PK_COVERS)) {
            return mThumbnailScale.getValue();
        }
        return ListStyle.IMAGE_SCALE_0_NOT_DISPLAYED;
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
        final int scale = getCoverScale();
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

        if (isInUse(PK_COVERS)) {
            labels.add(context.getString(R.string.lbl_covers));
        }
        if (isInUse(PK_AUTHOR)) {
            labels.add(context.getString(R.string.lbl_author));
        }
        if (isInUse(PK_PUBLISHER)) {
            labels.add(context.getString(R.string.lbl_publisher));
        }
        if (isInUse(PK_PUB_DATE)) {
            labels.add(context.getString(R.string.lbl_date_published));
        }
        if (isInUse(PK_ISBN)) {
            labels.add(context.getString(R.string.lbl_isbn));
        }
        if (isInUse(PK_FORMAT)) {
            labels.add(context.getString(R.string.lbl_format));
        }
        if (isInUse(PK_LOCATION)) {
            labels.add(context.getString(R.string.lbl_location));
        }
        if (isInUse(PK_RATING)) {
            labels.add(context.getString(R.string.lbl_rating));
        }
        if (isInUse(PK_BOOKSHELVES)) {
            labels.add(context.getString(R.string.lbl_bookshelves));
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
     * Get a flat map with accumulated preferences for this object and it's children.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return flat map
     */
    @NonNull
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = super.getRawPreferences();
        map.put(mThumbnailScale.getKey(), mThumbnailScale);
        return map;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ListScreenBookFields that = (ListScreenBookFields) o;
        return Objects.equals(mThumbnailScale, that.mThumbnailScale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mThumbnailScale);
    }

    @NonNull
    @Override
    public String toString() {
        return "ListScreenBookFields{"
               + super.toString()
               + ", mThumbnailScale=" + mThumbnailScale
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

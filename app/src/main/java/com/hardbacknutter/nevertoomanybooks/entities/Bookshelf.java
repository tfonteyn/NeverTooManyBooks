/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;

/**
 * Represents a Bookshelf.
 */
public class Bookshelf
        implements Parcelable, ItemWithFixableId, Entity {

    /** {@link Parcelable}. */
    public static final Creator<Bookshelf> CREATOR =
            new Creator<Bookshelf>() {
                @Override
                public Bookshelf createFromParcel(@NonNull final Parcel source) {
                    return new Bookshelf(source);
                }

                @Override
                public Bookshelf[] newArray(final int size) {
                    return new Bookshelf[size];
                }
            };

    /**
     * Preference name - the bookshelf to load next time we startup.
     * Storing the name and not the id. If you export/import... the id will be different.
     */
    public static final String PREF_BOOKSHELF_CURRENT = "Bookshelf.CurrentBookshelf";
    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT_ID = 1;

    /**
     * the virtual 'All Books' representing our complete library.
     * Note we use -1, as {@code 0} is generally used for a 'new' item.
     * i.e. when the user creates a new shelf, it has id==0 before it's saved.
     */
    public static final int ALL_BOOKS = -1;

    /** Bookshelf id. */
    private long mId;
    /** Bookshelf name. */
    @NonNull
    private String mName;
    /**
     * the style uuid. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle}}
     */
    @NonNull
    private String mStyleUuid;

    /**
     * Constructor without ID.
     *
     * @param name      for the Bookshelf
     * @param styleUuid the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final String styleUuid) {
        mName = name.trim();
        mStyleUuid = styleUuid;
    }

    /**
     * Constructor without ID.
     *
     * @param name  for the Bookshelf
     * @param style the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final BooklistStyle style) {
        mName = name.trim();

        mStyleUuid = style.getUuid();
    }

    /**
     * Full Constructor.
     *
     * @param id    the Bookshelf id
     * @param name  for the Bookshelf
     * @param style the style to apply to this shelf
     */
    private Bookshelf(final long id,
                      @NonNull final String name,
                      @NonNull final BooklistStyle style) {
        mId = id;
        mName = name.trim();

        mStyleUuid = style.getUuid();
    }

    /**
     * Full constructor.
     *
     * @param id     the Bookshelf id
     * @param mapper a cursor mapper.
     */
    public Bookshelf(final long id,
                     @NonNull final CursorMapper mapper) {
        mId = id;
        mName = mapper.getString(DBDefinitions.KEY_BOOKSHELF);
        mStyleUuid = mapper.getString(DBDefinitions.KEY_UUID);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Bookshelf(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
        //noinspection ConstantConditions
        mStyleUuid = in.readString();
    }

    /**
     * Get the named bookshelf with fallback to Default/AllBooks as needed.
     *
     * @param context Current context
     * @param db      Database Access
     * @param name    of bookshelf to get
     * @param useAll  set to {@code true} to return the AllBooks shelf, instead the default
     *                if the desired shelf was not found.
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         @Nullable final String name,
                                         final boolean useAll) {
        if (name != null && !name.isEmpty()) {
            Bookshelf bookshelf = db.getBookshelfByName(name);
            if (bookshelf != null) {
                return bookshelf;
            } else if (useAll) {
                // Caller wants "AllBooks" (instead of the default Bookshelf)
                return new Bookshelf(ALL_BOOKS, context.getString(R.string.bookshelf_all_books),
                                     BooklistStyle.getDefaultStyle(context, db));
            }
        }

        return new Bookshelf(DEFAULT_ID, context.getString(R.string.bookshelf_my_books),
                             BooklistStyle.getDefaultStyle(context, db));
    }

    /**
     * Get the preferred bookshelf with fallback to Default/AllBooks as needed.
     *
     * @param context Current context
     * @param db      Database Access
     * @param useAll  set to {@code true} to return the AllBooks shelf, instead the default
     *                if the desired shelf was not found.
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         final boolean useAll) {
        String name = PreferenceManager.getDefaultSharedPreferences(context)
                                       .getString(PREF_BOOKSHELF_CURRENT, null);
        return getBookshelf(context, db, name, useAll);
    }

    /**
     * Set this bookshelf as the current/preferred.
     *
     * @param context Current context
     */
    public void setAsPreferred(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putString(PREF_BOOKSHELF_CURRENT, mName).apply();
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @Override
    public String getLabel(@NonNull final Context context) {
        return mName;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Set the style for this bookshelf. The style will also be set as the global default.
     *
     * @param context Current context
     * @param db      Database Access
     * @param style   to set
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final DAO db,
                         @NonNull final BooklistStyle style) {

        style.setDefault(context);

        mStyleUuid = style.getUuid();
        long styleId = getStyle(context, db).getId();
        db.updateOrInsertBookshelf(context, this, styleId);
    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public BooklistStyle getStyle(@NonNull final Context context,
                                  @NonNull final DAO db) {

        BooklistStyle style = BooklistStyle.getStyle(context, db, mStyleUuid);
        // the previous uuid might have been overruled so we always refresh it
        mStyleUuid = style.getUuid();
        return style;
    }

    /**
     * Do <strong>NOT</strong> call for anything else but export to a CSV file.
     *
     * @return the uuid of the style
     */
    @NonNull
    public String getStyleUuid() {
        return mStyleUuid;
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void validateStyle(@NonNull final Context context,
                              @NonNull final DAO db) {
        String uuid = mStyleUuid;
        BooklistStyle style = getStyle(context, db);
        if (!uuid.equals(style.getUuid())) {
            db.updateBookshelf(this, style.getId());
        }
    }

    /**
     * Replace local details from another Bookshelf.
     *
     * @param source Bookshelf to copy from
     */
    public void copyFrom(@NonNull final Bookshelf source) {
        mName = source.mName;
        mStyleUuid = source.mStyleUuid;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeString(mStyleUuid);
    }

    /**
     * Write the extra data to the JSON object.
     *
     * @param data which {@link #fromJson(JSONObject)} will read
     *
     * @throws JSONException on failure
     */
    public void toJson(final JSONObject data)
            throws JSONException {
        if (!mStyleUuid.isEmpty()) {
            data.put(DBDefinitions.KEY_FK_STYLE, mStyleUuid);
        }
    }

    /**
     * Read the extra data from the JSON object.
     *
     * @param data as written by {@link #toJson(JSONObject)}
     */
    public void fromJson(@NonNull final JSONObject data) {
        // it's quite possible that the UUID is not a style we (currently)
        // know. But that does not matter as we'll check it upon first access.
        if (data.has(DBDefinitions.KEY_FK_STYLE)) {
            mStyleUuid = data.optString(DBDefinitions.KEY_FK_STYLE);
        } else if (data.has("style")) {
            mStyleUuid = data.optString("style");
        }
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale locale) {
        mId = db.getBookshelfId(this);
        return mId;
    }

    /**
     * Each Bookshelf is defined exactly by a unique ID.
     */
    @SuppressWarnings("SameReturnValue")
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Check if this is a regular shelf, or if this one represents our complete library.
     *
     * @return {@code true} for ALL books.
     */
    public boolean isAllBooks() {
        return mId == ALL_BOOKS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }

    /**
     * Equality.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND all other fields are equal</li>
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes even with identical id.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Bookshelf that = (Bookshelf) obj;
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mName, that.mName);
    }

    @Override
    @NonNull
    public String toString() {
        return "Bookshelf{"
               + "mId=" + mId
               + ", mName=`" + mName + '`'
               + ", mStyleUuid=" + mStyleUuid
               + '}';
    }
}

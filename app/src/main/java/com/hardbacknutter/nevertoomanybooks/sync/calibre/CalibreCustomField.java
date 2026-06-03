/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Mapping of the Calibre user fields.
 * <p>
 * Keys can have the same {@link #calibreKey} but <strong>MUST</strong>
 * have a different {@link #type} in that case.
 * <p>
 * Some defaults are loaded during installation.
 * <p>
 * ENHANCE: make custom field names editable.
 */
public class CalibreCustomField
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<CalibreCustomField> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CalibreCustomField createFromParcel(@NonNull final Parcel in) {
            return new CalibreCustomField(in);
        }

        @Override
        @NonNull
        public CalibreCustomField[] newArray(final int size) {
            return new CalibreCustomField[size];
        }
    };

    // Special handling fields which we know about.
    public static final String FIELD_READ_PROGRESS = "#read_progress";


    // NEWTHINGS: adding a Calibre custom field type
    // Don't transform to enum; we may want to support custom/unknown types.
    public static final String TYPE_BOOL = "bool";
    public static final String TYPE_COMMENTS = "comments";
    /** A calculated field in Calibre. Processed as a String. */
    public static final String TYPE_COMPOSITE = "composite";
    public static final String TYPE_DATETIME = "datetime";
    /** Processed as a String. */
    public static final String TYPE_ENUMERATION = "enumeration";
    /**
     * Defined here but not used by us as we don't have any float typed columns,
     * except 'rating' which is covered by TYPE_RATING.
     */
    public static final String TYPE_FLOAT = "float";
    public static final String TYPE_INT = "int";
    /** It's an empty string, or an int from 0..5. */
    public static final String TYPE_RATING = "rating";
    // ??
    public static final String TYPE_SERIES = "series";
    public static final String TYPE_TEXT = "text";

    static final String METADATA_DATATYPE = "datatype";
    static final String VALUE = "#value#";

    @NonNull
    private final String calibreKey;
    @NonNull
    private final String dbKey;
    @Type
    @NonNull
    private final String type;
    /** Row ID. */
    private long id;

    /**
     * Constructor without ID.
     *
     * @param calibreKey The Calibre field name
     * @param type       The Calibre field type
     * @param dbKey      The local {@link DBKey} to which the field is to be mapped
     */
    public CalibreCustomField(@NonNull final String calibreKey,
                              @NonNull @Type final String type,
                              @NonNull final String dbKey) {
        this.calibreKey = calibreKey;
        this.dbKey = dbKey;
        this.type = type;
    }

    /**
     * Full constructor.
     *
     * @param id      row id
     * @param rowData with data
     */
    public CalibreCustomField(final long id,
                              @NonNull final DataHolder rowData) {
        this.id = id;
        calibreKey = rowData.getString(DBKey.CALIBRE.CUSTOM_FIELD_NAME);
        type = rowData.getString(DBKey.CALIBRE.CUSTOM_FIELD_TYPE);
        dbKey = rowData.getString(DBKey.CALIBRE.CUSTOM_FIELD_MAPPING);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private CalibreCustomField(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        calibreKey = in.readString();
        //noinspection DataFlowIssue
        dbKey = in.readString();
        //noinspection DataFlowIssue
        type = in.readString();
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @NonNull
    public String getCalibreKey() {
        return calibreKey;
    }

    @NonNull
    public String getDbKey() {
        return dbKey;
    }

    @NonNull
    public String getType() {
        return type;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(calibreKey);
        dest.writeString(dbKey);
        dest.writeString(type);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreCustomField{"
               + "id=" + id
               + ", calibreKey=`" + calibreKey + '`'
               + ", dbKey=`" + dbKey + '`'
               + ", type=`" + type + '`'
               + '}';
    }

    @StringDef({
            TYPE_BOOL,
            TYPE_COMMENTS,
            TYPE_COMPOSITE,
            TYPE_DATETIME,
            TYPE_ENUMERATION,
            TYPE_FLOAT,
            TYPE_INT,
            TYPE_RATING,
            TYPE_SERIES,
            TYPE_TEXT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }
}

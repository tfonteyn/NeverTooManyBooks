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
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.CalibreBookJsonKey;

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

    /** Calibre defined field which requires special handling. */
    public static final String FIELD_READ_PROGRESS = "#read_progress";

    /**
     * The key which contains the type of the field.
     *
     * @see Type
     */
    public static final String METADATA_DATATYPE = "datatype";
    /**
     * The actual value of the custom field. Can be {@code None} and {@code null}.
     */
    public static final String VALUE = "#value#";

    @NonNull
    private final String calibreKey;
    @NonNull
    private final String dbKey;
    @Type.FieldType
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
                              @NonNull @Type.FieldType final String type,
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

    @Type.FieldType
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

    // NEWTHINGS: adding a Calibre custom field type
    // Don't transform the types to enum;
    // we may want to support custom/unknown types at a future time
    public static final class Type {

        /** Example: "#read". */
        public static final String BOOL = "bool";

        /** Example: "#date_read". */
        public static final String DATETIME = "datetime";

        /** Example: "#notes". */
        public static final String COMMENTS = "comments";
        /** Example: "#notes". */
        public static final String TEXT = "text";

        /** Example: "#rating". The same rules apply as {@link CalibreBookJsonKey#RATING} */
        public static final String RATING = "rating";

        /**
         * A calculated field in Calibre.
         * Processed as a String.
         * Pulled only, never pushed.
         */
        public static final String COMPOSITE = "composite";

        /**
         * Processed as a String.
         * Not used by a predefined field.
         */
        public static final String ENUMERATION = "enumeration";

        /**
         * Found in the Calibre source code, not sure what it represents.
         * Maybe a series of values, i.e. an ordered set ?
         */
        public static final String SERIES = "series";

        /** Not used by a predefined field. */
        public static final String FLOAT = "float";

        /** Not used by a predefined field. */
        public static final String INT = "int";

        private Type() {
        }

        @StringDef({
                BOOL,
                COMMENTS,
                COMPOSITE,
                DATETIME,
                ENUMERATION,
                FLOAT,
                INT,
                RATING,
                SERIES,
                TEXT
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface FieldType {

        }
    }
}

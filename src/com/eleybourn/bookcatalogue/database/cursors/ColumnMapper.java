package com.eleybourn.bookcatalogue.database.cursors;

import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Given a Cursor, and a list of domains, this class constructs a map with
 * the column indexes as used with that Cursor.
 * This is a caching variation of the {@link Cursor#getColumnIndex(String)} method.
 * The latter does a search for the column each time.
 * <p>
 * Avoids the need to repeat writing code to get column indexes for named columns.
 * <p>
 * If a given domain is not present in the Cursor, a {@link ColumnNotPresentException}
 * will be thrown at the time of fetching the value.
 * <p>
 * Tip: when using a ColumnMapper as a parameter to a constructor, e.g.
 * {@link com.eleybourn.bookcatalogue.entities.Bookshelf#Bookshelf(long, ColumnMapper)}
 * always pass the id additionally/separately. This gives the calling code a change to use
 * for example the foreign key id.
 */
public class ColumnMapper {

    /** the cache with the column ID's. WARNING: value will be -1 if column was not found. */
    private final Map<String, Integer> mColumnIndexes = new HashMap<>();
    /** the mapped cursor. */
    private final Cursor mCursor;

    /**
     * Construct the mapper using all the *registered* domains from this table,
     * and the given set of domains.
     * No errors or Exception thrown at construction time.
     *
     * @param cursor to read from
     * @param table  for which to map all registered columns, can be {@code null}.
     *
     * @see TableDefinition#addDomain we don't always register a full set.
     */
    public ColumnMapper(@NonNull final Cursor cursor,
                        @Nullable final TableDefinition table)
            throws IllegalArgumentException {
        mCursor = cursor;

        if (table != null) {
            for (DomainDefinition domain : table.getDomains()) {
                mColumnIndexes.put(domain.name, mCursor.getColumnIndex(domain.name));
            }
        }
    }

    /**
     * Add additional domains after construction time.
     * Useful for child classes.
     *
     * @param domains a list of domains
     */
    public void addDomains(@Nullable final DomainDefinition... domains)
            throws IllegalArgumentException {
        if (domains != null) {
            for (DomainDefinition domain : domains) {
                mColumnIndexes.put(domain.name, mCursor.getColumnIndex(domain.name));
            }
        }
    }

    /**
     * Add additional domains after construction time.
     * Useful for child classes.
     *
     * @param domains a list of domains
     */
    public void addDomains(@Nullable final String... domains)
            throws IllegalArgumentException {
        if (domains != null) {
            for (String domain : domains) {
                mColumnIndexes.put(domain, mCursor.getColumnIndex(domain));
            }
        }
    }

    /**
     * @param domainName the domain to get
     *
     * @return {@code true} if this mapper contains the specified domain.
     */
    public boolean contains(@NonNull final String domainName) {
        Integer index = mColumnIndexes.get(domainName);
        return (index != null) && (index != -1);
    }

    /**
     * @param domainName the name of the domain to get
     *
     * @return the string value of the column.
     * A {@code null} value will be returned as an empty String.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @NonNull
    public String getString(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
        if (mCursor.isNull(index)) {
            return "";
        }
        return mCursor.getString(index);
    }

    /**
     * @param domainName to get
     *
     * @return the boolean value of the column ({@code null} comes back as false).
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public boolean getBoolean(@NonNull final String domainName)
            throws ColumnNotPresentException {
        return getInt(domainName) == 1;
    }

    /**
     * @param domainName to get
     *
     * @return the int value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public int getInt(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getInt(index);
    }

    /**
     * @param domainName to get
     *
     * @return the long value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public long getLong(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getLong(index);
    }

    /**
     * @param domainName to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public double getDouble(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getDouble(index);
    }

    /**
     * @param domainName to get
     *
     * @return the byte[] value of the column.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @SuppressWarnings("unused")
    @Nullable
    public byte[] getBlob(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
        return mCursor.getBlob(index);
    }

    /**
     * @return a bundle with all the columns present ({@code null} values are dropped).
     */
    @SuppressWarnings("unused")
    @NonNull
    public Bundle getAll() {
        Bundle bundle = new Bundle();

        for (Map.Entry<String, Integer> col : mColumnIndexes.entrySet()) {
            if (!col.getValue().equals(-1)) {
                switch (mCursor.getType(col.getValue())) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        bundle.putInt(col.getKey(), mCursor.getInt(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_STRING:
                        bundle.putString(col.getKey(), mCursor.getString(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_FLOAT:
                        bundle.putFloat(col.getKey(), mCursor.getFloat(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_BLOB:
                        bundle.putByteArray(col.getKey(), mCursor.getBlob(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_NULL:
                        // field value null; skip
                        break;

                    default:
                        Logger.warnWithStackTrace(this, "Unknown type", "key=" + col.getKey());
                        break;
                }
            }
        }

        return bundle;
    }
}

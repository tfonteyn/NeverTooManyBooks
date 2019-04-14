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
 * Given a Cursor, and a list of {@link DomainDefinition}, this class constructs a map with
 * the column indexes as used with that Cursor.
 * <p>
 * Avoids the need to repeat writing code to get column indexes for named columns.
 * <p>
 * If a given domain is not present in the Cursor, a {@link ColumnNotPresentException}
 * will be thrown at the time of fetching the value.
 * <p>
 * Tip: when using a ColumnMapper as a parameter to a constructor, e.g.
 * {@link com.eleybourn.bookcatalogue.entities.Bookshelf#Bookshelf(long, ColumnMapper)}
 * always pass the id separately. This gives the calling code a change to use for example
 * the foreign key id.
 */
public class ColumnMapper {

    /** the cache with the column id's. WARNING: value will be -1 if column was not found. */
    private final Map<String, Integer> mColumnIndexes = new HashMap<>();
    /** the mapped cursor. */
    private final Cursor mCursor;

    /**
     * Construct the mapper using all the *registered* domains from this table,
     * and the given set of domains.
     * No errors or Exception thrown at construction time.
     *
     * @param cursor  to read from
     * @param table   for which to map all registered columns, can be null.
     * @param domains a list of domains (e.g. from other tables)
     *
     * @see TableDefinition#addDomain(DomainDefinition) we don't always register a full set.
     */
    public ColumnMapper(@NonNull final Cursor cursor,
                        @Nullable final TableDefinition table,
                        @Nullable final DomainDefinition... domains)
            throws IllegalArgumentException {
        mCursor = cursor;

        if (table != null) {
            for (DomainDefinition domain : table.getDomains()) {
                mColumnIndexes.put(domain.name, mCursor.getColumnIndex(domain.name));
            }
        }
        addDomains(domains);
    }

    /**
     * Add additional domains after construction time.
     * Useful for child classes.
     *
     * @param domains a list of domains
     */
    @SuppressWarnings("WeakerAccess")
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
     * @param names a list of domain names
     */
    public void addDomains(@Nullable final String... names)
            throws IllegalArgumentException {
        if (names != null) {
            for (String name : names) {
                mColumnIndexes.put(name, mCursor.getColumnIndex(name));
            }
        }
    }

    /**
     * @param domain the name of the domain to get
     *
     * @return <tt>true</tt> if this mapper contains the specified domain.
     */
    public boolean contains(@NonNull final DomainDefinition domain) {
        //noinspection ConstantConditions
        return -1 == mColumnIndexes.get(domain.name);
    }

    /**
     * @param domain the name of the domain to get
     *
     * @return the string value of the column. A null value will be returned as an empty String.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @NonNull
    public String getString(@NonNull final String domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain);
        if (index == -1) {
            throw new ColumnNotPresentException(domain);
        }
        if (mCursor.isNull(index)) {
            return "";
        }
        return mCursor.getString(index);
    }

    /**
     * @param domain to get
     *
     * @return the string value of the column. A null value will be returned as an empty String.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @NonNull
    public String getString(@NonNull final DomainDefinition domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain.name);
        if (index == -1) {
            throw new ColumnNotPresentException(domain.name);
        }
        if (mCursor.isNull(index)) {
            return "";
        }
        return mCursor.getString(index);
    }

    /**
     * @param domain to get
     *
     * @return the boolean value of the column (null comes back as false).
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public boolean getBoolean(@NonNull final DomainDefinition domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain.name);
        if (index == -1) {
            throw new ColumnNotPresentException(domain.name);
        }
//        if (mCursor.isNull(index)) {
//            return null; // false
//        }
        return mCursor.getInt(index) == 1;
    }

    /**
     * @param domain to get
     *
     * @return the int value of the column (null comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public int getInt(@NonNull final DomainDefinition domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain.name);
        if (index == -1) {
            throw new ColumnNotPresentException(domain.name);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getInt(index);
    }

    /**
     * @param domain to get
     *
     * @return the long value of the column (null comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public long getLong(@NonNull final DomainDefinition domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain.name);
        if (index == -1) {
            throw new ColumnNotPresentException(domain.name);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getLong(index);
    }

    /**
     * @param domain to get
     *
     * @return the double value of the column (null comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @SuppressWarnings("WeakerAccess")
    public double getDouble(final DomainDefinition domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain.name);
        if (index == -1) {
            throw new ColumnNotPresentException(domain.name);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getDouble(index);
    }

    /**
     * @param domain to get
     *
     * @return the byte[] value of the column.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @SuppressWarnings("unused")
    @Nullable
    public byte[] getBlob(@NonNull final DomainDefinition domain)
            throws ColumnNotPresentException {

        //noinspection ConstantConditions
        int index = mColumnIndexes.get(domain.name);
        if (index == -1) {
            throw new ColumnNotPresentException(domain.name);
        }
        return mCursor.getBlob(index);
    }

    /**
     * @return a bundle with all the columns present (null values are dropped).
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

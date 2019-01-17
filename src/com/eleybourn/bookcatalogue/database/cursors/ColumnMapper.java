package com.eleybourn.bookcatalogue.database.cursors;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Given a Cursor, and a list of {@link DomainDefinition}, this class constructs a map with
 * the column indexes as used with that Cursor.
 * <p>
 * Avoids the need to repeat writing code to get column indexes for named columns.
 */
public class ColumnMapper {

    private final Map<String, Integer> colIds = new HashMap<>();
    private final Cursor mCursor;

    /**
     * @param cursor to read from
     * @param domains a list of domains
     */
    public ColumnMapper(@NonNull final Cursor cursor,
                        @NonNull final DomainDefinition... domains) {
        mCursor = cursor;
        for (DomainDefinition domain : domains) {
            colIds.put(domain.name, mCursor.getColumnIndexOrThrow(domain.name));
        }
    }

    /**
     * @param cursor to read from
     * @param table   all *registered* domains from this table.
     * @param domains a list of domains (e.g. from other tables)
     *
     * @see TableDefinition#addDomain(DomainDefinition) we don't always register a full set.
     */
    public ColumnMapper(@NonNull final Cursor cursor,
                        @NonNull final TableDefinition table,
                        @Nullable final DomainDefinition... domains) {
        mCursor = cursor;
        for (DomainDefinition domain : table.getDomains()) {
            colIds.put(domain.name, mCursor.getColumnIndexOrThrow(domain.name));
        }
        if (domains != null) {
            for (DomainDefinition domain : domains) {
                colIds.put(domain.name, mCursor.getColumnIndexOrThrow(domain.name));
            }
        }
    }

    public String getString(@NonNull final DomainDefinition domain) {
        return mCursor.getString(colIds.get(domain.name));
    }

    public boolean getBoolean(@NonNull final DomainDefinition domain) {
        return mCursor.getInt(colIds.get(domain.name)) == 1;
    }

    public int getInt(@NonNull final DomainDefinition domain) {
        return mCursor.getInt(colIds.get(domain.name));
    }

    public long getLong(@NonNull final DomainDefinition domain) {
        return mCursor.getLong(colIds.get(domain.name));
    }

    public byte[] getBlob(@NonNull final DomainDefinition domain) {
        return mCursor.getBlob(colIds.get(domain.name));
    }
}

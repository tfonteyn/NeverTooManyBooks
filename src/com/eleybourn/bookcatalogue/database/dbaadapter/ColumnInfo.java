package com.eleybourn.bookcatalogue.database.dbaadapter;

/**
 * Column info support. This is useful for auto-building queries from maps that have
 * more columns than are in the table.
 *
 * @author Philip Warner
 */
@SuppressWarnings("unused")
public class ColumnInfo {
    public int position;
    public String name;
    public String typeName;
    public boolean allowNull;
    public boolean isPrimaryKey;
    public String defaultValue;
    public int typeClass;
}

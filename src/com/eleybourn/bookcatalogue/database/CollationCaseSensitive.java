package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


/**
 * Class to detect if collation implementations are case sensitive.
 * This was built because ICS broke the UNICODE collation (making it CS) and we needed
 * to check for collation case-sensitivity.
 * 
 * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
 * 
 * Now the code has been generalized to allow for arbitrary changes to choice of collation.
 * 
 * @author Philip Warner
 */
public class CollationCaseSensitive {
	public static boolean isCaseSensitive(SQLiteDatabase db) {
		// Drop and create table
		db.execSQL("Drop Table If Exists collation_cs_check");
		db.execSQL("Create Table collation_cs_check (t text, i int)");		
		try {
			// Row that *should* be returned first assuming 'a' <=> 'A' 
			db.execSQL("insert into collation_cs_check values ('a', 1)");		
			// Row that *should* be returned second assuming 'a' <=> 'A'; will be returned first if 'A' < 'a'.
			db.execSQL("insert into collation_cs_check values ('A', 2)");

			String s;
			try (Cursor c = db.rawQuery("Select t, i from collation_cs_check order by t " + DatabaseHelper.COLLATION + ", i", new String[] {})) {
				c.moveToFirst();
				s = c.getString(0);
			}
			return !"a".equals(s);
		} finally {
			try { db.execSQL("Drop Table If Exists collation_cs_check");
			} catch (Exception ignored) {}
		}
	}
}

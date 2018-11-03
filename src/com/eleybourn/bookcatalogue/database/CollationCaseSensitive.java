package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;


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
	public static boolean isCaseSensitive(final @NonNull SQLiteDatabase db) {
		// Drop and create table
		db.execSQL("DROP TABLE If Exists collation_cs_check");
		db.execSQL("CREATE TABLE collation_cs_check (t text, i integer)");
		try {
			// Row that *should* be returned first assuming 'a' <=> 'A' 
			db.execSQL("INSERT INTO collation_cs_check VALUES('a', 1)");
			// Row that *should* be returned second assuming 'a' <=> 'A'; will be returned first if 'A' < 'a'.
			db.execSQL("INSERT INTO collation_cs_check VALUES('A', 2)");

			String s;
			try (Cursor c = db.rawQuery("SELECT t, i FROM collation_cs_check ORDER BY t " + DatabaseHelper.COLLATION + ", i", new String[] {})) {
				c.moveToFirst();
				s = c.getString(0);
			}
			return !"a".equals(s);
		} finally {
			try { db.execSQL("DROP TABLE If Exists collation_cs_check");
			} catch (Exception ignored) {}
		}
	}
}

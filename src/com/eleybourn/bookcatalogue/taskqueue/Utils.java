/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.taskqueue;

import android.annotation.SuppressLint;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Utils {
	// Used for formatting dates for sql; everything is assumed to be UTC, or converted to UTC since 
	// UTC is the default SQLite TZ. 
	private static final TimeZone tzUtc = TimeZone.getTimeZone("UTC");

	/** Date format used in displaying and parsing dates in the database */
	@SuppressLint("SimpleDateFormat")
	private static final DateFormat m_stdDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static { 
		m_stdDateFormat.setTimeZone(tzUtc);
	}
	/**
	 * Utility routine to convert a date to a string.
	 * 
	 * @param date	Date to convert
	 * @return		Formatted string
	 */
	public static String date2string(Date date) {
		return m_stdDateFormat.format(date);
	}
	
	/**
	 * Utility routine to convert a 'standard' date string to a date. Returns current date on failure.
	 * 
	 * @param dateString	String to convert
	 * @return				Resulting Date
	 */
	public static Date string2date(String dateString) {
		Date date;
		try {
			date = m_stdDateFormat.parse(dateString);			
		} catch (Exception e) {
			date = new Date();
		}
		return date;
	}
}

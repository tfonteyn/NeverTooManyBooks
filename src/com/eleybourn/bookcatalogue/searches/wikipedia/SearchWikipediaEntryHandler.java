/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.wikipedia;

import android.support.annotation.NonNull;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/** 
 * An XML handler for the Wikipedia entry return 
 */
public class SearchWikipediaEntryHandler extends DefaultHandler {
	private StringBuilder builder;
	private boolean entry1 = false;
	private boolean entry2 = false;
	private boolean entry3 = false;
	private boolean intoc = false;
	private boolean in_parent_ul = false;
	private boolean ready_to_close_parent_ul = false;
	private int div = 0;
	private int entrydiv = 0;
	private String this_title = "";
	private List<String> titles = new ArrayList<>();
	
	private static final String DIV = "div";
	private static final String LIST1 = "ul";
	private static final String LIST2 = "ol";
	private static final String ENTRY = "li";
	private static final String LINK1 = "a"; //optional
	private static final String LINK2 = "i"; //optional
	private static final String LINK3 = "b"; //optional
	private static final String TOC_TABLE = "table";
	
	public List<String> getList(){
		return titles;
	}
	
	@Override
	public void characters(@NonNull final char[] ch, final int start, final int length) throws SAXException {
		super.characters(ch, start, length);
		builder.append(ch, start, length);
	}

	@Override
	public void endElement(@NonNull final String uri, @NonNull final String localName, @NonNull final String name) throws SAXException {
		super.endElement(uri, localName, name);
		// don't do anything if we are in the table of contents
		if (!intoc) {
			if (localName.equalsIgnoreCase(ENTRY)){
				if (entry1 && entry2) {
					String title = this_title + builder;
					title = title.replace("\"", "").trim();
					if (!title.isEmpty()) {
						titles.add(title);
					}
					this_title = "";
					entry3 = false;
				}
			} else if (localName.equalsIgnoreCase(LINK1) || localName.equalsIgnoreCase(LINK2) || localName.equalsIgnoreCase(LINK3)){
					if (entry1 && entry2 && entry3) {
						this_title += builder.toString();
					}
			} else if (localName.equalsIgnoreCase(LIST1) || localName.equalsIgnoreCase(LIST2)){
				if (in_parent_ul && !ready_to_close_parent_ul) {
					// inner ul (if exists)
					in_parent_ul = false;
					entry3 = false;
				} else if (entry1 && entry2) {
					entry1 = false;
					entry2 = false;
					entry3 = false;
					in_parent_ul = false;
				}
			} 
		}
		if (localName.equalsIgnoreCase(DIV)){
			if (entry1 && div==entrydiv) {
				entry1 = false;
				entry2 = false;
				entry3 = false;
			}
			div--;
		}
		if (localName.equalsIgnoreCase(TOC_TABLE)){
			if (intoc) {
				intoc = false;
			}
		}
		builder.setLength(0);
	}

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		// Clear all variables (in case they were set in a previous instantiation)
		builder = new StringBuilder();
		titles = new ArrayList<>();
		entry1 = false;
		entry2 = false;
		entry3 = false;
		intoc = false;
		in_parent_ul = false;
		ready_to_close_parent_ul = false;
		div = 0;
		entrydiv = 0;
		this_title = "";
	}
	
	@Override
	public void startElement(@NonNull final String uri, @NonNull final String localName, @NonNull final String name, @NonNull final Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);
		if (localName.equalsIgnoreCase(DIV)) {
			div++;
			String idName = attributes.getValue("id");
			if ("bodyContent".equals(idName)) {
				entrydiv = div;
				entry1 = true;
			}
		}
		if (entry1 && localName.equalsIgnoreCase(TOC_TABLE)) {
			String idName = attributes.getValue("id");
			if ("toc".equals(idName)) {
				intoc = true;
			}
		}
		if (!intoc) {
			// This is a parent ul. Not the list ul
			if (entry1 && entry2 && (localName.equalsIgnoreCase(LIST1) || localName.equalsIgnoreCase(LIST2))) {
				// inner ul (if exists)
				in_parent_ul = true;
				this_title = "";
				ready_to_close_parent_ul = false;
			} else if (entry1 && (localName.equalsIgnoreCase(LIST1) || localName.equalsIgnoreCase(LIST2))) {
				entry2 = true;
				ready_to_close_parent_ul = true;
			}
			if (entry1 && entry2 && localName.equalsIgnoreCase(ENTRY)) {
				entry3 = true;
			}
		}
	}
}

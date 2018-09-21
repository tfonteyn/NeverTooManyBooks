/*
 * @copyright 2012 Philip Warner
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
package com.eleybourn.bookcatalogue.searches.goodreads.api;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * TODO DEBUG ONLY. TrivialParser is a WORK IN PROGRESS.
 * 
 * Parser Handler to collect the raw HTML response. It just reads the output and stores it for later examination.
 * 
 * @author Philip Warner
 */
public class TrivialParser extends DefaultHandler {

	private final StringBuilder m_Builder = new StringBuilder();

	public TrivialParser() {
	}

	public String getHtml() {
		return m_Builder.toString();
	}

	@Override
	public void characters(final char[] ch, final int start, final int length) throws SAXException {
		super.characters(ch, start, length);
		m_Builder.append(ch, start, length);
	}

	@Override
	public void startElement(final String uri, final String localName, final String name, final Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);

		m_Builder.append("<");
		if (uri != null && !uri.isEmpty())
			m_Builder.append(uri).append(":");
		if (localName != null && !localName.isEmpty())
			m_Builder.append(localName);

		//if (name != null && !name.isEmpty()) {
		//	m_Builder.append(" ");
		//	m_Builder.append(name);
		//}
		if (attributes != null && attributes.getLength() > 0) {
			for(int i = 0; i < attributes.getLength(); i++) {
				m_Builder.append(" ");
				String attrName = attributes.getQName(i);
				if (attrName == null || attrName.isEmpty())
					attrName = attributes.getLocalName(i);
				m_Builder.append(attrName);
				m_Builder.append("='");
				m_Builder.append(attributes.getValue(i));
				m_Builder.append("'");
			}
		}
		m_Builder.append(">");
	}

	@Override
	public void endElement(final String uri, final String localName, final String name) throws SAXException {
		super.endElement(uri, localName, name);

		m_Builder.append("</");
		if (uri != null && !uri.isEmpty())
			m_Builder.append(uri).append(":");
		if (localName != null && !localName.isEmpty())
			m_Builder.append(localName);
		else if (name != null && !name.isEmpty()) {
			m_Builder.append(name);
		}
		m_Builder.append(">");
	}
}

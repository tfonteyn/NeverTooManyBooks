/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.datamanager.datavalidators;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * Base class for validators that operate on a list of other validators.
 * 
 * @author Philip Warner
 */
abstract class MetaValidator extends ArrayList<DataValidator> implements DataValidator {
	public static final long serialVersionUID = 1L;

	/**
	 * Constructor taking one 'sub' Validator.
	 * 
	 * @param v1	Validator to check
	 */
	MetaValidator(final @NonNull DataValidator v1) {
		this.add(v1);
	}

	/**
	 * Constructor taking two 'sub' Validator.
	 * 
	 * @param v1	Validator to check
	 * @param v2	Validator to check
	 */
	MetaValidator(final @NonNull DataValidator v1, final @NonNull DataValidator v2) {
		this.add(v1);
		this.add(v2);
	}

	/**
	 * Constructor taking three 'sub' Validator.
	 * 
	 * @param v1	Validator to check
	 * @param v2	Validator to check
	 * @param v3	Validator to check
	 */
	MetaValidator(final @NonNull DataValidator v1, final @NonNull DataValidator v2,  final @NonNull DataValidator v3) {
		this.add(v1);
		this.add(v2);
		this.add(v3);
	}
}
// Taken from:
/*
   Jazzy - a Java library for Spell Checking
   Copyright (C) 2001 Mindaugas Idzelis
   Full text of license can be found in LICENSE.txt

   This library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2.1 of the License, or (at your option) any later version.

   This library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with this library; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
/*
 * put your module comment here
 * formatted with JxBeauty (c) johann.langhofer@nextra.at
 */

package org.uwcs.choob;

import com.swabunga.spell.engine.*;

import java.io.*;
import java.util.Hashtable;
import java.util.List;
import java.util.LinkedList;

/**
 * Taken from SpellDictionaryHashTable in Jazzy. Not thread-safe.
 * http://www.sourceforge.net/projects/jazzy/
 */
public class SpellDictionaryChoob extends SpellDictionaryASpell {
	/** A field indicating the initial hash map capacity (16KB) for the main
	 *  dictionary hash map. Interested to see what the performance of a
	 *  smaller initial capacity is like.
	 */
	private final static int INITIAL_CAPACITY = 16 * 1024;

	/**
	 * The hashmap that contains the word dictionary. The map is hashed on the doublemeta
	 * code. The map entry contains a LinkedList of words that have the same double meta code.
	 */
	private Hashtable<String, List<String>> mainDictionary = new Hashtable<String, List<String>>(INITIAL_CAPACITY);

	public SpellDictionaryChoob(File phonetics) throws IOException
	{
		super(phonetics);
	}

	/**
	 * Allocates a word in the dictionary
	 */
	public void addWord(String word) {
		String code = getCode(word);
		List<String> list = mainDictionary.get(code);
		if (list == null)
		{
			list = new LinkedList<String>();
			mainDictionary.put(code, list);
			list.add(word.toLowerCase());
		}
		else if (!list.contains(word))
			list.add(word.toLowerCase());
	}

	/**
	 * Removes a word from the dictionary
	 */
	public void removeWord(String word) {
		String code = getCode(word);
		List<String> list = mainDictionary.get(code);
		if (list == null)
			return;
		else
		{
			list.remove(word.toLowerCase());
		}
	}

	/**
	 * Returns a list of strings (words) for the code.
	 */
	public List getWords(String code) {
		//Check the main dictionary.
		List<String> mainDictResult = mainDictionary.get(code);
		if (mainDictResult == null)
			return new LinkedList<String>();
		else
			return mainDictResult;
	}

	/**
	 * Returns true if the word is correctly spelled against the current word list.
	 */
	public boolean isCorrect(String word) {
		List possible = getWords(getCode(word));
		if (possible.contains(word.toLowerCase()))
			return true;
		else
			return false;
	}
}

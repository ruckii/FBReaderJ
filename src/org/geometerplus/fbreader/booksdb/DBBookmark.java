/*
 * Copyright (C) 2009-2012 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.booksdb;

import java.util.Date;

import org.geometerplus.zlibrary.text.view.*;

import org.geometerplus.fbreader.library.Book;
import org.geometerplus.fbreader.library.Bookmark;

public class DBBookmark extends Bookmark {
	DBBookmark(long id, long bookId, String bookTitle, String text, Date creationDate, Date modificationDate, Date accessDate, int accessCount, String modelId, int paragraphIndex, int elementIndex, int charIndex, boolean isVisible) {
		super(id, bookId, bookTitle, text, creationDate, modificationDate, accessDate, accessCount, modelId, paragraphIndex, elementIndex, charIndex, isVisible);
	}

	public DBBookmark(Book book, String modelId, ZLTextWordCursor cursor, int maxLength, boolean isVisible) {
		super(book, modelId, cursor, maxLength, isVisible);
	}

	public DBBookmark(Book book, String modelId, ZLTextPosition position, String text, boolean isVisible) {
		super(book, modelId, position, text, isVisible);
	}

	@Override
	public void save() {
		if (myIsChanged) {
			myId = BooksDatabase.Instance().saveBookmark(this);
			myIsChanged = false;
		}
	}

	@Override
	public void delete() {
		if (myId != -1) {
			BooksDatabase.Instance().deleteBookmark(this);
		}
	}
}

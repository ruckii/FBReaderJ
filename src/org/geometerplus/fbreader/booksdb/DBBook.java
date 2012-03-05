/*
 * Copyright (C) 2007-2012 Geometer Plus <contact@geometerplus.com>
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

import java.util.*;

import org.geometerplus.zlibrary.core.util.ZLMiscUtil;
import org.geometerplus.zlibrary.core.filesystem.*;

import org.geometerplus.zlibrary.text.view.ZLTextPosition;

import org.geometerplus.fbreader.library.*;
import org.geometerplus.fbreader.formats.*;
import org.geometerplus.fbreader.Paths;
import org.geometerplus.fbreader.bookmodel.BookReadingException;

public class DBBook extends Book {
	private long myId;

	private String myEncoding;
	private String myLanguage;
	private String myTitle;
	private List<Author> myAuthors;
	private List<Tag> myTags;
	private SeriesInfo mySeriesInfo;

	private boolean myIsSaved;

	DBBook(long id, ZLFile file, String title, String encoding, String language) {
		super(file);
		myId = id;
		myTitle = title;
		myEncoding = encoding;
		myLanguage = language;
		myIsSaved = true;
	}

	DBBook(ZLFile file) {
		super(file);
		myId = -1;
	}

	void loadLists(BooksDatabase db) {
		myAuthors = db.loadAuthors(myId);
		myTags = db.loadTags(myId);
		mySeriesInfo = db.loadSeriesInfo(myId);
		myIsSaved = true;
	}

	@Override
	public long getId() {
		return myId;
	}

	@Override
	protected boolean readMetaInfo() {
		myEncoding = null;
		myLanguage = null;
		myTitle = null;
		myAuthors = null;
		myTags = null;
		mySeriesInfo = null;

		myIsSaved = false;

		final FormatPlugin plugin = PluginCollection.Instance().getPlugin(File);
		if (plugin == null) {
			return fasle;
		}
		try {
			plugin.readMetaInfo(this);
		} catch (BookReadingException e) {
			return false;
		}
		if (myTitle == null || myTitle.length() == 0) {
			final String fileName = File.getShortName();
			final int index = fileName.lastIndexOf('.');
			setTitle(index > 0 ? fileName.substring(0, index) : fileName);
		}
		final String demoPathPrefix = Paths.BooksDirectoryOption().getValue() + java.io.File.separator + "Demos" + java.io.File.separator;
		if (File.getPath().startsWith(demoPathPrefix)) {
			final String demoTag = LibraryUtil.resource().getResource("demo").getValue();
			setTitle(getTitle() + " (" + demoTag + ")");
			addTag(demoTag);
		}
		return true;
	}

	@Override
	public String getTitle() {
		return myTitle;
	}

	@Override
	public void setTitle(String title) {
		if (!ZLMiscUtil.equals(myTitle, title)) {
			myTitle = title;
			myIsSaved = false;
		}
	}

	@Override
	public List<Author> authors() {
		return (myAuthors != null) ? Collections.unmodifiableList(myAuthors) : Collections.<Author>emptyList();
	}

	@Override
	protected void addAuthor(Author author) {
		if (author == null) {
			return;
		}
		if (myAuthors == null) {
			myAuthors = new ArrayList<Author>();
			myAuthors.add(author);
			myIsSaved = false;
		} else if (!myAuthors.contains(author)) {
			myAuthors.add(author);
			myIsSaved = false;
		}
	}

	void addAuthorWithNoCheck(Author author) {
		if (myAuthors == null) {
			myAuthors = new ArrayList<Author>();
		}
		myAuthors.add(author);
	}

	@Override
	public String getLanguage() {
		return myLanguage;
	}

	@Override
	public void setLanguage(String language) {
		if (!ZLMiscUtil.equals(myLanguage, language)) {
			myLanguage = language;
			myIsSaved = false;
		}
	}

	@Override
	public String getEncoding() {
		return myEncoding;
	}

	@Override
	public void setEncoding(String encoding) {
		if (!ZLMiscUtil.equals(myEncoding, encoding)) {
			myEncoding = encoding;
			myIsSaved = false;
		}
	}

	@Override
	public SeriesInfo getSeriesInfo() {
		return mySeriesInfo;
	}

	@Override
	public void setSeriesInfo(String name, float index) {
		if (mySeriesInfo == null) {
			if (name != null) {
				mySeriesInfo = new SeriesInfo(name, index);
				myIsSaved = false;
			}
		} else if (name == null) {
			mySeriesInfo = null;
			myIsSaved = false;
		} else if (!name.equals(mySeriesInfo.Name) || mySeriesInfo.Index != index) {
			mySeriesInfo = new SeriesInfo(name, index);
			myIsSaved = false;
		}
	}

	void setSeriesInfoWithNoCheck(String name, float index) {
		mySeriesInfo = new SeriesInfo(name, index);
	}

	@Override
	public List<Tag> tags() {
		return (myTags != null) ? Collections.unmodifiableList(myTags) : Collections.<Tag>emptyList();
	}

	@Override
	public void addTag(Tag tag) {
		if (tag != null) {
			if (myTags == null) {
				myTags = new ArrayList<Tag>();
			}
			if (!myTags.contains(tag)) {
				myTags.add(tag);
				myIsSaved = false;
			}
		}
	}

	void addTagWithNoCheck(Tag tag) {
		if (myTags == null) {
			myTags = new ArrayList<Tag>();
		}
		myTags.add(tag);
	}

	@Override
	public boolean save() {
		if (myIsSaved) {
			return false;
		}
		final BooksDatabase database = BooksDatabase.Instance();
		database.executeAsATransaction(new Runnable() {
			public void run() {
				if (myId >= 0) {
					final FileInfoSet fileInfos = new FileInfoSet(File);
					database.updateBookInfo(myId, fileInfos.getId(File), myEncoding, myLanguage, myTitle);
				} else {
					myId = database.insertBookInfo(File, myEncoding, myLanguage, myTitle);
					storeAllVisitedHyperinks();
				}

				long index = 0;
				database.deleteAllBookAuthors(myId);
				for (Author author : authors()) {
					database.saveBookAuthorInfo(myId, index++, author);
				}
				database.deleteAllBookTags(myId);
				for (Tag tag : tags()) {
					database.saveBookTagInfo(myId, tag);
				}
				database.saveBookSeriesInfo(myId, mySeriesInfo);
			}
		});

		myIsSaved = true;
		return true;
	}

	private Set<String> myVisitedHyperlinks;
	private void initHyperlinkSet() {
		if (myVisitedHyperlinks == null) {
			myVisitedHyperlinks = new TreeSet<String>();
			if (myId != -1) {
				myVisitedHyperlinks.addAll(BooksDatabase.Instance().loadVisitedHyperlinks(myId));
			}
		}
	}

	@Override
	public boolean isHyperlinkVisited(String linkId) {
		initHyperlinkSet();
		return myVisitedHyperlinks.contains(linkId);
	}

	@Override
	public void markHyperlinkAsVisited(String linkId) {
		initHyperlinkSet();
		if (!myVisitedHyperlinks.contains(linkId)) {
			myVisitedHyperlinks.add(linkId);
			if (myId != -1) {
				BooksDatabase.Instance().addVisitedHyperlink(myId, linkId);
			}
		}
	}

	@Override
	public void insertIntoBookList() {
		if (myId != -1) {
			BooksDatabase.Instance().insertIntoBookList(myId);
		}
	}

	private void storeAllVisitedHyperinks() {
		if (myId != -1 && myVisitedHyperlinks != null) {
			for (String linkId : myVisitedHyperlinks) {
				BooksDatabase.Instance().addVisitedHyperlink(myId, linkId);
			}
		}
	}
}

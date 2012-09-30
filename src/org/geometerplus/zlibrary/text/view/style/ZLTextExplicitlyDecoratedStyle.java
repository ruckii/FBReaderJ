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

package org.geometerplus.zlibrary.text.view.style;

import org.geometerplus.zlibrary.core.util.ZLBoolean3;

import org.geometerplus.zlibrary.text.model.ZLTextMetrics;
import org.geometerplus.zlibrary.text.model.ZLTextStyleEntry;

import org.geometerplus.zlibrary.text.view.ZLTextStyle;

public class ZLTextExplicitlyDecoratedStyle extends ZLTextStyle implements ZLTextStyleEntry.Feature, ZLTextStyleEntry.FontModifier {
	private final ZLTextStyleEntry myEntry;

	public ZLTextExplicitlyDecoratedStyle(ZLTextStyle base, ZLTextStyleEntry entry) {
		super(base, base.Hyperlink);
		myEntry = entry;
	}

	@Override
	public String getFontFamily() {
		if (myEntry.isFeatureSupported(FONT_FAMILY)) {
			// TODO: implement
		}
		return Base.getFontFamily();
	}
	@Override
	public int getFontSize(ZLTextMetrics metrics) {
		if (myEntry.isFeatureSupported(FONT_STYLE_MODIFIER)) {
			if (myEntry.getFontModifier(FONT_MODIFIER_INHERIT) == ZLBoolean3.B3_TRUE) {
				return Base.Base.getFontSize(metrics);
			}
			if (myEntry.getFontModifier(FONT_MODIFIER_LARGER) == ZLBoolean3.B3_TRUE) {
				return Base.Base.getFontSize(metrics) * 120 / 100;
			}
			if (myEntry.getFontModifier(FONT_MODIFIER_SMALLER) == ZLBoolean3.B3_TRUE) {
				return Base.Base.getFontSize(metrics) * 100 / 120;
			}
		}
		if (myEntry.isFeatureSupported(LENGTH_FONT_SIZE)) {
			return myEntry.getLength(LENGTH_FONT_SIZE, metrics);
		}
		return Base.getFontSize(metrics);
	}

	@Override
	public boolean isBold() {
		switch (myEntry.getFontModifier(FONT_MODIFIER_BOLD)) {
			case B3_TRUE:
				return true;
			case B3_FALSE:
				return false;
			default:
				return Base.isBold();
		}
	}
	@Override
	public boolean isItalic() {
		switch (myEntry.getFontModifier(FONT_MODIFIER_ITALIC)) {
			case B3_TRUE:
				return true;
			case B3_FALSE:
				return false;
			default:
				return Base.isItalic();
		}
	}
	@Override
	public boolean isUnderline() {
		switch (myEntry.getFontModifier(FONT_MODIFIER_UNDERLINED)) {
			case B3_TRUE:
				return true;
			case B3_FALSE:
				return false;
			default:
				return Base.isUnderline();
		}
	}
	@Override
	public boolean isStrikeThrough() {
		switch (myEntry.getFontModifier(FONT_MODIFIER_STRIKEDTHROUGH)) {
			case B3_TRUE:
				return true;
			case B3_FALSE:
				return false;
			default:
				return Base.isStrikeThrough();
		}
	}

	@Override
	public int getLeftIndent() {
		// TODO: implement
		return Base.getLeftIndent();
	}
	@Override
	public int getRightIndent() {
		// TODO: implement
		return Base.getRightIndent();
	}
	@Override
	public int getFirstLineIndentDelta() {
		// TODO: implement
		return Base.getFirstLineIndentDelta();
	}
	@Override
	public int getLineSpacePercent() {
		// TODO: implement
		return Base.getLineSpacePercent();
	}
	@Override
	public int getVerticalShift() {
		// TODO: implement
		return Base.getVerticalShift();
	}
	@Override
	public int getSpaceBefore() {
		// TODO: implement
		return Base.getSpaceBefore();
	}
	@Override
	public int getSpaceAfter() {
		// TODO: implement
		return Base.getSpaceAfter();
	}
	@Override
	public byte getAlignment() {
		return
			myEntry.isFeatureSupported(ALIGNMENT_TYPE)
				? myEntry.getAlignmentType()
				: Base.getAlignment();
	}

	@Override
	public boolean allowHyphenations() {
		// TODO: implement
		return Base.allowHyphenations();
	}
}

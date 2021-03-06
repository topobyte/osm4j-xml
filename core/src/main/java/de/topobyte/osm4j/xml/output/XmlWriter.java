// Copyright 2017 Sebastian Kuerten
//
// This file is part of osm4j.
//
// osm4j is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// osm4j is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with osm4j. If not, see <http://www.gnu.org/licenses/>.

package de.topobyte.osm4j.xml.output;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmMetadata;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmTag;
import de.topobyte.osm4j.core.model.iface.OsmWay;

class XmlWriter
{

	private final String indent1;
	private final String indent2;
	private final String newline;
	private final boolean printMetadata;

	public XmlWriter(String indent1, String indent2, String newline,
			boolean printMetadata)
	{
		this.indent1 = indent1;
		this.indent2 = indent2;
		this.newline = newline;
		this.printMetadata = printMetadata;
	}

	private DecimalFormat f = new DecimalFormat("0.#######;-0.#######",
			new DecimalFormatSymbols(Locale.US));

	private DateTimeFormatter formatter = DateTimeFormat
			.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZoneUTC();

	private CharSequenceTranslator escaper = StringEscapeUtils.ESCAPE_XML11;

	private String templateBounds = "<bounds minlon=\"%f\" minlat=\"%f\" maxlon=\"%f\" maxlat=\"%f\"/>";

	public void write(BuilderWriter buf, OsmBounds bounds)
	{
		buf.append(indent1);
		buf.append(String.format(Locale.US, templateBounds, bounds.getLeft(),
				bounds.getBottom(), bounds.getRight(), bounds.getTop()));
	}

	public void write(BuilderWriter buf, OsmNode node)
	{
		buf.append(indent1);
		buf.append("<node id=\"");
		buf.append(node.getId());
		buf.append("\"");
		buf.append(" lat=\"");
		buf.append(f.format(node.getLatitude()));
		buf.append("\"");
		buf.append(" lon=\"");
		buf.append(f.format(node.getLongitude()));
		buf.append("\"");
		if (printMetadata) {
			OsmMetadata metadata = node.getMetadata();
			printMetadata(buf, metadata);
		}
		if (node.getNumberOfTags() == 0) {
			buf.append("/>");
		} else {
			buf.append(">");
			buf.append(newline);
			printTags(buf, node);
			buf.append(indent1);
			buf.append("</node>");
		}
	}

	public void write(BuilderWriter buf, OsmWay way)
	{
		buf.append(indent1);
		buf.append("<way id=\"");
		buf.append(way.getId());
		buf.append("\"");
		if (printMetadata) {
			OsmMetadata metadata = way.getMetadata();
			printMetadata(buf, metadata);
		}
		if (way.getNumberOfTags() == 0 && way.getNumberOfNodes() == 0) {
			buf.append("/>");
		} else {
			buf.append(">");
			buf.append(newline);
			for (int i = 0; i < way.getNumberOfNodes(); i++) {
				long nodeId = way.getNodeId(i);
				buf.append(indent2);
				buf.append("<nd ref=\"");
				buf.append(nodeId);
				buf.append("\"/>");
				buf.append(newline);
			}
			printTags(buf, way);
			buf.append(indent1);
			buf.append("</way>");
		}
	}

	public void write(BuilderWriter buf, OsmRelation relation)
	{
		buf.append(indent1);
		buf.append("<relation id=\"");
		buf.append(relation.getId());
		buf.append("\"");
		if (printMetadata) {
			OsmMetadata metadata = relation.getMetadata();
			printMetadata(buf, metadata);
		}
		if (relation.getNumberOfTags() == 0
				&& relation.getNumberOfMembers() == 0) {
			buf.append("/>");
		} else {
			buf.append(">");
			buf.append(newline);
			for (int i = 0; i < relation.getNumberOfMembers(); i++) {
				OsmRelationMember member = relation.getMember(i);
				EntityType type = member.getType();
				String t = type == EntityType.Node ? "node"
						: type == EntityType.Way ? "way" : "relation";
				buf.append(indent2);
				buf.append("<member type=\"");
				buf.append(t);
				buf.append("\" ref=\"");
				buf.append(member.getId());
				buf.append("\" role=\"");
				escape(buf, member.getRole());
				buf.append("\"/>");
				buf.append(newline);
			}
			printTags(buf, relation);
			buf.append(indent1);
			buf.append("</relation>");
		}
	}

	private void printMetadata(BuilderWriter buf, OsmMetadata metadata)
	{
		if (metadata == null) {
			return;
		}
		buf.append(" version=\"");
		buf.append(metadata.getVersion());
		buf.append("\"");
		buf.append(" timestamp=\"");
		buf.append(formatter.print(metadata.getTimestamp()));
		buf.append("\"");
		if (metadata.getUid() >= 0) {
			buf.append(" uid=\"");
			buf.append(metadata.getUid());
			buf.append("\"");
			String user = metadata.getUser();
			buf.append(" user=\"");
			escape(buf, user);
			buf.append("\"");
		}
		buf.append(" changeset=\"");
		buf.append(metadata.getChangeset());
		buf.append("\"");
		if (!metadata.isVisible()) {
			buf.append(" visible=\"false\"");
		}
	}

	private void printTags(BuilderWriter buf, OsmEntity entity)
	{
		for (int i = 0; i < entity.getNumberOfTags(); i++) {
			OsmTag tag = entity.getTag(i);
			buf.append(indent2);
			buf.append("<tag k=\"");
			escape(buf, tag.getKey());
			buf.append("\" v=\"");
			escape(buf, tag.getValue());
			buf.append("\"/>");
			buf.append(newline);
		}
	}

	/**
	 * This optimizes the performance by avoiding the construction of a
	 * {@link java.io.StringWriter} in
	 * {@link CharSequenceTranslator#translate(CharSequence)}. The StringWriter
	 * uses a {@link StringBuffer} internally, which we want to avoid too,
	 * because we have the superior {@link StringBuilder} in use anyway.
	 */
	private void escape(BuilderWriter buf, String string)
	{
		try {
			escaper.translate(string, buf);
		} catch (IOException e) {
			// This should really not happen
			throw new RuntimeException(e);
		}
	}

}

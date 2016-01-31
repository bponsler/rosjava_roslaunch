package org.ros.rosjava.roslaunch.xmlrpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The XmlToObject class
 *
 * This class is responsible for providing functions that
 * allow XML elements to be converted into an Object.
 *
 * This can handle the following types of objects stored in XML:
 *     - booleans
 *     - integers
 *     - doubles
 *     - strings
 *     - Lists
 *     - Maps
 *
 *  An example of the XML for a List containing ["Hello", 1, 123.4] is:
 *
 *      <value><array><data>
 *          <value><string>Hello</string></value>
 *          <value><int>1</int></value>
 *          <value><double>123.4</double></value>
 *      </data></array></value>
 */
public class XmlToObject
{
	/**
	 * Convert an XML Element into an Object.
	 *
	 * @param valueElement the XML element
	 * @return the Object
	 */
	public static Object xmlToObject(final Element valueElement)
	{
		String tagName = valueElement.getTagName();
		if (tagName.compareTo("value") == 0)
		{
			NodeList children = valueElement.getChildNodes();
			for (int i = 0; i < children.getLength(); ++i)
			{
				Node child = children.item(i);

				// Only interested in the first element child
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					return xmlValueToObject((Element)child);
				}
			}
		}

		return null;
	}

	/**
	 * Convert an XML value tag into an object.
	 *
	 * @param element the XML Element for the 'value' tag
	 * @return the Object
	 */
	private static Object xmlValueToObject(final Element element)
	{
		String tag = element.getTagName();
		String content = element.getTextContent();

		if (tag.compareTo("boolean") == 0) {
			// XML booleans are 0 and 1 (not false and true)
			return (content.compareTo("1") == 0);
		}
		else if (tag.compareTo("int") == 0) {
			return Integer.parseInt(content);
		}
		else if (tag.compareTo("double") == 0) {
			return Double.parseDouble(content);
		}
		else if (tag.compareTo("string") == 0) {
			return content;
		}
		else if (tag.compareTo("array") == 0) {
			return xmlArrayToObject(element);
		}
		else if (tag.compareTo("struct") == 0) {
			return xmlStructToObject(element);
		}
		else {
			System.err.println("Unknown XML value type: " + tag);
		}

		return null;
	}

	/**
	 * Convert an XML array Element into an Object.
	 *
	 * @param array the XML array Element
	 * @return the Object
	 */
	private static Object xmlArrayToObject(final Element array)
	{
		List<Object> items = new ArrayList<Object>();

		NodeList children = array.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i)
		{
			Node child = children.item(i);
			String tag = child.getNodeName();

			// Only interested in the first element child
			if (child.getNodeType() == Node.ELEMENT_NODE &&
				tag.compareTo("data") == 0)
			{
				NodeList dataChildren = child.getChildNodes();
				for (int j = 0; j < dataChildren.getLength(); ++j)
				{
					Node dataChild = dataChildren.item(j);

					// Only concerned with child elements of the data tag
					if (dataChild.getNodeType() == Node.ELEMENT_NODE)
					{
						Object item = xmlToObject((Element)dataChild);
						if (item != null) {
							items.add(item);
						}
					}
				}
			}
		}

		return items;
	}

	/**
	 * Convert an XML struct Element into an Object.
	 *
	 * @param struct the XML struct Element
	 * @return the Object
	 */
	private static Object xmlStructToObject(final Element struct)
	{
		Map<String, Object> map = new HashMap<String, Object>();

		NodeList children = struct.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i)
		{
			Node child = children.item(i);
			String tag = child.getNodeName();

			// Only interested in the first element child
			if (child.getNodeType() == Node.ELEMENT_NODE &&
				tag.compareTo("member") == 0)
			{
				String memberName = null;
				Object memberValue = null;

				NodeList memberChildren = child.getChildNodes();
				for (int j = 0; j < memberChildren.getLength(); ++j)
				{
					Node memberChild = memberChildren.item(j);
					String memberTag = memberChild.getNodeName();

					// Only interested in the first element child
					if (memberChild.getNodeType() == Node.ELEMENT_NODE)
					{
						if (memberTag.compareTo("name") == 0)
						{
							// Fond the name of the dictionary entry
							memberName = memberChild.getTextContent();
						}
						else if (memberTag.compareTo("value") == 0)
						{
							// Found the value of the dictionary entry
							memberValue = xmlToObject((Element)memberChild);
						}
					}
				}

				// If the key/value pair was found -- add it to the map
				if (memberName != null && memberValue != null) {
					map.put(memberName, memberValue);
				}
			}
		}

		return map;
	}
}

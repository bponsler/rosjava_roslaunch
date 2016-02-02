package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;
import org.w3c.dom.Element;

/**
 * The BaseTag class
 *
 * This class is responsible for providing a base class for all tags.
 *
 * It contains the logic for parsing if/unless attributes defined on
 * XML tags that are capable of dynamically enabling and disabling
 * the tag.
 *
 * It also provides the logic for checking for unknown/unexpected
 * attributes defined on the tag and printing a warning if any
 * were found.
 */
public class BaseTag
{
	/** The file containing this node. */
	protected File m_parentFile;

	/** The value of the if attribute for the node. */
	protected boolean m_if;
	/** The value of the unless attribute for the node. */
	protected boolean m_unless;

	/** The list of attributes supported by the base tag. */
	private static final Attribute[] BASE_SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.If,
		Attribute.Unless,
	};

	/**
	 * Constructor
	 *
	 * Create a BaseTag object.
	 */
	public BaseTag()
	{
		m_parentFile = null;

		// Enabled by default
		m_if = true;
		m_unless = false;
	}

	/**
	 * Constructor
	 *
	 * Create an IfUnlessTag object.
	 *
	 * @param parentFile the file that contains this element
	 * @param element the XML Element
	 * @param argMap the Map of args defined in this scope
	 * @param supportedAttributes is the List of attributes supported by this tag
	 */
	public BaseTag(
			final File parentFile,
			final Element element,
			final Map<String, String> argMap,
			final Attribute[] supportedAttributes)
	{
		// Check for unknown attributes
		Attribute[] attributes =
			ArrayUtils.addAll(supportedAttributes, BASE_SUPPORTED_ATTRIBUTES);
		Util.checkForUnknownAttributes(parentFile, element, attributes);

		m_parentFile = parentFile;

		// Cannot specify both if and unless attributes
		if (element.hasAttribute(Attribute.If.val()) &&
			element.hasAttribute(Attribute.Unless.val()))
		{
			throw new RuntimeException(
				"Invalid <" + element.getTagName() + ">: cannot set both 'if' and 'unless' on the same tag");
		}

		// If will default to 'true' if unspecified, and unless will default to 'false'
		// if unspecified. Which both equate to being enabled
		m_if = RosUtil.getBoolAttribute(element, Attribute.If.val(), true, false, argMap);
		m_unless = RosUtil.getBoolAttribute(element, Attribute.Unless.val(), false, false, argMap);
	}

	/**
	 * Determine if this node is enabled or disabled based on the
	 * 'if' and 'unless' attributes
	 *
	 * @return true if the node is enabled, false otherwise
	 */
	final public boolean isEnabled()
	{
		// Both must evaluate to true in order to be enabled
		return (m_if && !m_unless);
	}

	/**
	 * Get the filename where this node is defined.
	 *
	 * @return the filename
	 */
	final public String getFilename()
	{
		if (m_parentFile != null) {
			return m_parentFile.getAbsolutePath();
		}
		else {
			return null;
		}
	}
}

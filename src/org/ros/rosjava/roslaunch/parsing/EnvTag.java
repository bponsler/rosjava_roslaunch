package org.ros.rosjava.roslaunch.parsing;

import java.io.File;

import org.ros.rosjava.roslaunch.util.Util;
import org.w3c.dom.Element;

/**
 * The EnvTag class
 *
 * This class is responsible for parsing and storing the data
 * for an "env" XML roslaunch tag.
 */
public class EnvTag
{
	/** The name of the env variable. */
	private String m_name;
	/** The value of the env variable. */
	private String m_value;
	
	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Name,
		Attribute.Value,
	};
	
	/**
	 * Constructor
	 *
	 * Create an EnvTag object from XML.
	 * 
	 * @param file is the File that contains this env tag
	 * @param env is the env XML element to parse
	 * @throws a RuntimeException if the 'name' attribute is missing
	 * @throws a RuntimeException if the 'value' attribute is missing
	 */
	public EnvTag(final File file, final Element env)
	{
		// Check for unknown attributes 
		Util.checkForUnknownAttributes(file, env, SUPPORTED_ATTRIBUTES);
		
		// The name attribute is required
		if (!env.hasAttribute(Attribute.Name.val())) {
			throw new RuntimeException("<env> tag is missing the required 'name' attribute");
		}
		
		// Value attribute is required
		if (!env.hasAttribute(Attribute.Value.val())) {
			throw new RuntimeException("<env> tag is missing the required 'value' attribute");
		}
		
		// Grab the two attributes
		m_name = env.getAttribute(Attribute.Name.val());
		m_value = env.getAttribute(Attribute.Value.val());
	}
	
	/**
	 * Get the name of the env variable.
	 * 
	 * @return the name
	 */
	public String getName()
	{
		return m_name;
	}
	
	/**
	 * Get the value of the env variable.
	 * 
	 * @return the value
	 */
	public String getValue()
	{
		return m_value;
	}
}
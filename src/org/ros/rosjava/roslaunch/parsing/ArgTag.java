package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.util.Map;

import org.w3c.dom.Element;

/**
 * The ArgTag class
 *
 * This class is responsible for parsing and storing the data
 * pertaining to an 'arg' XML tag within a roslaunch file.
 */
public class ArgTag extends BaseTag
{
	/** The name of the arg. */
	private String m_name;
	/** The value of the arg. */
	private String m_value;
	/** The documentation string for the arg. */
	private String m_doc;

	/** The arg's default value -- or null if not defined. */
	private String m_defaultValue;
	/** True if the default value attribute was defined for this arg. */
	private boolean m_hasDefaultValue;
	/** True if the default value was defined for this arg. */
	private boolean m_hasValue;

	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Name,
		Attribute.Doc,
		Attribute.Value,
		Attribute.Default,
	};

	/**
	 * Constructor
	 *
	 * Create a ArgTag object from XML.
	 *
	 * @param parentFile is the File that contains this arg
	 * @param arg is the XML Element for the arg tag
	 * @param argMap is the Map of args defined in this scope
	 * @throws a RuntimeException if the 'name' attribute is not defined
	 * @throws a RuntimeException if the 'default' and 'value' attributes
	 *         are defined at the same time
	 */
	public ArgTag(final File parentFile, final Element arg, final Map<String, String> argMap)
	{
		super(parentFile, arg, argMap, SUPPORTED_ATTRIBUTES);

		m_defaultValue = null;
		m_hasDefaultValue = false;
		m_hasValue = false;

		// An arg tag could contain the following attributes:
		//    name -- required
		//    default (optional)
		//    value (optional)
		if (!arg.hasAttribute(Attribute.Name.val())) {
			throw new RuntimeException("Invalid <arg> tag: 'name'\n");
		}

		// Args with an empty name are allowed by roslaunch
		m_name = arg.getAttribute("name");

		// Attempt to resolve the name of the arg which could contain
		// substitution arguments
		if (m_name.length() > 0) {
			m_name = SubstitutionArgs.resolve(m_name, argMap);
		}

		// Handle optional doc attribute
		m_doc = "";  // Default value is no documentation
		if (arg.hasAttribute(Attribute.Doc.val())) {
			m_doc = arg.getAttribute(Attribute.Doc.val());
			m_doc = SubstitutionArgs.resolve(m_doc, argMap);
		}

		// Check for a specific value first, as it has priority over
		// the default value
		String value = arg.getAttribute(Attribute.Value.val());
		value = SubstitutionArgs.resolve(value, argMap);

		// No value -- check for a default value
		String defaultValue = arg.getAttribute(Attribute.Default.val());
		defaultValue = SubstitutionArgs.resolve(defaultValue, argMap);

		m_defaultValue = defaultValue;
		m_hasDefaultValue = arg.hasAttribute(Attribute.Default.val());
		m_hasValue = arg.hasAttribute(Attribute.Value.val());

		// If the argument is already defined in the input args
		// map, then we use its value as our default value
		if (argMap.containsKey(m_name)) {
			defaultValue = argMap.get(m_name);
		}

		// Args cannot define both a default value and a value
		if (arg.hasAttribute(Attribute.Default.val()) &&
			arg.hasAttribute(Attribute.Value.val()))
		{
			throw new RuntimeException("Invalid <arg> tag: <arg> tag must have one and only one of value/default..");
		}
		else if (!arg.hasAttribute(Attribute.Value.val()) &&
				!arg.hasAttribute(Attribute.Default.val()))
		{
			// Neither default value or value are specified
			m_value = null;

			// If no value or default value is provided, then we use the
			// value stored in the input args, if one exists
			if (argMap.containsKey(m_name)) {
				m_value = argMap.get(m_name);
			}
		}
		else
		{
			// Either default value or value was specified:
			//     The value attribute (when specified) takes priority over the default value
			m_value = defaultValue;
			if (value.length() > 0) {
				m_value = value;
			}
		}
	}

	/**
	 * Get the name of the arg.
	 *
	 * @return the name of the arg
	 */
	public String getName()
	{
		return m_name;
	}

	/**
	 * Determine if the value attribute is defined for this arg.
	 *
	 * @return true if the value attribute is defined, false otherwise
	 */
	public boolean hasValue()
	{
		return (m_value != null);
	}

	/**
	 * Get the value for this arg.
	 *
	 * @return the value for the arg
	 */
	public String getValue()
	{
		return m_value;
	}

	/**
	 * Get the default value for this arg.
	 *
	 * @return the default value for the arg
	 */
	public String getDefaultValue()
	{
		return m_defaultValue;
	}

	/**
	 * Get the documentation string for this arg. This function
	 * will return an empty string if the doc attribute is undefined.
	 *
	 * @return the documentation string for this arg
	 */
	public String getDoc()
	{
		return m_doc;
	}

	/**
	 * Determine if this is a required arg (i.e., an arg in which the
	 * default and value attirubtes are not specified).
	 *
	 * @return true if this is a required arg, false otherwise
	 */
	public boolean isRequired()
	{
		return (!m_hasValue && !m_hasDefaultValue);
	}

	/**
	 * Determine if this is an optional arg (i.e., an arg in which the
	 * default value is defined but the value attribute is not specified).
	 *
	 * @return true if this is an optional arg, false otherwise
	 */
	public boolean isOptional()
	{
		return (!m_hasValue && m_hasDefaultValue);
	}
}

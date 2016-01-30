package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;
import org.w3c.dom.Element;

/**
 * The ParamTag class
 *
 * This class is responsible for parsing and storing the data
 * pertaining to a 'param' XML tag in a roslaunch file.
 */
public class ParamTag extends BaseTag
{
	/** The name of the param. */
	private String m_name;
	/** The type of the param. */
	private String m_type;
	/** The value of the param. */
	private String m_value;

	/** The namespace the param is in. */
	private String m_ns;

	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Name,
		Attribute.Type,
		Attribute.Value,
		Attribute.TextFile,
		Attribute.BinFile,
		Attribute.Command,
	};

	/**
	 * Constructor
	 *
	 * Create a ParamTag object from XML.
	 *
	 * @param parentFile is the File that contains this node
	 * @param param the XML Element for the param tag
	 * @param argMap the Map of args defined in this scope
	 * @param parentNs the namespace of the parent scope
	 * @throws a RuntimeExcpetion if more than one 'binfile', 'command',
	 *         'testfile', or 'value' attributes are defined at the same time
	 * @throws a RuntimeException if an invalid value for the param type is given
	 * @throws a RuntimeException if an unknown type is given.
	 * @throws a RuntimeException if an missing/invalid file is accessed
	 * @throws a RuntimeException if the executed 'command' is invalid
	 */
	public ParamTag(
			final File parentFile,
			final Element param,
			final Map<String, String> argMap,
			final String parentNs)
	{
		super(parentFile, param, argMap, SUPPORTED_ATTRIBUTES);

		m_ns = parentNs;

		if (!param.hasAttribute(Attribute.Name.val()))
		{
			throw new RuntimeException(
				"Invalid <param> tag: must specify the 'name' attribute");
		}
		m_name = param.getAttribute(Attribute.Name.val());
		m_name = SubstitutionArgs.resolve(m_name, argMap);

		boolean hasBinFile = param.hasAttribute(Attribute.BinFile.val());
		boolean hasCommand = param.hasAttribute(Attribute.Command.val());
		boolean hasTextFile = param.hasAttribute(Attribute.TextFile.val());
		boolean hasValue = param.hasAttribute(Attribute.Value.val());

		// Determine how many different 'values' were set, and enforce
		// that only one can be specified at a time
		int num = 0;
		if (hasBinFile) num++;
		if (hasCommand) num++;
		if (hasTextFile) num++;
		if (hasValue) num++;

		// Enforce the highlander rule for these attributes
		if (num != 1)
		{
			throw new RuntimeException(
				"Invalid <param> tag: <param> tag must have one and only one of " +
				"binfile/command/testfile/value.");
		}

		// Grab the optional value attribute
		m_value = null;
		if (hasValue)
		{
			m_value = param.getAttribute(Attribute.Value.val());
			m_value = SubstitutionArgs.resolve(m_value, argMap);
		}

		// Load the optional type attribute
		m_type = "string";  // defaults to a string
		if (param.hasAttribute(Attribute.Type.val()))
		{
			m_type = param.getAttribute(Attribute.Type.val());
			m_type = SubstitutionArgs.resolve(m_type, argMap);

			// Only type check the value attribute
			if (m_value != null)
			{
				if (m_type.compareTo("str") == 0 || m_type.compareTo("string") == 0)
				{
					// Do nothing, it's already a string
					m_type = "string";  // Actual xml type
				}
				else if (m_type.compareTo("int") == 0)
				{
					// Attempt to convert to an integer
					try {
						Integer.parseInt(m_value);
					}
					catch (Exception e) {
						throw new RuntimeException(
							"Invalid <param> tag: '" + m_name + "' invalid 'int' type given: '" + m_value + "'");
					}
				}
				else if (m_type.compareTo("double") == 0)
				{
					// Attempt to convert to an double
					try {
						Double.parseDouble(m_value);
					}
					catch (Exception e) {
						throw new RuntimeException(
							"Invalid <param> tag: '" + m_name + "' invalid 'double' type given: '" + m_value + "'");
					}
				}
				else if (m_type.compareTo("bool") == 0 || m_type.compareTo("boolean") == 0)
				{
					m_type = "boolean";  // Actual XML type

					// Attempt to convert to an bool
					try {
						Boolean.parseBoolean(m_value);
					}
					catch (Exception e) {
						throw new RuntimeException(
							"Invalid <param> tag: '" + m_name + "' invalid 'bool' type given: '" + m_value + "'");
					}
				}
				else
				{
					throw new RuntimeException(
						"Invalid <param> tag: '" + m_name + "' invalid type attribute: '" + m_type + "'");
				}
			}
		}

		// Load the optional textfile
		if (hasTextFile)
		{
			String textfile = param.getAttribute(Attribute.TextFile.val());
			textfile = SubstitutionArgs.resolve(textfile, argMap);

			// Load the file
			File f = new File(textfile);
			if (!f.exists() && !f.isFile())
			{
				throw new RuntimeException(
					"Invalid <param> tag: No such file or directory: " + textfile);
			}

			// Read the contents of the file
			try {
				byte[] encoded = Files.readAllBytes(Paths.get(textfile));
				m_value = new String(encoded, Charset.defaultCharset());
			}
			catch (IOException e)
			{
				throw new RuntimeException(
					"Invalid <param> tag: failed to read textfile: " + textfile);
			}
		}

		// Load the optional binfile
		if (hasBinFile)
		{
			// TODO: finish supporting this
			PrintLog.error(
				"WARNING: the param tag attribute 'binfile' is not yet supported!");
		}

		// Load the optional command
		if (hasCommand)
		{
			String command = param.getAttribute(Attribute.Command.val());
			command = SubstitutionArgs.resolve(command, argMap);

			// Calls to xacro that pass a filename surrounded by single quotes
			// will fail -- this fixes that issue
			if (command.contains("xacro")) {
				command = command.replace("'", "");  // Remove single quotes
			}

			// Don't allow empty commands (roslaunch throws a more difficult
			// to interpret error than this)
			if (command.length() == 0)
			{
				throw new RuntimeException(
					"Invalid <param> tag: invalid command: '" + "'");
			}

			// Execute the command
			m_value = Util.getCommandOutput(command);
		}
	}

	/**
	 * Get the name of the param
	 *
	 * @return the name of the param
	 */
	public String getParamName()
	{
		return m_name;
	}

	/**
	 * Get the fully resolved name of the param.
	 *
	 * @return the fully resolved name of the param
	 */
	public String getResolvedName()
	{
		return RosUtil.joinNamespace(m_ns, m_name);
	}

	/**
	 * Get the type of this param.
	 *
	 * @return the type of this param
	 */
	public String getType()
	{
		return m_type;
	}

	/**
	 * Get the value of the param.
	 *
	 * @return the value of the param
	 */
	public String getValue()
	{
		return m_value;
	}
}

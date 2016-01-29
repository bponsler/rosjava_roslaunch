package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;
import org.w3c.dom.Element;
import org.yaml.snakeyaml.Yaml;

// NOTE: SnakeYaml downloaded from:
//       http://repo1.maven.org/maven2/org/yaml/snakeyaml/1.16/
//
// The SnakeYaml project website: https://bitbucket.org/asomov/snakeyaml
// does not contain the JAR, so it was grabbed from the maven repository

/**
 * The RosParamTag class
 *
 * This class is responsible for parsing and storing the data
 * associated with a 'rosparam' XML tag within a roslaunch file.
 */
public class RosParamTag
{
	/** The command attribute for this rosparam tag. */
	private String m_command;
	/** The file attribute for this rosparam tag. */
	private String m_file;
	/** The param name for this rosparam tag. */
	private String m_param;
	/** The namespace for this rosparam tag. */
	private String m_ns;
	/** The subs value attribute for this rosparam tag. */
	private boolean m_substValue;
	/** The YAML object for this rosparam data. */
	private Object m_yamlObj;
	/** The YAML content for this rosparam data. */
	private String m_yamlContent;
	
	/** The list of command types RosParamTags support. */
	private List<String> m_supportedCommands;
	
	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Command,
		Attribute.File,
		Attribute.Param,
		Attribute.Ns,
		Attribute.Subst_Value,
	};
	
	/**
	 * The Command enumeration
	 *
	 * This enumeration contains identifiers for RosParamTags.
	 */
	private enum Command
	{
		/** The command to load a parameter. */
		Load,
		/** The command to delete a parameter. */
		Delete,
		/** The command to dump all parameters. */
		Dump;
		
		/**
		 * Get the XML text value for this Command.
		 *
		 * @return the XML text value
		 */
		public String val()
		{
			return this.name().toLowerCase();
		} 
	};
	
	/**
	 * Constructor
	 *
	 * Create a RosParamTag object from XML.
	 *
	 * @param parentFile is the File the contains this rosparam tag
	 * @param rosparam is the XML Element for this rosparam tag
	 * @param argMap is the Map of args defined in this scope
	 * @param parentNs is the namespace for the parent of this tag
	 * @throws a RuntimeException if an unknown command value is given
	 * @throws a RuntimeException if the 'file' attribute is specified
	 *         with the 'delete' command
	 * @throws a RuntimeException if a missing/invalid file is given
	 * @throws a RuntimeException if the YAML content fails to parse
	 * @throws a RuntimeException if the 'dump' command is given an invalid file
	 */
	public RosParamTag(
			final File parentFile,
			final Element rosparam,
			final Map<String, String> argMap,
			final String parentNs)
	{
		// Check for unknown attributes 
		Util.checkForUnknownAttributes(parentFile, rosparam, SUPPORTED_ATTRIBUTES);
				
		boolean isYamlDict = false;  // Assume no YAML dictionary by default
		
		// Add list of supported commands
		m_supportedCommands = new ArrayList<String>();
		m_supportedCommands.add(Command.Load.val());
		m_supportedCommands.add(Command.Dump.val());
		m_supportedCommands.add(Command.Delete.val());
		
		// command: optional: load|dump|delete
		m_command = Command.Load.val();  // default command is load
		if (rosparam.hasAttribute(Attribute.Command.val()))
		{
			m_command = rosparam.getAttribute(Attribute.Command.val());
			m_command = SubstitutionArgs.resolve(m_command, argMap);
			
			if (!m_supportedCommands.contains(m_command))
			{
				throw new RuntimeException(
					"Invalid <rosparam> tag: invalid command value: " + m_command);
			}
		}
		
		// File cannot be specified for the delete command					
		if (isDeleteCommand() && rosparam.hasAttribute(Attribute.File.val()))
		{
			throw new RuntimeException(
				"Invalid <rosparam> tag: the 'file' attribute is " +
					"invalid with the 'delete' command.");
		}
		
		// Grab the namespace
		m_ns = RosUtil.addNamespace(rosparam, parentNs, argMap);
		
		// file (only for load or dump commands)
		m_file = null;
		if (isLoadCommand() || isDumpCommand())
		{
			if (rosparam.hasAttribute(Attribute.File.val()))
			{
				m_file = rosparam.getAttribute(Attribute.File.val());
				m_file = SubstitutionArgs.resolve(m_file, argMap);
				
				if (isLoadCommand())
				{
					//// load command
						
					// Make sure the file exists
					File f = new File(m_file);
					if (!f.exists() || !f.isFile())
					{
						throw new RuntimeException(
							"Invalid <rosparam> tag: file does not exist: '" + m_file + "'");
					}
					
					// Load the YAML data from the file
					Yaml yaml = new Yaml();
					
					// Load the YAML data
					InputStream inputStream;
					try
					{
						inputStream = new FileInputStream(m_file);
						
						m_yamlObj = yaml.load(inputStream);
						
						// Determine if this is a YAML dictionary
						isYamlDict = isYamlDict(m_yamlObj);
						
						// Store the yaml content
						m_yamlContent = yaml.dump(m_yamlObj);
					}
					catch (Exception e) {
						throw new RuntimeException(
							"Invalid <rosparam> tag: failed to parse YAML file: " +
							e.getMessage() + "\nFile is: " + m_file);
					}
				}
				else
				{
					//// dump command
					
					if (m_file.length() == 0)
					{
						// roslaunch does not throw this error, but it throws a
						// different error when this happens, that is more
						// difficult to understand
						throw new RuntimeException(
							"Invalid <rosparam> tag: dump command given " +
							"invalid empty file");
					}
				}
			}
			else if (isDumpCommand())
			{
				// roslaunch does not throw this error, but it throws a
				// different error when this happens, that is more
				// difficult to understand
				throw new RuntimeException(
					"Invalid <rosparam> tag: dump command requires " +
					"the 'file' attribute to be specified");
			}
		}
		
		// The load command is the only time the content gets parsed
		// and it's only when the file attribute is not specified
		// because the file attribute takes precedence over the text content
		if (isLoadCommand() && !rosparam.hasAttribute(Attribute.File.val()))
		{
			// subst_value (Allows use of substitution args in the YAML text)
			m_substValue = RosUtil.getBoolAttribute(
					rosparam, Attribute.Subst_Value.val(), false, false, argMap);
			
			// Handle the YAML text content
			m_yamlContent = rosparam.getTextContent();
			if (m_yamlContent.length() > 0)
			{
				// Resolve any substitution args in the YAML text, if desired
				if (m_substValue) {
					m_yamlContent = SubstitutionArgs.resolve(m_yamlContent, argMap);
				}
				
				Yaml yaml = new Yaml();
				
				try {
					m_yamlObj = yaml.load(m_yamlContent);
				}
				catch (Exception e)
				{
					throw new RuntimeException(
						"Invalid <rosparam> tag: failed to parse " +
						"YAML content: " + e.getMessage() + "\nYAML is: " + m_yamlContent);
				}
						
				
				// Determine if this is a YAML dictionary
				isYamlDict = isYamlDict(m_yamlObj);
			}
		}
		
		// param (optional only if the YAML text describes a dictionary)
		m_param = "";
		if (rosparam.hasAttribute(Attribute.Param.val()))
		{
			m_param = rosparam.getAttribute(Attribute.Param.val());
			m_param = SubstitutionArgs.resolve(m_param, argMap);
		}
		else if (isLoadCommand() && m_yamlContent.length() > 0 && !isYamlDict)
		{
			// Param must be specified for the load command when the
			// data is a non-dictionary type 
			throw new RuntimeException(
				"Invalid <rosparam> tag: the 'param' attribute must be set " +
				"for non-dictionary values");
		}
	}
	
	/**
	 * Determine if this rosparam is loading a parameter.
	 *
	 * @return true if it is loading a parameter, false otherwise
	 */
	public boolean isLoadCommand()
	{
		return (m_command.compareTo(Command.Load.val()) == 0);
	}
	
	/**
	 * Determine if this rosparam is dumping all parameters.
	 *
	 * @return true if it is dumping parameters, false otherwise
	 */
	public boolean isDumpCommand()
	{
		return (m_command.compareTo(Command.Dump.val()) == 0);
	}
	
	/**
	 * Determine if this rosparam is deleting a parameter.
	 *
	 * @return true if it is deleting a parameter, false otherwise
	 */
	public boolean isDeleteCommand()
	{
		return (m_command.compareTo(Command.Delete.val()) == 0);
	}
	
	/**
	 * Get the fully resolved name for this rosparam.
	 *
	 * @return the fully resolved name
	 */
	public String getResolvedName()
	{
		String param = m_param;
		if (param == null) param = "";  // Avoid null params
		
		return RosUtil.joinNamespace(m_ns, param);
	}
	
	/**
	 * Get the filename that contains this rosparam.
	 *
	 * @return the filename
	 */
	public String getFile()
	{
		return m_file;
	}
	
	/**
	 * Get the namespace of this rosparam.
	 *
	 * @return the namespace
	 */
	public String getNamespace()
	{
		return m_ns;
	}
	
	/**
	 * Get the defined name of the rosparam.
	 *
	 * @return the param name
	 */
	public String getParam()
	{
		return m_param;
	}
	
	/**
	 * Get the YAML content for this rosparam.
	 *
	 * @return the YAML content
	 */
	public String getYamlContent()
	{
		return m_yamlContent.trim();
	}
	
	/**
	 * Get the YAML object for this rosparam.
	 *
	 * @return the YAML object
	 */
	public Object getYamlObject()
	{
		return m_yamlObj;
	}

	/**
	 * Determine if the given YAML object is a dictionary (i.e., Map).
	 *
	 * @param yamlObj the YAML object
	 * @return true if it is a dictionary (i.e., Map)
	 */
	private boolean isYamlDict(final Object yamlObj)
	{
		try
		{
			@SuppressWarnings({ "unchecked", "unused" })
			Map<String, String> map = (Map<String, String>)yamlObj;
			return true;  // Have a YAML dictionary!
		}
		catch (ClassCastException e) {
			return false;  // Not a dictionary
		}
	}
}

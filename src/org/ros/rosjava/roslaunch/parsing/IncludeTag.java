package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The IncludeTag class
 *
 * This class is responsible for parsing and storing the data
 * pertaining to an 'include' XML tag within a roslaunch file.
 */
public class IncludeTag extends BaseTag
{
	/** The included LaunchFile. */
	private LaunchFile m_launchFile;
	/** The namespace for this include tag. */
	private String m_ns;
	/** The value of the clear params tag. */
	private boolean m_clearParams;

	/** The List ArgTags passed to the included launch file. */
	private List<ArgTag> m_argTags;
	/** The Map of args defined in this include's scope. */
	private Map<String, String> m_args;
	/** The Map of env variables defined in this include's scope. */
	private Map<String, String> m_env;
	/** The Map of remap tags defined in this include's scope. */
	private Map<String, String> m_remaps;

	/** Set of launch file names that are direct parents of this LaunchFile. */
	private Set<String> m_parentFilenames;

	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.File,
		Attribute.Clear_Params,
	};

	/**
	 * Constructor
	 *
	 * Create an IncludeTag object from XML.
	 *
	 * @param parentFile is the File that contains this include tag
	 * @param include is the XML Element for the include tag
	 * @param argMap is the Map of args defined in this scope
	 * @param env is the Map of env variables defined in this scope
	 * @param remaps is the Map of remap tags defined in this scope
	 * @param parentNs is the parent namespace of the include
	 * @param parentFilenames is the Set of filenames that are direct parents
	 *        of this include
	 * @throws a RuntimeException if the 'file' attribute is not defined
	 * @throws a RuntimeException if the given 'file' attribute is a
	 *         missing/invalid file
	 * @throws a RuntimeException if the clear params attribute is used
	 *         and the ns attribute is not set
	 * @throws a RuntimeException if the contents of the include fail to parse
	 * @throws a RuntimeException if the 'if' and 'unless' attributes are set at the same time
	 */
	public IncludeTag(
			final File parentFile,
			final Element include,
			final Map<String, String> argMap,
			final Map<String, String> env,
			final Map<String, String> remaps,
			final String parentNs,
			final Set<String> parentFilenames)
	{
		super(parentFile, include, argMap, SUPPORTED_ATTRIBUTES);

		// Add the direct parent to the set
		m_parentFilenames = new HashSet<String>(parentFilenames);
		m_parentFilenames.add(parentFile.getAbsolutePath());

		m_argTags = new ArrayList<ArgTag>();
		m_args = new HashMap<String, String>(argMap);
		m_env = new HashMap<String, String>(env);  // Store incoming environment
		m_remaps = new HashMap<String, String>(remaps);

		// Include tags may have the following attributes:
		//    file -- required
		//    ns  -- optional
		//    clear_param -- optional
		if (!include.hasAttribute(Attribute.File.val())) {
			throw new RuntimeException("Invalid <include> tag: missing 'file' attribute");
		}

		String file = include.getAttribute(Attribute.File.val());

		// Resolve any and all substitution args in the filename
		file = SubstitutionArgs.resolve(file, argMap);

		// Make sure the include file actually exists
		if (!includeExists(file)) {
			throw new RuntimeException("Invalid <include> tag: No such file or directory: " + file);
		}

		m_ns = RosUtil.addNamespace(include, parentNs, argMap);

		// Grab the clear params attribute
		m_clearParams = RosUtil.getBoolAttribute(
				include, Attribute.Clear_Params.val(), false, true, argMap);

		// Make sure ns is specified when using clear_params
		if (m_clearParams && !include.hasAttribute(Attribute.Ns.val())) {
			throw new RuntimeException("'ns' attribute must be set in order to use 'clear_params'");
		}

		// Parse all of the child tags
		parseChildren(include);

		// Make sure the file exists before trying to load it
		File fd = new File(file);
		if (!fd.exists() || !fd.isFile())
		{
			throw new RuntimeException(
				"Invalid <include> tag: No such file or directory: " + file);
		}

		// Check if the launch file being included is already one of
		// our own ancestors (i.e., if there is a cycle in the launch graph)
		if (m_parentFilenames.contains(file))
		{
			String msg = "ERROR: there is a cycle in the launch graph.\n";
			msg += "The file [" + m_parentFile.getAbsolutePath() + "] includes one ";
			msg += "of its ancestors [" + file + "]";
			throw new RuntimeException(msg);
		}

		// Load the included file as a LaunchFile object and pass to
		// it the current args and environment
		m_launchFile = new LaunchFile(fd, m_args, m_env, m_remaps);
		m_launchFile.setParents(m_parentFilenames);  // Pass parents to our children
		m_launchFile.setNamespace(m_ns);  // Pass our namespace to our children

		// Attempt to parse the file
		try {
			m_launchFile.parseFile();
		}
		catch (Exception e) {
			throw new RuntimeException(
				"Invalid <include> tag: [" + fd.getAbsolutePath() + "]: " + e.getMessage());
		}

		// Cannot specify both if and unless attributes
		if (include.hasAttribute(Attribute.If.val()) &&
			include.hasAttribute(Attribute.Unless.val()))
		{
			throw new RuntimeException(
				"Invalid <include> tag: cannot set both 'if' and 'unless' on the same tag");
		}

		// Only check for unused args if the include is enabled
		if (isEnabled())
		{
			// Compare the set of args defined in the included launch file to
			// the set of includes passed to the include to determine if any of
			// the includes passed to the launch file were not actually used
			List<String> launchArgNames = m_launchFile.getArgNames();
			String unusedArgs = "";
			for (ArgTag arg : m_argTags)
			{
				String argName = arg.getName();
				if (!launchArgNames.contains(argName))
				{
					if (unusedArgs.length() > 0) unusedArgs += ", ";  // Add comma separator
					unusedArgs += argName;
				}
			}

			// Throw an error about the unused args, if there are any
			if (unusedArgs.length() > 0) {
				throw new RuntimeException(
					"unused args [" + unusedArgs + "] for include of [" + file + "]");
			}
		}
	}

	/**
	 * Get the LaunchFile that was included by this include tag.
	 *
	 * @return the included LaunchFile
	 */
	public LaunchFile getLaunchFile()
	{
		return m_launchFile;
	}

	/**
	 * Get the namespace for this include tag.
	 *
	 * @return the namespace
	 */
	public String getNamespace()
	{
		return m_ns;
	}

	/**
	 * Get the value of the clear params attribute for this include tag.
	 *
	 * @return the clear params attribute
	 */
	public boolean getClearParams()
	{
		return m_clearParams;
	}

	/**
	 * Determine if the given include file exists.
	 *
	 * @param filename the include filename
	 * @return true if it exists, false otherwise
	 */
	private boolean includeExists(final String filename)
	{
		String temp = filename;

		// Handle the linux tilde to the home directory so that we have
		// an absolute path (otherwise java will consider it to be a relative
		// path from wherever the executable is called)
		temp = temp.replaceFirst("^~", System.getProperty("user.home"));

		// Attempt to open the file
		File f = new File(temp);

		// Log an error if the file does not exist, or is not a file
		return (f.exists() && f.isFile());
	}

	/**
	 * Parse the child tags for the include
	 *
	 * @param include the XML Element for the include tag
	 */
	private void parseChildren(final Element include)
	{
		NodeList children = include.getChildNodes();
		for (int index = 0; index < children.getLength(); ++index)
		{
			Node node = children.item(index);
			String childTag = node.getNodeName();

			// Ignore #comment and #text tags, all tags we're interested in
			// should be element nodes
			if (!childTag.startsWith("#") && node.getNodeType() == Node.ELEMENT_NODE)
			{
				Element child = (Element)node;

				// Handle all possible child tags
				if (childTag.compareTo(Tag.Arg.val()) == 0)
				{
					ArgTag arg = new ArgTag(m_parentFile, child, m_args);
					if (arg.isEnabled())
					{
						m_argTags.add(arg);
						m_args.put(arg.getName(), arg.getValue());
					}
				}
				else if (childTag.compareTo(Tag.Env.val()) == 0)
				{
					EnvTag envTag = new EnvTag(m_parentFile, child, m_args);
					if (envTag.isEnabled()) {
						m_env.put(envTag.getName(), envTag.getValue());
					}
				}
				else
				{
					PrintLog.error(
						"WARN: unrecognized '" + childTag + "' tag in <include> tag");
				}
			}
		}
	}
}

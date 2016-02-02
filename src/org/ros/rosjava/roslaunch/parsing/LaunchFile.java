package org.ros.rosjava.roslaunch.parsing;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * The LaunchFile class
 *
 * This class is responsible for parsing and storing the data
 * pertaining to a launch file. This includes parsing all:
 *
 *     - include tags
 *     - node tags
 *     - group tags
 *     - remap tags
 *     - param tags
 *     - rosparam tags
 *     - arg tags
 *     - env tags
 *     - machine tags
 */
public class LaunchFile
{
	/** The File for this LaunchFile. */
	private final File m_file;
	/** The filename for this launch file. */
	private final String m_filename;
	/** The namespace for this launch file. */
	private String m_ns;

	/** The List of arg tags defined in this launch file. */
	private List<ArgTag> m_args;
	/** The Map of args defined in the scope of this launch file. */
	private Map<String, String> m_argMap;
	/** The Map of env variables defined in the scope of this launch file. */
	private Map<String, String> m_env;
	/** the List of IncludeTags defined by this launch file. */
	private List<IncludeTag> m_includes;
	/** the List of NodeTags defined by this launch file. */
	private List<NodeTag> m_nodes;
	/** the List of GroupTag defined by this launch file. */
	private List<GroupTag> m_groups;
	/** the Map of remap tags defined by this launch file. */
	private Map<String, String> m_remaps;
	/** the List of ParamTags defined by this launch file. */
	private List<ParamTag> m_params;
	/** the List of RosParamTags defined by this launch file. */
	private List<RosParamTag> m_rosParams;
	/** the List of MachineTags defined by this launch file. */
	private List<MachineTag> m_machines;
	/** the List of Test nodes defined by this launch file. */
	private List<TestTag> m_tests;

	/** Whether or not the launch tag is enabled based on the if/unless attributes. */
	private boolean m_enabled;
	/** The deprecated warning for this launch file. */
	private String m_deprecated;

	/** Set of launch file names that are direct parents of this LaunchFile. */
	private Set<String> m_parentFilenames;

	/** The list of attributes supported by the launch tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Deprecated,
		Attribute.Filename,
	};

	public LaunchFile()
	{
		m_file = null;
		m_filename = null;
		m_ns = "";

		m_enabled = true;
		m_deprecated = "";  // Not deprecated

		m_args = new ArrayList<ArgTag>();
		m_argMap = new HashMap<String, String>();
		m_env = new HashMap<String, String>();
		m_includes = new ArrayList<IncludeTag>();
		m_nodes = new ArrayList<NodeTag>();
		m_groups = new ArrayList<GroupTag>();
		m_remaps = new HashMap<String, String>();
		m_params = new ArrayList<ParamTag>();
		m_rosParams = new ArrayList<RosParamTag>();
		m_machines = new ArrayList<MachineTag>();
		m_tests = new ArrayList<TestTag>();
		m_parentFilenames = new HashSet<String>();
	}

	/**
	 * Constructor
	 *
	 * Create a LaunchFile object from XML.
	 *
	 * @param file the File to parse
	 */
	public LaunchFile(final File file)
	{
		m_file = file;
		m_filename = (file != null) ? m_file.getAbsolutePath() : null;
		m_ns = "";

		m_enabled = true;
		m_deprecated = "";  // Not deprecated

		m_args = new ArrayList<ArgTag>();
		m_argMap = new HashMap<String, String>();
		m_env = new HashMap<String, String>();
		m_includes = new ArrayList<IncludeTag>();
		m_nodes = new ArrayList<NodeTag>();
		m_groups = new ArrayList<GroupTag>();
		m_remaps = new HashMap<String, String>();
		m_params = new ArrayList<ParamTag>();
		m_rosParams = new ArrayList<RosParamTag>();
		m_machines = new ArrayList<MachineTag>();
		m_tests = new ArrayList<TestTag>();
		m_parentFilenames = new HashSet<String>();
	}

	/**
	 * Constructor
	 *
	 * Create a LaunchFile object.
	 *
	 * @param file the File to parse
	 * @param args the Map of ags defined in the parent's scope
	 * @param env the Map of env variables defined in the parent's scope
	 * @param remaps the Map of remap tags defined in the parent's scope
	 */
	public LaunchFile(
			final File file,
			final Map<String, String> args,
			final Map<String, String> env,
			final Map<String, String> remaps)
	{
		this(file);

		m_argMap = new HashMap<String, String>(args);
		m_env = new HashMap<String, String>(env);
		m_remaps = new HashMap<String, String>(remaps);
	}

	/**
	 * Get the name of this launch file (e.g., the basename of
	 * the file, my_launch.launch)
	 *
	 * @return the name of this LaunchFile
	 */
	public String getName()
	{
		if (m_file != null) {
			return m_file.getName();
		}
		else {
			return "";
		}
	}

	/**
	 * Get the filename where this LaunchFile is stored.
	 *
	 * @return the filename
	 */
	public String getFilename()
	{
		return m_filename;
	}

	/**
	 * Determine if this LaunchFile is enabled or not.
	 *
	 * @return true if it is enabled, false otherwise
	 */
	public boolean isEnabled()
	{
		return m_enabled;
	}

	/**
	 * Get the deprecated value, or an empty string if none is defined.
	 *
	 * @return the deprecated value
	 */
	public String getDeprecated()
	{
		return m_deprecated;
	}

	/**
	 * Add the give Map of args defined to the scope of this LaunchFile.
	 *
	 * @param args the Map of args to add
	 */
	public void addArgMap(final Map<String, String> args)
	{
		m_argMap.putAll(args);
	}

	/**
	 * Set the namespace for this LaunchFile.
	 *
	 * @param ns the namespace
	 */
	public void setNamespace(final String ns)
	{
		m_ns = ns;
	}

	/**
	 * Set the file names that are direct parents of this launch file.
	 *
	 * @param parentFilenames The set of parent filenames
	 */
	public void setParents(final Set<String> parentFilenames)
	{
		m_parentFilenames = parentFilenames;
	}

	/**
	 * Get the List of IncludeTags defined by this LaunchFile.
	 *
	 * @return the List of IncludeTags
	 */
	public List<IncludeTag> getIncludes()
	{
		return m_includes;
	}

	/**
	 * Get the List of NodeTags defined by this LaunchFile.
	 *
	 * @return the List of NodeTags
	 */
	public List<NodeTag> getNodes()
	{
		return m_nodes;
	}

	/**
	 * Get the List of GroupTags defined by this LaunchFile.
	 *
	 * @return the List of GroupTags
	 */
	public List<GroupTag> getGroups()
	{
		return m_groups;
	}

	/**
	 * Get the List of ParamTags defined by this LaunchFile.
	 *
	 * @return the List of ParamTags
	 */
	public List<ParamTag> getParameters()
	{
		return m_params;
	}

	/**
	 * Get the List of RosParamTags defined by this LaunchFile.
	 *
	 * @return the List of RosParamTags
	 */
	public List<RosParamTag> getRosParams()
	{
		return m_rosParams;
	}

	/**
	 * Get the List of MachineTags defined by this LaunchFile.
	 *
	 * @return the List of MachineTags
	 */
	public List<MachineTag> getMachines()
	{
		return m_machines;
	}

	/**
	 * Get the List of ArgTags defined by this LaunchFile.
	 *
	 * @return the List of ArgTags
	 */
	public List<ArgTag> getArgs()
	{
		return m_args;
	}

	/**
	 * Get the List of names of all ArgTags defined by this LaunchFile.
	 *
	 * @return the List of ArgTag names
	 */
	public List<String> getArgNames()
	{
		List<String> names = new ArrayList<String>();
		for (ArgTag arg : m_args) {
			names.add(arg.getName());
		}
		return names;
	}

	/**
	 * Print all of the nodes defined in the launch file tree.
	 */
	public void printNodes()
	{
		for (IncludeTag include : m_includes)
		{
			if (include.isEnabled()) {
				include.getLaunchFile().printNodes();
			}
		}

		for (GroupTag group : m_groups)
		{
			if (group.isEnabled()) {
				group.getLaunchFile().printNodes();
			}
		}

		for (NodeTag node : m_nodes)
		{
			if (node.isEnabled()) {
				PrintLog.info(node.getResolvedName());
			}
		}
	}

	/**
	 * Print all of the files defined in this launch file tree.
	 */
	public void printFiles()
	{
		if (m_filename != null) {
			PrintLog.info(m_filename);
		}

		for (IncludeTag include : m_includes)
		{
			if (include.isEnabled()) {
				include.getLaunchFile().printFiles();
			}
		}

		for (GroupTag group : m_groups)
		{
			if (group.isEnabled()) {
				group.getLaunchFile().printFiles();
			}
		}
	}

	/**
	 * Find a NodeTag within this launch file tree by its fully resolved name.
	 *
	 * @param nodeName the fully resolved name of the node
	 * @return the NodeTag, or null if it could not be found
	 */
	public NodeTag findNode(final String nodeName)
	{
		for (NodeTag node : m_nodes)
		{
			if (node.isEnabled())
			{
				if (node.getResolvedName().compareTo(nodeName) == 0) {
					return node;
				}
			}
		}

		for (IncludeTag include : m_includes)
		{
			if (include.isEnabled())
			{
				NodeTag node = include.getLaunchFile().findNode(nodeName);
				if (node != null) {
					return node;
				}
			}
		}

		for (GroupTag group : m_groups)
		{
			if (group.isEnabled())
			{
				NodeTag node = group.getLaunchFile().findNode(nodeName);
				if (node != null) {
					return node;
				}
			}
		}

		return null;
	}

	/**
	 * Parse the LaunchFile.
	 *
	 * @throws a RuntimeException if an missing/invalid file is given
	 * @throws a RuntimeException if parsing fails
	 */
	public void parseFile()
	{
		Document doc;
		try
		{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			// Keep the document builder from printing error messages
			dBuilder.setErrorHandler(new ErrorHandler() {
			    @Override
			    public void fatalError(SAXParseException exception) throws SAXException {
			        // Do not print fatal errors
			    }

				@Override
				public void error(SAXParseException arg0) throws SAXException {
					// Do not log errors
				}

				@Override
				public void warning(SAXParseException arg0) throws SAXException {
					// Do not log warning
				}
			});

			doc = dBuilder.parse(m_file);
		}
		catch (Exception e)
		{
			throw new RuntimeException(
				"ERROR: failed to parse launch file: [" + m_filename + "]");
		}

		if (doc != null)
		{
			Element launch = doc.getDocumentElement();
			if (launch.getNodeName().compareTo(Tag.Launch.val()) != 0)
			{
				throw new RuntimeException(
						"Invalid roslaunch XML syntax: no root <" + Tag.Launch.val() + "> tag");
			}

			// Parse all attributes
			parseAttributes(launch);

			// Parse all children -- only when enabled
			if (isEnabled()) {
				parseChildren(launch);
			}
		}
	}

	/**
	 * Parse the given XML String.
	 *
	 * @param data the XML data String
	 */
	public void parseString(final String data)
	{
		Document doc;
		try
		{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			// Keep the document builder from printing error messages
			dBuilder.setErrorHandler(new ErrorHandler() {
			    @Override
			    public void fatalError(SAXParseException exception) throws SAXException {
			        // Do not print fatal errors
			    }

				@Override
				public void error(SAXParseException arg0) throws SAXException {
					// Do not log errors
				}

				@Override
				public void warning(SAXParseException arg0) throws SAXException {
					// Do not log warning
				}
			});

			InputStream is = new ByteArrayInputStream(data.getBytes());
			doc = dBuilder.parse(is);
			is.close();
		}
		catch (Exception e)
		{
			throw new RuntimeException(
				"ERROR: failed to parse launch file from string: " + e.getMessage());
		}

		if (doc != null)
		{
			Element launch = doc.getDocumentElement();
			if (launch.getNodeName().compareTo(Tag.Launch.val()) != 0)
			{
				throw new RuntimeException(
						"Invalid roslaunch XML syntax: no root <" + Tag.Launch.val() + "> tag");
			}

			// Parse all attributes
			parseAttributes(launch);

			// Parse all children -- only when enabled
			if (isEnabled()) {
				parseChildren(launch);
			}
		}
	}

	/**
	 * Parse all of the XML attributes for this launch tag.
	 *
	 * @param launch the XML Element for this launch tag
	 */
	public void parseAttributes(final Element launch)
	{
		// Create a base tag to parse the base tag attributes
		BaseTag tag = new BaseTag(m_file, launch, m_argMap, SUPPORTED_ATTRIBUTES);

		// Store whether or not the launch tag is enabled
		// NOTE: roslaunch does not honor the if/unless attributes of the
		//       launch tag, although it parses them correctly. Here we
		//       are going to honor those values
		m_enabled = tag.isEnabled();

		// Parse the deprecated attribute
		m_deprecated = "";  // Not deprecated
		if (launch.hasAttribute(Attribute.Deprecated.val()))
		{
			// NOTE: The deprecated string does not resolve arguments
			m_deprecated = launch.getAttribute(Attribute.Deprecated.val());
		}
	}

	/**
	 * Parse all of the child XML tags for this LaunchFile.
	 *
	 * @param launch the XML Element for this launch file
	 */
	public void parseChildren(final Element launch)
	{
		// Iterate over all the children of the launch tag
		NodeList children = launch.getChildNodes();
		for (int index = 0; index < children.getLength(); ++index)
		{
			Node node = children.item(index);
			String childTag = node.getNodeName();

			// Ignore #comment and #text tags, all tags we're interested in
			// should be element nodes
			if (!childTag.startsWith("#") && node.getNodeType() == Node.ELEMENT_NODE)
			{
				Element child = (Element)node;

				if (childTag.compareTo(Tag.Arg.val()) == 0)
				{
					ArgTag arg = new ArgTag(
							m_file, child, m_argMap);
					if (arg.isEnabled())
					{
						m_args.add(arg);
						m_argMap.put(arg.getName(), arg.getValue());
					}
				}
				else if (childTag.compareTo(Tag.Env.val()) == 0)
				{
					EnvTag env = new EnvTag(
							m_file, child, m_argMap);
					if (env.isEnabled()) {
						m_env.put(env.getName(), env.getValue());
					}
				}
				else if (childTag.compareTo(Tag.Group.val()) == 0)
				{
					GroupTag group = new GroupTag(
							m_file, child, m_argMap, m_env, m_remaps, m_ns);
					if (group.isEnabled()) {
						m_groups.add(group);
					}
				}
				else if (childTag.compareTo(Tag.Include.val()) == 0)
				{
					IncludeTag include = new IncludeTag(
							m_file,
							child,
							m_argMap,
							m_env,
							m_remaps,
							m_ns,
							m_parentFilenames);
					if (include.isEnabled()) {
						m_includes.add(include);
					}
				}
				else if (childTag.compareTo(Tag.Machine.val()) == 0)
				{
					MachineTag machine = new MachineTag(
							m_file, child, m_argMap, m_env);
					if (machine.isEnabled()) {
						m_machines.add(machine);
					}
				}
				else if (childTag.compareTo(Tag.Node.val()) == 0)
				{
					NodeTag nodeTag = new NodeTag(
							m_file, child, m_argMap, m_env, m_remaps, m_ns);
					if (nodeTag.isEnabled()) {
						m_nodes.add(nodeTag);
					}
				}
				else if (childTag.compareTo(Tag.Param.val()) == 0)
				{
					ParamTag param = new ParamTag(
							m_file, child, m_argMap, m_ns);
					if (param.isEnabled()) {
						m_params.add(param);
					}
				}
				else if (childTag.compareTo(Tag.Remap.val()) == 0)
				{
					RemapTag remap = new RemapTag(
							m_file, child, m_argMap);
					if (remap.isEnabled()) {
						m_remaps.put(remap.getFrom(), remap.getTo());
					}
				}
				else if (childTag.compareTo(Tag.RosParam.val()) == 0)
				{
					RosParamTag rosParam = new RosParamTag(
							m_file, child, m_argMap, m_ns);
					if (rosParam.isEnabled()) {
						m_rosParams.add(rosParam);
					}
				}
				else if (childTag.compareTo(Tag.Test.val()) == 0)
				{
					TestTag test = new TestTag(
							m_file, child, m_argMap, m_env, m_ns);
					if (test.isEnabled()) {
						m_tests.add(test);
					}
				}
				else {
					PrintLog.error("WARNING: unrecognized tag: [" + childTag + "]");
				}
			}
		}
	}
}

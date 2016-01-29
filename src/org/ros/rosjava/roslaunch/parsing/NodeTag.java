package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The NodeTag class
 *
 * This class is responsible for parsing and storing the data
 * associated with a 'node' XML tag within a roslaunch file.
 */
public class NodeTag
{
	/** The log value for the output attribute. */
	private static final String LOG_OUTPUT = "log";
	/** The screen value for the output attribute. */
	private static final String SCREEN_OUTPUT = "screen";
	
	/** The cwd value to use the node's executable directory. */
	private static final String NODE_CWD = "node";
	/** The cwd value to use the current working directory. */
	private static final String CWD_CWD = "cwd";
	/** The cwd value to use ROS_HOME. */
	private static final String ROS_HOME_CWD = "ros-home";
	/** The cwd value to use ROS_ROOT. */
	private static final String ROS_ROOT_CWD = "ros-root";
	
	/** The file containing this node. */
	private File m_file;
	
	/** The package this node lives in. */
	private String m_package;
	/** The type of node this is. */
	private String m_type;
	/** The name of the node. */
	private String m_name;
	
	/** The list of command line arguments to add to the node. */
	private String m_args;
	/** The machine name where this node should be launched. */
	private String m_machineName;
	/** True if the node should be respawned when it dies. */
	private boolean m_respawn;
	/** The delay before respawning the node if it dies. */
	private float m_respawnDelay;
	/** True if the node is required, false if it is not. */
	private boolean m_required;
	/** The namespace for the node. */
	private String m_ns;
	/** The clear params value for the node. */
	private boolean m_clearParams;
	/** The output value for the node. */
	private String m_output;
	/** The current working directory for the node. */
	private String m_cwd;
	/** The launch prefix for the node. */
	private String m_launchPrefix;
	/** The value of the if attribute for the node. */
	private boolean m_if;
	/** The value of the unless attribute for the node. */
	private boolean m_unless;

	/** The MachineTag associated with this node. */
	private MachineTag m_machine;
	
	/** The List of ParamTags defined by this node. */
	private List<ParamTag> m_params;
	/** The Map of env variables defined by this node. */
	private Map<String, String> m_env;
	/** The Map of remapped topics defined in this node's scope. */
	private Map<String, String> m_remappings;
	/** The List of RosParamTags defined by this node. */
	private List<RosParamTag> m_rosParams;
	
	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Name,
		Attribute.Pkg,
		Attribute.Type,
		Attribute.Name,
		Attribute.Args,
		Attribute.Machine,
		Attribute.Respawn,
		Attribute.Respawn_Delay,
		Attribute.Required,
		Attribute.Ns,
		Attribute.Clear_Params,
		Attribute.Output,
		Attribute.Cwd,
		Attribute.Launch_Prefix,
		Attribute.If,
		Attribute.Unless,
	};
	
	/**
	 * Constructor
	 *
	 * Create a NodeTag object from XML.
	 *
	 * @param parentFile is the File that includes this node
	 * @param node is the XML element for the node tag
	 * @param argMap is the Map of args defined in this scope
	 * @param env is the Map of env variables defined in this scope
	 * @param remappings is the Map remap tags defined in this scope
	 * @param parentNs is the namespace of the node's parent
	 * @throws a RuntimeException if the 'name' attribute is missing
	 * @throws a RuntimeException if the 'name' attribute contains a namespace
	 * @throws a RuntimeException if the 'pkg' attribute missing
	 * @throws a RuntimeException if the 'type' attribute missing
	 * @throws a RuntimeException if the 'respawn-delay' attribute has
	 *         an invalid float value
	 * @throws a RuntimeException if the 'output' attribute has a value
	 *         that is not either 'log' or 'screen'
	 * @throws a RuntimeException if the 'cwd' attribute is not either
	 *         'ROS_HOME' or 'node'
	 * @throws a RuntimeException if both the 'if' and 'unless' attributes
	 *         are defined at the same time
	 */
	public NodeTag(
			final File parentFile,
			final Element node,
			final Map<String, String> argMap,
			final Map<String, String> env,
			final Map<String, String> remappings,
			final String parentNs)
	{
		// Check for unknown attributes 
		Util.checkForUnknownAttributes(parentFile, node, SUPPORTED_ATTRIBUTES);
				
		m_file = parentFile;
		m_params = new ArrayList<ParamTag>();
		m_env = new HashMap<String, String>();  // TODO: do we need to inherit?
		m_remappings = new HashMap<String, String>(remappings);
		m_rosParams = new ArrayList<RosParamTag>();
	
		m_machine = null;  // No machine by default
		
		// The name attribute is required
		if (!node.hasAttribute(Attribute.Name.val())) {
			throw new RuntimeException("<node> tag is missing required attribute: 'name'");
		}
		
		// Grab the name and attempt to resolve it value
		m_name = node.getAttribute(Attribute.Name.val());
		m_name = SubstitutionArgs.resolve(m_name, argMap);
		
		// Node names cannot contain a namespace
		if (m_name.contains("/")) {
			throw new RuntimeException("Invalid <node> tag: node name cannot contain a namespace");
		}
		
		// The package attribute is required
		if (!node.hasAttribute(Attribute.Pkg.val())) {
			throw new RuntimeException("<node> tag is missing required attribute: 'pkg'");
		}
		
		// Grab the package and attempt to resolve it
		m_package = node.getAttribute(Attribute.Pkg.val());
		m_package = SubstitutionArgs.resolve(m_package, argMap);
		
		// The type attribute is required
		if (!node.hasAttribute(Attribute.Type.val())) {
			throw new RuntimeException("<node> tag is missing required attribute: 'type'");
		}
		
		// Grab the node type and attempt to resolve it
		m_type = node.getAttribute(Attribute.Type.val());
		m_type = SubstitutionArgs.resolve(m_type, argMap);
		
		// Grab the optional args to pass to the node when running it
		m_args = "";
		if (node.hasAttribute(Attribute.Args.val())) {
			m_args = node.getAttribute(Attribute.Args.val());
			m_args = SubstitutionArgs.resolve(m_args, argMap);
			
			// Remove whitespace around the args, if there is any
			m_args = m_args.trim();
		}
		
		// Grab the optional machine name
		m_machineName = "";
		if (node.hasAttribute(Attribute.Machine.val())) {
			m_machineName = node.getAttribute(Attribute.Machine.val());
			m_machineName = SubstitutionArgs.resolve(m_machineName, argMap);
		}
		
		// Handle the optional respawn attribute
		m_respawn = RosUtil.getBoolAttribute(
				node, Attribute.Respawn.val(), false, false, argMap);
		
		// Respawn delay gets parsed regardless of the respawn attribute
		m_respawnDelay = 0;
		if (node.hasAttribute(Attribute.Respawn_Delay.val()))
		{
			String delay = node.getAttribute(Attribute.Respawn_Delay.val());
			delay = SubstitutionArgs.resolve(delay, argMap);
			
			try
			{
				m_respawnDelay = Float.parseFloat(delay);
			}
			catch (Exception e)
			{
				throw new RuntimeException(
					"Invalid <node> tag: invalid float value for respawn_delay: " + delay);
			}
		}
		
		// Get the optional required attribute
		m_required = RosUtil.getBoolAttribute(
				node, Attribute.Required.val(), false, false, argMap);
		
		// The respawn and required attributes cannot be enabled simultaneously
		if (m_respawn && m_required) {
			throw new RuntimeException(
				"Invalid <node> tag: respawn and required cannot both be set to true");
		}
		
		// Get the optional namespace attribute
		m_ns = RosUtil.addNamespace(node, parentNs, argMap);
		
		// Get the clear params attribute
		m_clearParams = RosUtil.getBoolAttribute(
				node, Attribute.Clear_Params.val(), false, true, argMap);
		
		// Get the optional output attribute
		m_output = LOG_OUTPUT;
		if (node.hasAttribute(Attribute.Output.val()))
		{
			m_output = node.getAttribute(Attribute.Output.val());
			m_output = SubstitutionArgs.resolve(m_output, argMap);
			
			// This is case sensitive
			if (m_output.compareTo(LOG_OUTPUT) != 0 &&
				m_output.compareTo(SCREEN_OUTPUT) != 0)
			{
				throw new RuntimeException(
					"Invalid <node> tag: output must be one of '" +
				    LOG_OUTPUT + "', '" + SCREEN_OUTPUT + "'.");
			}
		}
		
		// Get the optional cwd attribute
		m_cwd = ROS_HOME_CWD;  // Default value
		if (node.hasAttribute(Attribute.Cwd.val()))
		{
			m_cwd = node.getAttribute(Attribute.Cwd.val());
			m_cwd = SubstitutionArgs.resolve(m_cwd, argMap);
			
			if (m_cwd.compareTo(EnvVar.ROS_HOME.name()) != 0 &&
				m_cwd.compareTo(ROS_HOME_CWD) != 0 &&
				m_cwd.compareTo(EnvVar.ROS_ROOT.name()) != 0 &&
				m_cwd.compareTo(ROS_ROOT_CWD) != 0 &&
				m_cwd.compareTo(NODE_CWD) != 0 &&
				m_cwd.compareTo(CWD_CWD) != 0)
			{
				throw new RuntimeException(
					"Invalid <node> tag: cwd must be one of 'ros-home', 'ros-root', 'cwd', 'node'");
			}
		}
		
		// Get the optional launch prefix tag
		m_launchPrefix = "";
		if (node.hasAttribute(Attribute.Launch_Prefix.val()))
		{
			m_launchPrefix = node.getAttribute(Attribute.Launch_Prefix.val());
			m_launchPrefix = SubstitutionArgs.resolve(m_launchPrefix, argMap);
		}
		
		// Cannot specify both if and unless attributes
		if (node.hasAttribute(Attribute.If.val()) &&
			node.hasAttribute(Attribute.Unless.val()))
		{
			throw new RuntimeException("Invalid <node>: cannot set both 'if' and 'unless' on the same tag");
		}
		
		// If will default to 'true' if unspecified, and unless will default to 'false'
		// if unspecified. Which both equate to being enabled
		m_if = RosUtil.getBoolAttribute(node, Attribute.If.val(), true, false, argMap);
		m_unless = RosUtil.getBoolAttribute(node, Attribute.Unless.val(), false, false, argMap);
		
		// Parse all child tags
		parseChildren(node, argMap);
	}
	
	/**
	 * Parse all of the child tags for the node.
	 *
	 * @param node is the XML Element for the node tag
	 * @param argMap is the Map of args defined in this scope
	 */
	private void parseChildren(final Element node, final Map<String, String> argMap)
	{
		String privateNs = getResolvedName();
		
		NodeList children = node.getChildNodes();
		for (int index = 0; index < children.getLength(); ++index)
		{
			Node element = children.item(index);
			String childTag = element.getNodeName();
			
			// Ignore #comment and #text tags, all tags we're interested in
			// should be element nodes
			if (!childTag.startsWith("#") && element.getNodeType() == Node.ELEMENT_NODE)
			{
				Element child = (Element)element;
				
				// Handle all possible child tags
				if (childTag.compareTo(Tag.Env.val()) == 0)
				{
					EnvTag envTag = new EnvTag(m_file, child);
					m_env.put(envTag.getName(), envTag.getValue());
				}
				else if (childTag.compareTo(Tag.Param.val()) == 0)
				{
					ParamTag param = new ParamTag(
							m_file, child, argMap, privateNs);
					m_params.add(param);
				}
				else if (childTag.compareTo(Tag.Remap.val()) == 0)
				{
					RemapTag remap = new RemapTag(
							m_file, child, argMap);
					m_remappings.put(remap.getFrom(), remap.getTo());
				}
				else if (childTag.compareTo(Tag.RosParam.val()) == 0)
				{
					RosParamTag param = new RosParamTag(
							m_file, child, argMap, m_ns);
					m_rosParams.add(param);
				}
				else
				{
					System.err.println(
						"WARN: unrecognized '" + childTag + "' tag in <node> tag");
				}
			}
		}
	}
	
	/**
	 * Get the filename where this node is defined.
	 *
	 * @return the filename
	 */
	public String getFilename()
	{
		return m_file.getAbsolutePath();
	}
	
	/**
	 * Determine if this node is enabled or disabled based on the
	 * 'if' and 'unless' attributes
	 *
	 * @return true if the node is enabled, false otherwise
	 */
	public boolean isEnabled()
	{
		// Both must evaluate to true in order to be enabled
		return (m_if && !m_unless);
	}
	
	/**
	 * Get the name of the package this node lives in.
	 *
	 * @return the package name
	 */
	public String getPackage()
	{
		return m_package;
	}
	
	/**
	 * Get the type of this node.
	 *
	 * @return the node type
	 */
	public String getType()
	{
		return m_type;
	}
	
	/**
	 * Get the name of this node.
	 *
	 * @return the name of this node
	 */
	public String getName()
	{
		return m_name;
	}
	
	/**
	 * Get the fully resolved name of this node.
	 *
	 * @return the fully resolved name
	 */
	public String getResolvedName()
	{
		return RosUtil.joinNamespace(m_ns, m_name);
	}
	
	/**
	 * Get the array of command line arguments specified for this node.
	 *
	 * @return the command line arguments
	 */
	public String[] getArgs()
	{
		// Split on an empty string returns an array with a single
		// element, which is not correct. this handles that case
		// and returns an empty array
		if (m_args.length() > 0) {
			return m_args.split(" ");
		}
		else {
			return new String[0];
		}
	}
	
	/**
	 * Get the MachineTag associated with this node.
	 *
	 * @return the MachineTag
	 */
	public MachineTag getMachine()
	{
		return m_machine;
	}
	
	/**
	 * Set the MachineTag associated with this node.
	 *
	 * @param machine the MachineTag
	 */
	public void setMachine(final MachineTag machine)
	{
		m_machine = new MachineTag(machine);
	}
	
	/**
	 * Get the name of the machine where this node should be launched.
	 *
	 * @return the machine name
	 */
	public String getMachineName()
	{
		return m_machineName;
	}
	
	/**
	 * Determine if this node should be respawned when it dies.
	 *
	 * @return true if the node should be respawned
	 */
	public boolean shouldRespawn()
	{
		return m_respawn;
	}

	/**
	 * Get the delay (in seconds) before the node should be
	 * respawned.
	 *
	 * @return the respawn delay
	 */
	public float getRespawnDelay()
	{
		return m_respawnDelay;
	}
	
	/**
	 * Determine if this node is required.
	 *
	 * @return true if it is required, false otherwise
	 */
	public boolean isRequired()
	{
		return m_required;
	}
	
	/**
	 * Get the namespace for this node.
	 *
	 * @return the namespace
	 */
	public String getNamespace()
	{
		return m_ns;
	}
	
	/**
	 * Get the value of the clear params attribute.
	 *
	 * @return the value of the clear params attribute
	 */
	public boolean getClearParams()
	{
		return m_clearParams;
	}
	
	/**
	 * Get the output type for this node ('log' or 'screen')
	 *
	 * @return the output type
	 */
	public String getOutput()
	{
		return m_output;
	}
	
	/**
	 * Determine if this node is a 'screen' output type.
	 *
	 * @return true if 'screen' output
	 */
	public boolean isScreenOutput()
	{
		return (m_output.compareTo(SCREEN_OUTPUT) == 0);
	}
	
	/**
	 * Get the path to the current working directory for this node.
	 *
	 * @return the path to the current working directory for this node
	 */
	public File getCwd()
	{
		// Determine where the working directory should be based on
		// the value of the attribute
		File workingDir = RosUtil.getRosHome();
		if (m_cwd.compareTo(NODE_CWD) == 0)
		{
			// Path to the node's executable directory
			File executable = getExecutable();
			workingDir = executable.getParentFile();
		}
		else if (m_cwd.compareTo(ROS_ROOT_CWD) == 0) {
			workingDir = RosUtil.getRosRoot();
		}
		else if (m_cwd.compareTo(CWD_CWD) == 0) {
			workingDir = Util.getCurrentWorkingDirectory();
		}
		
		return workingDir;
	}
	
	/**
	 * Get the launch prefix for this node.
	 *
	 * @return the launch prefix
	 */
	public String getLaunchPrefix()
	{
		return m_launchPrefix;
	}
	
	/**
	 * Get the List of ParamTags defined by this node.
	 *
	 * @return the List of ParamTags
	 */
	public List<ParamTag> getParams()
	{
		return m_params;
	}
	
	/**
	 * Get the List of RosParamTags defined by this node.
	 *
	 * @return the List of RosParamTags
	 */
	public List<RosParamTag> getRosParams()
	{
		return m_rosParams;
	}
	
	/**
	 * Get the Map of remapped topics defined by this node.
	 *
	 * @return the Map of remapped topics
	 */
	public Map<String, String> getRemappings() 
	{
		return m_remappings;
	}
	
	/**
	 * Get the Map of env variables defined by this node.
	 *
	 * @return the Map of env variables
	 */
	public Map<String, String> getEnv()
	{
		return m_env;
	}
	
	/**
	 * Get the path to the executable for this node.
	 *
	 * @return the path to the executable
	 */
	public File getExecutable()
	{
		String pkg = getPackage();
		String type = getType();
		
		// Look up the path to the node executable
		String path = RosUtil.findResource(pkg, type);
		if (path == null || path.length() == 0) {
			throw new RuntimeException(
				"Could not locate node type '" + type + "' in package: " + pkg);
		}
		
		return new File(path);
	}
}

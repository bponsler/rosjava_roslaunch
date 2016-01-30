package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.ros.rosjava.roslaunch.util.RosUtil;
import org.w3c.dom.Element;

/**
 * The GroupTag class
 *
 * This class is responsible for parsing and storing the data
 * contained within a "group" XML tag in roslaunch files.
 */
public class GroupTag extends BaseTag
{
	/** The namespace for the group. */
	private String m_ns;
	/** The value of clear params attribute. */
	private boolean m_clearParams;

	/** The internals of the group parsed as a separate launch file. */
	private LaunchFile m_launchFile;

	/** The map of remap tags defined by the group. */
	private Map<String, String> m_remaps;

	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
        Attribute.Ns,
		Attribute.Clear_Params,
	};

	/**
	 * Constructor
	 *
	 * Create a GroupTag object from XML.
	 *
	 * @param parentFile the File that contains this group
	 * @param argMap the current Map of arg values for resolving substitution args
	 * @param remaps the current Map of defined remap tags in this scope
	 * @param parentNs the parent scope's namespace
	 * @throws a RuntimeException if 'if' and 'unless' are both set on the group
	 */
	public GroupTag(
			final File parentFile,
			final Element group,
			final Map<String, String> argMap,
			final Map<String, String> env,
			final Map<String, String> remaps,
			final String parentNs)
	{
		super(parentFile, group, argMap, SUPPORTED_ATTRIBUTES);

		m_remaps = new HashMap<String, String>(remaps);

		// Get the optional namespace attribute
		m_ns = RosUtil.addNamespace(group, parentNs, argMap);

		// Get the clear params attribute
		m_clearParams = RosUtil.getBoolAttribute(
				group, Attribute.Clear_Params.val(), false, true, argMap);

		// NS must be specified if trying to use clear params
		if (m_clearParams && !group.hasAttribute(Attribute.Ns.val())) {
			throw new RuntimeException(
				"Invalid <group> tag: the 'ns' attribute must be set in order to use clear_params");
		}

		// Cannot specify both if and unless attributes
		if (group.hasAttribute(Attribute.If.val()) &&
			group.hasAttribute(Attribute.Unless.val()))
		{
			throw new RuntimeException(
				"Invalid <group>: cannot set both 'if' and 'unless' on the same tag");
		}

		// If will default to 'true' if unspecified, and unless will default to 'false'
		// if unspecified. Which both equate to being enabled
		m_if = RosUtil.getBoolAttribute(group, Attribute.If.val(), true, false, argMap);
		m_unless = RosUtil.getBoolAttribute(group, Attribute.Unless.val(), false, false, argMap);

		// The rest of the group is basically a launch file, so
		// create a launch file and parse it the same way
		m_launchFile = new LaunchFile(parentFile, argMap, env, m_remaps);
		m_launchFile.setNamespace(m_ns);  // Pass namespace down to children
		m_launchFile.parseChildren(group);
	}

	/**
	 * Get the namespace for this group
	 *
	 * @return the namespace
	 */
	public String getNamespace()
	{
		return m_ns;
	}

	/**
	 * Get the value of the clear params attribute for this group.
	 *
	 * @return the clear params value
	 */
	public boolean getClearParams()
	{
		return m_clearParams;
	}

	/**
	 * Get the launch file that represents the internals of the group.
	 *
	 * @return the internal launch file
	 */
	public LaunchFile getLaunchFile()
	{
		return m_launchFile;
	}
}

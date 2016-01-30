package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.util.Map;

import org.ros.rosjava.roslaunch.util.RosUtil;
import org.w3c.dom.Element;

/**
 * The TestTag class
 *
 * This class is responsible for parsing and storing data
 * pertaining to the 'test' XML tag within a roslaunch file.
 */
public class TestTag extends BaseTag
{
	/** The package where this test node lives. */
	private String m_package;
	/** The type of this test node. */
	private String m_type;
	/** The name of this test node. */
	private String m_name;

	/** The command line arguments to pass to this test node. */
	private String m_args;
	/** The namespace for this test node. */
	private String m_ns;
	/** The value of the clear params attribute. */
	private boolean m_clearParams;
	/** The value of the cwd attribute. */
	private String m_cwd;
	/** The launch prefix for this test node. */
	private String m_launchPrefix;
	/** The number of times to retry the test before it's considered a failure. */
	private int m_retry;
	/** The number of seconds before the test is considered a failure. */
	private float m_timeLimit;

	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Pkg,
		Attribute.Type,
		Attribute.Test_Name,
		Attribute.Args,
		Attribute.Ns,
		Attribute.Clear_Params,
		Attribute.Cwd,
		Attribute.Launch_Prefix,
		Attribute.Retry,
		Attribute.Time_Limit,
	};

	/**
	 * Constructor
	 *
	 * Create a TestTag object from XML.
	 *
	 * @param parentFile is the File that contains this TestTag
	 * @param test is the XML Element for this test tag
	 * @param argMap is the Map of args defined in this scope
	 * @param env is the Map of env variables defined in this scope
	 * @param parentNs is the namespace of the parent
	 * @throws a RuntimeException if the 'test-name' attribute is missing
	 * @throws a RuntimeException if the 'test-name' contains a namespace
	 * @throws a RuntimeException if the 'pkg' attribute is missing
	 * @throws a RuntimeException if the 'type' attribute is missing
	 * @throws a RuntimeException if the 'cwd' attribute is not 'ROS_HOME' or 'node'
	 * @throws a RuntimeException if the 'time-limit' attribute is an invalid float value
	 * @throws a RuntimeException if both the 'if' and 'unless' attributes are
	 *         defined at the same time
	 */
	public TestTag(
			final File parentFile,
			final Element test,
			final Map<String, String> argMap,
			final Map<String, String> env,
			final String parentNs)
	{
		super(parentFile, test, argMap, SUPPORTED_ATTRIBUTES);

		// Stop parsing if the tag is not included
		if (!isEnabled()) return;

		// The name attribute is required
		if (!test.hasAttribute(Attribute.Test_Name.val())) {
			throw new RuntimeException(
				"<test> tag is missing required attribute: 'test-name'");
		}

		// Grab the name and attempt to resolve it value
		m_name = test.getAttribute(Attribute.Name.val());
		m_name = SubstitutionArgs.resolve(m_name, argMap);

		// Node names cannot contain a namespace
		if (m_name.contains("/")) {
			throw new RuntimeException("Invalid <test> tag: node name cannot contain a namespace");
		}

		// The package attribute is required
		if (!test.hasAttribute(Attribute.Pkg.val())) {
			throw new RuntimeException("<test> tag is missing required attribute: 'pkg'");
		}

		// Grab the package and attempt to resolve it
		m_package = test.getAttribute(Attribute.Pkg.val());
		m_package = SubstitutionArgs.resolve(m_package, argMap);

		// The type attribute is required
		if (!test.hasAttribute(Attribute.Type.val())) {
			throw new RuntimeException("<test> tag is missing required attribute: 'type'");
		}

		// Grab the node type and attempt to resolve it
		m_type = test.getAttribute(Attribute.Type.val());
		m_type = SubstitutionArgs.resolve(m_type, argMap);

		// Grab the optional args to pass to the node when running it
		m_args = "";
		if (test.hasAttribute(Attribute.Args.val())) {
			m_args = test.getAttribute(Attribute.Args.val());
			m_args = SubstitutionArgs.resolve(m_args, argMap);
		}

		// Get the optional namespace attribute
		m_ns = RosUtil.addNamespace(test, parentNs, argMap);

		// Get the clear params attribute
		m_clearParams = RosUtil.getBoolAttribute(
				test, Attribute.Clear_Params.val(), false, true, argMap);

		// Get the optional cwd attribute
		m_cwd = "";
		if (test.hasAttribute(Attribute.Cwd.val()))
		{
			m_cwd = test.getAttribute(Attribute.Cwd.val());
			m_cwd = SubstitutionArgs.resolve(m_cwd, argMap);

			if (m_cwd.compareTo("ROS_HOME") != 0 &&
				m_cwd.compareTo("node") != 0)
			{
				throw new RuntimeException(
					"Invalid <test> tag: cwd must be one of 'ROS_HOME', 'node'");
			}
		}

		// Get the optional launch prefix tag
		m_launchPrefix = "";
		if (test.hasAttribute(Attribute.Launch_Prefix.val()))
		{
			m_launchPrefix = test.getAttribute(Attribute.Launch_Prefix.val());
			m_launchPrefix = SubstitutionArgs.resolve(m_launchPrefix, argMap);
		}

		// Get the optional retry attribute
		m_retry = 0;
		if (test.hasAttribute(Attribute.Retry.val()))
		{
			String retry = test.getAttribute(Attribute.Retry.val());
			retry = SubstitutionArgs.resolve(retry, argMap);

			try
			{
				m_retry = Integer.parseInt(retry);
			}
			catch (Exception e)
			{
				throw new RuntimeException(
						"Invalid <test> tag: invalid int value for retry: " + retry);
			}
		}

		// Get the optional time limit attribute
		m_timeLimit = 60;
		if (test.hasAttribute(Attribute.Time_Limit.val()))
		{
			String limit = test.getAttribute(Attribute.Time_Limit.val());
			limit = SubstitutionArgs.resolve(limit, argMap);

			try
			{
				m_timeLimit = Float.parseFloat(limit);
			}
			catch (Exception e)
			{
				throw new RuntimeException(
						"Invalid <test> tag: invalid float value for time-limit: " + limit);
			}
		}

		// Cannot specify both if and unless attributes
		if (test.hasAttribute(Attribute.If.val()) &&
			test.hasAttribute(Attribute.Unless.val()))
		{
			throw new RuntimeException("Invalid <test>: cannot set both 'if' and 'unless' on the same tag");
		}

		// TODO: support all child tags
		//     TODO: env
		//     TODO: remap
		//     TODO: param
		//     TODO: rosparam
	}

	/**
	 * Get the name of the package where this test node lives.
	 *
	 * @return the name of the package
	 */
	public String getPackage()
	{
		return m_package;
	}

	/**
	 * Get the type of this test node.
	 *
	 * @return the test node type
	 */
	public String getType()
	{
		return m_type;
	}

	/**
	 * Get the name of this test node.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return m_name;
	}

	/**
	 * Get the command line arguments for this test node.
	 *
	 * @return the command line arguments
	 */
	public String getArgs()
	{
		return m_args;
	}

	/**
	 * Get the namespace for this test node.
	 *
	 * @return the namespace
	 */
	public String getNamespace()
	{
		return m_ns;
	}

	/**
	 * Get the value of the clear params attribute for this test node.
	 *
	 * @return the clear params value
	 */
	public boolean getClearParams()
	{
		return m_clearParams;
	}

	/**
	 * Get the value of the cwd attribute for this test node.
	 *
	 * @return the cwd value
	 */
	public String getCwd()
	{
		return m_cwd;
	}

	/**
	 * Get the launch prefix for this test node.
	 *
	 * @return the launch prefix
	 */
	public String getLaunchPrefix()
	{
		return m_launchPrefix;
	}

	/**
	 * Get the number of attempts this test node should be retried
	 * before it is considered a failure.
	 *
	 * @return the number of retry attempts
	 */
	public int getRetry()
	{
		return m_retry;
	}

	/**
	 * Get the timeout for this test node before it is considered
	 * a failure.
	 *
	 * @return the timeout
	 */
	public float getTimeLimit()
	{
		return m_timeLimit;
	}
}

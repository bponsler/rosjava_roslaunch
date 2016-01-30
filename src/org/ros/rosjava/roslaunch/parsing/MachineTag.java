package org.ros.rosjava.roslaunch.parsing;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.w3c.dom.Element;

/**
 * The MachineTag class
 *
 * This class is responsible for parsing and storing the data
 * associated with a 'machine' XML tag within a roslaunch file.
 */
public class MachineTag extends BaseTag
{
	/** The name of the machine. */
	private String m_name;
	/** The address of the machine. */
	private String m_address;
	/** The value of the env loader attribute for the machine. */
	private String m_envLoader;
	/** The value of the default attribute for the machine. */
	private String m_default;
	/** The username for the machine. */
	private String m_username;
	/** The password for the machine. */
	private String m_password;
	/** The SSH port for the machine. */
	private int m_sshPort;
	/** The timeout for the machine. */
	private float m_timeout;

	/** The list of attributes supported by this tag. */
	private static final Attribute[] SUPPORTED_ATTRIBUTES = new Attribute[]{
		Attribute.Name,
		Attribute.Address,
		Attribute.Env_Loader,
		Attribute.Username,
		Attribute.Password,
		Attribute.Default,
		Attribute.SSH_Port,
		Attribute.Timeout,
	};

	/**
	 * Constructor
	 *
	 * Create a MachineTag object given its name and address.
	 *
	 * @param name the name of the machine
	 * @param address the address of the machine
	 */
	public MachineTag(final String name, final String address)
	{
		super();

		m_name = name;
		m_address = address;

		m_envLoader = "";
		m_default = "";
		m_username = "";
		m_password = "";
		m_sshPort = 22;
		m_timeout = 10;
	}

	/**
	 * Copy constructor
	 *
	 * Create a copy of a MachineTag object.
	 *
	 * @param other the MachineTag to copy
	 */
	public MachineTag(final MachineTag other)
	{
		super();

		m_name = other.m_name;
		m_address = other.m_address;
		m_envLoader = other.m_envLoader;
		m_default = other.m_default;
		m_username = other.m_username;
		m_password = other.m_password;
		m_sshPort = other.m_sshPort;
		m_timeout = other.m_timeout;
	}

	/**
	 * Constructor
	 *
	 * Create a MachineTag object from XML.
	 *
	 * @param parentFile is the file containing this machine tag
	 * @param machine is the XML Element for the machine tag
	 * @param argMap is the Map of args defined in this scope
	 * @throws a RuntimeException if the 'name' attribute is missing
	 * @throws a RuntimeException if the 'address' attribute is missing
	 * @throws a RuntimeException if the 'default' attribute has an
	 *         invalid boolean value
	 * @throws a RuntimeException if an invalid int value is given
	 *         for the 'ssh-port' attribute
	 * @throws a RuntimeException if an invalid float value is given
	 *         for the 'timeout' attribute
	 */
	public MachineTag(
			final File parentFile,
			final Element machine,
			final Map<String, String> argMap)
	{
		super(parentFile, machine, argMap, SUPPORTED_ATTRIBUTES);

		// Stop parsing if the tag is not included
		if (!isEnabled()) return;

		// name (required)
		if (!machine.hasAttribute(Attribute.Name.val())) {
			throw new RuntimeException(
				"Invalid <machine> tag: missing the required attribute: 'name'");
		}
		m_name = machine.getAttribute(Attribute.Name.val());
		m_name = SubstitutionArgs.resolve(m_name, argMap);

		// address (required)
		if (!machine.hasAttribute(Attribute.Address.val())) {
			throw new RuntimeException(
				"Invalid <machine> tag: missing the required attribute: 'address'");
		}
		m_address = machine.getAttribute(Attribute.Address.val());
		m_address = SubstitutionArgs.resolve(m_address, argMap);

		// env-loader (optional)
		m_envLoader = "";
		if (machine.hasAttribute(Attribute.Env_Loader.val()))
		{
			m_envLoader = machine.getAttribute(Attribute.Env_Loader.val());
			m_envLoader = SubstitutionArgs.resolve(m_envLoader, argMap);
		}

		// default (optional)
		m_default = "";  // Unspecified by default
		if (machine.hasAttribute(Attribute.Default.val()))
		{
			m_default = machine.getAttribute(Attribute.Default.val());
			m_default = SubstitutionArgs.resolve(m_default, argMap);

			if (m_default.compareTo("true") != 0 &&
				m_default.compareTo("false") != 0 &&
				m_default.compareTo("never") != 0)
			{
				throw new RuntimeException(
					"Invalid <machine> tag: invalid boolean 'default' value: " + m_default);
			}
		}

		// user (optional)
		m_username = "";
		if (machine.hasAttribute(Attribute.Username.val()))
		{
			m_username = machine.getAttribute(Attribute.Username.val());
			m_username = SubstitutionArgs.resolve(m_username, argMap);
		}

		// password (optional)
		m_password = "";
		if (machine.hasAttribute(Attribute.Password.val()))
		{
			m_password = machine.getAttribute(Attribute.Password.val());
			m_password = SubstitutionArgs.resolve(m_password, argMap);
		}

		// Support ssh-port attribute (optional, default 22) which is
		// not mentioned in online documentation, but is supported in code
		m_sshPort = 22;
		if (machine.hasAttribute(Attribute.SSH_Port.val()))
		{
			String portStr = machine.getAttribute(Attribute.SSH_Port.val());
			portStr = SubstitutionArgs.resolve(portStr, argMap);

			try {
				m_sshPort = Integer.parseInt(portStr);
			}
			catch (Exception e)
			{
				throw new RuntimeException(
					"Invalid <machine> tag: invalid int value for " +
					"'ssh-port' attribute: " + portStr);
			}
		}

		// timeout (optional)
		m_timeout = 10;  // Default
		if (machine.hasAttribute(Attribute.Timeout.val()))
		{
			String timeout = machine.getAttribute(Attribute.Timeout.val());
			timeout = SubstitutionArgs.resolve(timeout, argMap);

			try
			{
				m_timeout = Float.parseFloat(timeout);
			}
			catch (Exception e)
			{
				throw new RuntimeException(
					"Invalid <machine> tag: invalid float 'timeout' value: " + timeout);
			}
		}
	}

	/**
	 * Get the name of the machine.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return m_name;
	}

	/**
	 * Get the address for this machine.
	 *
	 * @return the address
	 */
	public String getAddress()
	{
		return m_address;
	}

	/**
	 * Get the value of the env-loader attribute.
	 *
	 * @return the env-loader value
	 */
	public String getEnvLoader()
	{
		return m_envLoader;
	}

	/**
	 * Get the value of the default attribute.
	 *
	 * @return the default value
	 */
	public String getDefault()
	{
		return m_default;
	}

	/**
	 * Get the username for this machine.
	 *
	 * @return the username
	 */
	public String getUsername()
	{
		return m_username;
	}

	/**
	 * Get the password for this machine.
	 *
	 * @return the password
	 */
	public String getPassword()
	{
		return m_password;
	}

	/**
	 * Get the SSH port for this machine.
	 *
	 * @return the SSH port
	 */
	public int getSshPort()
	{
		return m_sshPort;
	}

	/**
	 * Get the timeout for this machine
	 *
	 * @return the timeout
	 */
	public float getTimeout()
	{
		return m_timeout;
	}

	/**
	 * Get the InetAddress for this machine.
	 *
	 * @return the InetAddress
	 * @throws a RuntimeException if the address could not be resolved
	 */
	public InetAddress getInetAddress()
	{
		InetAddress machineAddress;
		try {
			machineAddress = InetAddress.getByName(m_address);
		}
		catch (UnknownHostException e)
		{
			throw new RuntimeException(
				"Could not resolve hostname for machine '" + m_name +
				"': [" + m_address + "]");
		}

		return machineAddress;
	}

	/**
	 * Determine if two MachineTags are equal. This checks all
	 * attributes except the 'name' and 'timeout' attributes.
	 *
	 * @return true if the MachineTags are equal
	 */
	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null) return false;
	    if (getClass() != obj.getClass()) return false;

	    // Cast to an actual machine
	    final MachineTag other = (MachineTag)obj;

	    /////// Compare all items, except for name, and default

	    //// Address
	    if (!m_address.equals(other.m_address)) {
	    	return false;
	    }

	    //// Env loader
	    if (!m_envLoader.equals(other.m_envLoader)) {
	    	return false;
	    }

	    //// SSH port
	    if (m_sshPort != other.m_sshPort) {
	    	return false;
		}

	    //// Username
	    if (!m_username.equals(other.m_username)) {
	    	return false;
	    }

	    //// Password
	    if (!m_password.equals(other.m_password)) {
	    	return false;
	    }

	    //// Timeout
	    if (m_timeout != other.m_timeout) {
	    	return false;
	    }

	    // Otherwise the machine tags are the same
		return true;
	}
}

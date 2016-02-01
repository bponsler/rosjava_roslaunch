package org.ros.rosjava.roslaunch.parsing;

/**
 * The Attribute enumeration
 *
 * This enumeration provides the names for all XML attributes
 * that can be contained in launch file XML code.
 */
public enum Attribute
{
	Address,
	Args,
	BinFile,
	Clear_Params,
	Command,
	Cwd,
	Default,
	Deprecated,
	Doc,
	Env_Loader(true),
	File,
	Filename,
	From,
	If,
	Launch_Prefix(true),
	Machine,
	Name,
	Ns,
	Output,
	Param,
	Password,
	Pkg,
	Required,
	Respawn,
	Respawn_Delay,
	Retry,
	SSH_Port(true),
	Subst_Value,
	Test_Name(true),
	TextFile,
	Timeout,
	Time_Limit(true),
	To,
	Type,
	Unless,
	User,
	Value;

	/** The XML text value for the Attribute. */
	private String m_val;

	/**
	 * Constructor
	 *
	 * Create a Attribute object that uses underscores (default)
	 * or dashes. When true the useDashes argument will convert the
	 * underscores in the Attribute name to be dashes.
	 *
	 * @param useDashes is true if the Attribute uses dashes instead of underscores
	 */
	private Attribute(final boolean useDashes)
	{
		String val = this.name().toLowerCase();

		// If the attribute uses dashes instead of underscores
		// then we need to update its value
		if (useDashes) {
			val = val.replace("_", "-");
		}

		m_val = val;
	}

	/**
	 * Constructor
	 *
	 * Create an Attribute object that uses underscores.
	 */
	private Attribute()
	{
		this(false);  // Use underscores, not dashes
	}

	/**
	 * Get the XML text value for this Attribute.
	 *
	 * @param the XML text value for this Attribute
	 */
	public String val()
	{
		return m_val;
	}
}

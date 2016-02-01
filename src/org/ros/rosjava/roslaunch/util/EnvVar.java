package org.ros.rosjava.roslaunch.util;

/**
 * The EnvVar enumeration
 *
 * This class provides access to environment variables.
 */
public enum EnvVar
{
	LD_LIBRARY_PATH,
	ROS_HOME,
	ROS_HOSTNAME,
	ROS_IP,
	ROS_IPV6,
	ROS_LOG_DIR,
	ROS_MASTER_URI,
	ROS_NAMESPACE,
	ROS_PACKAGE_PATH,
	ROS_ROOT,
	ROSLAUNCH_SSH_UNKNOWN;

	/**
	 * Get the value of the environment variable.
	 *
	 * @return the value of the environment variable (or null if it's undefined)
	 */
	public String get()
	{
		return System.getenv(this.name());
	}

	/**
	 * Get the value of an environment variable.
	 *
	 * This function will get the value of an environment variable and
	 * return a default value if the value is undefined. The environment variable
	 * is considered undefined when:
	 *
	 *     - its value is null, or
	 *     - its value is an empty string and the checkLength flag is set to true
	 *
	 * If the value is undefined and the defaultValue variable is also null
	 * this function will throw a RuntimeException.
	 *
	 * @param defaultValue is the value returned if the variable was not defined
	 * @param checkLength when true will consider the value undefined if it's an empty string
	 * @return the value of the environment variable.
	 * @throws a RuntimeException if the value is undefined, and a default value is also null
	 */
	private String getVar(final String defaultValue, final boolean checkLength)
	{
		String value = this.get();
		if (value == null || (checkLength && value.length() == 0))
		{
			// If a default value is provided, then return that
			// otherwise throw an error
			if (defaultValue != null) {
				return defaultValue;
			}
			else {
				throw new RuntimeException(
					"Failed to find the '" + this.name() + "' environment variable!");
			}
		}

		return value;
	}

	/**
	 * Get a required environment variable. This will return
	 * variables that are defined as empty strings.
	 *
	 * @return the value of the environment variable
	 * @throws a RuntimeException if the value is undefined
	 */
	public String getReq()
	{
		return getVar(null, false);  // Required, can be empty
	}

	/**
	 * Get a required non-empty environment variable. This
	 * function will throw a RuntimeException if the variable
	 * is not defined, or is defined as an empty string.
	 *
	 * @return the value of the environment variable
	 * @throws a RuntimeException if the value is undefined or is an empty string
	 */
	public String getReqNonEmpty()
	{
		return getVar(null, true);  // Required and non-empty
	}

	/**
	 * Get an optional environment variable. This function will
	 * return an empty string if a variable is defined as such.
	 *
	 * @param defaultValue is the value returned if the variable was not defined
	 * @return the value of the environment variable
	 * @throw a RuntimeException if the variable is undefined and the
	 *        defaultValue is null
	 */
	public String getOpt(final String defaultValue)
	{
		return getVar(defaultValue, false);  // Optional, can be empty
	}

	/**
	 * Get an optional non-empty environment variable.
	 *
	 * @param defaultValue is the value returned if the variable was not defined
	 * @return the value of the environment variable
	 * @throw a RuntimeException if the variable is undefined and the
	 *        defaultValue is null
	 */
	public String getOptNonEmpty(final String defaultValue)
	{
		return getVar(defaultValue, true);  // Optional and non-empty
	}
}

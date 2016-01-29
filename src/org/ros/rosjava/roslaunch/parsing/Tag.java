package org.ros.rosjava.roslaunch.parsing;

/**
 * The Tag enumeration
 *
 * This enumeration contains identifiers for all XML
 * tags that can be contained within ROS launch file
 * XML code.
 */
public enum Tag
{
	Arg,
	Env,
	Group,
	Include,
	Machine,
	Launch,
	Node,
	Param,
	Remap,
	RosParam,
	Test;
	
	/**
	 * Return the XML text value of the Tag.
	 * 
	 * @return the XML text value of the Tag
	 */
	public String val()
	{
		return super.name().toLowerCase();
	}
};

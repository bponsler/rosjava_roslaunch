package org.ros.rosjava.roslaunch.xmlrpc;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;

/**
 * The ObjectToXml class
 *
 * This class is responsible for converting Java Objects
 * into XML that can be used for XMLRPC requests.
 */
public class ObjectToXml
{
	/**
	 * Convert the given Java Object into proper XML.
	 * 
	 * This function can convert the following Java Objects to XML:
	 *     - Booleans
	 *     - Integer
	 *     - Double
	 *     - String
	 *     - List
	 * 
	 * This function does not handle converting Map objects to
	 * XML as those should be handled elsewhere.
	 * 
	 * @param obj the object to convert to XML
	 * @return the XML representing the object
	 * @throws a RuntimeException if this is passed a Map object
	 * @throws a RuntimeException if this is an unknown object type
	 */
	public static String objectToXml(final Object obj)
	{
		if (isBoolean(obj))
		{
			Boolean val = (Boolean)obj;
			String boolStr = (val.booleanValue()) ? "1" : "0";
			return "<value><boolean>" + boolStr + "</boolean></value>";
		}
		else if (isInteger(obj))
		{
			Integer val = (Integer)obj;
			return "<value><int>" + val.toString() + "</int></value>";
		}
		else if (isDouble(obj))
		{
			Double val = (Double)obj;
			return "<value><double>" + val.toString() + "</double></value>";
		}
		else if (isString(obj))
		{
			String val = (String)obj;
			return "<value><string>" + StringEscapeUtils.escapeXml11(val) + "</string></value>";
		}
		else if (isList(obj))
		{
			@SuppressWarnings("unchecked")
			List<Object> list = (List<Object>)obj;
			
			String xml = "<array><data>";
			
			// Convert each time into a list
			for (Object item : list) {
				xml += objectToXml(item);
			}
			
			xml += "</data></array>";
			
			return xml;
		}
		else if (isMap(obj)){	
			throw new RuntimeException("Maps are not supported");
		}
		else
		{
			throw new RuntimeException("Invalid type of YAML object!");
		}
	}
	
	/**
	 * Determine if the given Object is a Boolean.
	 * 
	 * @param obj is the object
	 * @return true if this object is a Boolean
	 */
	public static boolean isBoolean(final Object obj)
	{
		try
		{
			@SuppressWarnings("unused")
			Boolean str = (Boolean)obj;
			return true;
		}
		catch (Exception e) {
			return false;  // Not a boolean
		}
	}
	
	/**
	 * Determine if the given Object is an Integer.
	 * 
	 * @param obj is the object
	 * @return true if this object is an Integer
	 */
	public static boolean isInteger(final Object obj)
	{
		try
		{
			@SuppressWarnings("unused")
			Integer item = (Integer)obj;
			return true;
		}
		catch (Exception e) {
			return false;  // Not an integer
		}
	}
	
	/**
	 * Determine if the given Object is a Double.
	 * 
	 * @param obj is the object
	 * @return true if this object is a Double
	 */
	public static boolean isDouble(final Object obj)
	{
		try
		{
			@SuppressWarnings("unused")
			Double item = (Double)obj;
			return true;
		}
		catch (Exception e) {
			return false;  // Not a double
		}
	}
	
	/**
	 * Determine if the given Object is a String.
	 * 
	 * @param obj is the object
	 * @return true if this object is a String
	 */
	public static boolean isString(final Object obj)
	{
		try
		{
			@SuppressWarnings("unused")
			String item = (String)obj;
			return true;
		}
		catch (Exception e) {
			return false;  // Not a string
		}
	}
	
	/**
	 * Determine if the given Object is a List.
	 * 
	 * @param obj is the object
	 * @return true if this object is a List
	 */
	public static boolean isList(final Object obj)
	{
		try
		{
			@SuppressWarnings({ "unused", "unchecked" })
			List<Object> item = (List<Object>)obj;
			return true;
		}
		catch (Exception e) {
			return false;  // Not at string
		}
	}
	
	/**
	 * Determine if the given Object is a Map.
	 * 
	 * @param obj is the object
	 * @return true if this object is a Map
	 */
	public static boolean isMap(final Object obj)
	{
		try
		{
			@SuppressWarnings({ "unused", "unchecked" })
			Map<Object, Object> item = (Map<Object, Object>)obj;
			return true;
		}
		catch (Exception e) {
			return false;  // Not a map
		}
	}
}

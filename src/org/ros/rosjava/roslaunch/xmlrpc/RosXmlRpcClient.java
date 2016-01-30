package org.ros.rosjava.roslaunch.xmlrpc;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.RosParamTag;
import org.ros.rosjava.roslaunch.util.RosUtil;

/**
 * The RosXmlRpcClient class
 *
 * This class is responsible for providing a client interface
 * for making requests to the ROS XMLRPC server.
 *
 * This is defined based on the ROS XMLRPC server specification found here:
 *
 *     http://wiki.ros.org/ROS/Parameter%20Server%20API
 *
 * It is only a partial implementation of the specification.
 *
 * Additionally this class makes use of the Apache commons lang library
 * which can be found here:
 *
 *     http://commons.apache.org/proper/commons-lang/download_lang.cgi
 */
public class RosXmlRpcClient
{
	/** The XML header used for all requests. */
	private static String XML_HEADER = "<?xml version=\"1.0\"?>";

	/** The URI for the XMLRPC server. */
	private String m_uri;
	/** The name of the requesting process. */
	private String m_name;

	/** The list of supported XML types. */
	private List<String> m_supportedTypes;

	/**
	 * Constructor
	 *
	 * Create an RosXmlRpcClient object to communicate with a ROS
	 * XMLRPC server at the given uri.
	 *
	 * @param uri the URI for the ROS XMLRPC server
	 */
	public RosXmlRpcClient(final String uri)
	{
		m_uri = uri;

		// generate a name for calling the XMLRPC interface
		m_name = "rosjava-roslaunch";

		// Add all of the supported XML types
		m_supportedTypes = new ArrayList<String>();
		m_supportedTypes.add("boolean");
		m_supportedTypes.add("int");
		m_supportedTypes.add("double");
		m_supportedTypes.add("string");
	}

	/**
	 * Request the value of a parameter from the server.
	 *
	 * @param param is the name of the parameter to request
	 * @return the value of the parameter
	 */
	public String getParam(final String param)
	{
		// TODO: finish implementing this request
		return null;
	}

	/**
	 * Request the state of the system from the server.
	 *
	 * @return the SystemStateResponse data
	 * @throws an Exception if the request fails
	 */
	public SystemStateResponse getSystemState() throws Exception
	{
		String data = "";
		data += "<methodCall>";
		data += "  <methodName>getSystemState</methodName>";
		data += "  <params>";
		data += "    <param><value><string>" + m_name + "</string></value></param>";
		data += "  </params>";
		data += "</methodCall>";

		return new SystemStateResponse(sendXmlRpcData(data));
	}

	/**
	 * Set the value of a YAML parameter on the server.
	 *
	 * @param rosparam is the RosParamTag to set
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setYamlParam(
			final RosParamTag rosparam) throws Exception
	{
		String paramName = rosparam.getParam();
		String resolved = rosparam.getResolvedName();
		Object yamlObj = rosparam.getYamlObject();

		// Dictionaries get a parameter set for each entry
		if (ObjectToXml.isMap(yamlObj))
		{
			@SuppressWarnings("unchecked")
			boolean success = _setMapParams(
					resolved, (Map<String, Object>)yamlObj);
			return new RosParamResponse(success);
		}
		else
		{
			// Otherwise handle all non-map rosparams the same way
			return setParamObject(paramName, yamlObj);
		}
	}

	/**
	 * Set the value of all parameters contained within a dictionary.
	 *
	 * For example, given the following dictionary:
	 *     {a: 1, b: hello, c: {sub: {another: value}
	 *
	 * The following parameters will be set:
	 *
	 *     /a = 1
	 *     /b = hello
	 *     /c/sub/another = value
	 *
	 * @param namespace is the parent namespace applied to all parameters
	 * @param map is the Map object
	 * @return true if all parameters were set successfully, false if any failed
	 */
	@SuppressWarnings("unchecked")
	private boolean _setMapParams(
			final String namespace,
			final Map<String, Object> map)
	{
		boolean success = true;
		for (Object key : map.keySet())
		{
			Object value = map.get(key);
			String resolvedKey = RosUtil.joinNamespace(namespace, key.toString());

			if (ObjectToXml.isMap(value))
			{
				// Recurse to handle this dictionary
				success |= _setMapParams(resolvedKey, (Map<String, Object>)value);
			}
			else
			{
				// Found a final parameter, set it
				String xml = ObjectToXml.objectToXml(value);

				try {
					setXmlParam(resolvedKey, xml);
				}
				catch (Exception e)
				{
					PrintLog.error("Failed to set param: " + resolvedKey + ": " + e.getMessage());
					success = false;
				}
			}
		}

		return success;
	}

	/**
	 * Clear the value of a parameter on the server.
	 *
	 * @param paramName is the name of the parameter to clear
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse clearParam(final String paramName) throws Exception
	{
		// Setting a parameter to an empty struct effectively deletes it and
		// every parameter under its namespace
		String xml = "<value><struct></struct></value>";
		return setXmlParam(paramName, xml);
	}

	/**
	 * Set the value of a boolean parameter on the server.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the boolean value
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setParam(final String paramName, final boolean value) throws Exception
	{
		return setParamObject(paramName, value);
	}

	/**
	 * Set the value of a int parameter on the server.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the int value
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setParam(final String paramName, final int value) throws Exception
	{
		return setParamObject(paramName, value);
	}

	/**
	 * Set the value of a double parameter on the server.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the double value
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setParam(final String paramName, final double value) throws Exception
	{
		return setParamObject(paramName, value);
	}

	/**
	 * Set the value of a String parameter on the server.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the String value
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setParam(final String paramName, final String value) throws Exception
	{
		return setParamObject(paramName, value);
	}

	/**
	 * Set the value of a binary parameter on the server.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the binary data
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setBinaryParam(final String paramName, final String value) throws Exception
	{
		// Encode the string using base64
		String encoded = Base64.encodeBase64String(value.getBytes());

		String xml = "<value><base64>" + encoded + "</base64></value>";
		return setXmlParam(paramName, xml);
	}

	/**
	 * Set the value of a parameter of the given type on the server.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param type is the type of parameter ("boolean", "int", "double", or "string")
	 * @param value is the String value
	 * @return the RosParamResponse data
	 * @throws a RuntimeException if an unknown type is given
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setParam(final String paramName, final String type, final String value) throws Exception
	{
		// Make sure this is a supported type
		if (!m_supportedTypes.contains(type)) {
			throw new RuntimeException("Invalid XML type: " + type);
		}

		if (type.compareTo("boolean") == 0) {
			return setParamObject(paramName, Boolean.parseBoolean(value));
		}
		else if (type.compareTo("int") == 0) {
			return setParamObject(paramName, Integer.parseInt(value));
		}
		else if (type.compareTo("double") == 0) {
			return setParamObject(paramName, Double.parseDouble(value));
		}
		else if (type.compareTo("string") == 0) {
			return setParamObject(paramName, value);
		}
		else {
			throw new RuntimeException("Invalid param type: " + type);
		}
	}

	/**
	 * Set the value of a parameter of the given Object on the server that
	 * has not yet been converted to XML.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the Object representing the value of the parameter
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setParamObject(final String paramName, final Object value) throws Exception
	{
		// Create the xml to specify the value of the given type
		String valueXml = ObjectToXml.objectToXml(value);

		String data = "";
		data += "<methodCall>";
		data += "  <methodName>setParam</methodName>";
		data += "  <params>";
		data += "    <param><value><string>" + m_name + "</string></value></param>";
		data += "    <param><value><string>" + paramName + "</string></value></param>";
		data += "    <param>" + valueXml + "</param>";
		data += "  </params>";
		data += "</methodCall>";

		return new RosParamResponse(sendXmlRpcData(data));
	}

	/**
	 * Set the value of a parameter on the server which has already
	 * been converted to XML.
	 *
	 * @param paramName is the name of the parameter to set
	 * @param value is the Object representing the value of the parameter
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse setXmlParam(final String paramName, final Object value) throws Exception
	{
		String data = "";
		data += "<methodCall>";
		data += "  <methodName>setParam</methodName>";
		data += "  <params>";
		data += "    <param><value><string>" + m_name + "</string></value></param>";
		data += "    <param><value><string>" + paramName + "</string></value></param>";
		data += "    <param>" + value + "</param>";
		data += "  </params>";
		data += "</methodCall>";

		return new RosParamResponse(sendXmlRpcData(data));
	}

	/**
	 * Request that the server deletes a parameter.
	 *
	 * @param paramName is the name of the parameter to delete
	 * @return the RosParamResponse data
	 * @throws an Exception if the request fails
	 */
	public RosParamResponse deleteParam(final String paramName) throws Exception
	{
		String data = "";
		data += "<methodCall>";
		data += "  <methodName>deleteParam</methodName>";
		data += "  <params>";
		data += "    <param><value><string>" + m_name + "</string></value></param>";
		data += "    <param><value><string>" + paramName + "</string></value></param>";
		data += "  </params>";
		data += "</methodCall>";

		return new RosParamResponse(sendXmlRpcData(data));
	}

	/**
	 * Helper function to make the actual XMLRPC request.
	 *
	 * @param data is the XML data to request
	 * @return the RosXmlRpcResponseBase data
	 * @throws an Exception if the request fails
	 */
	private RosXmlRpcResponseBase sendXmlRpcData(final String data) throws Exception
	{
		// Open an HTTP connection to the server
		URL u = new URL(m_uri);
		URLConnection uc = u.openConnection();
		HttpURLConnection connection = (HttpURLConnection)uc;
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestMethod("POST");
		OutputStream out = connection.getOutputStream();
		OutputStreamWriter wout = new OutputStreamWriter(out, "UTF-8");

		// Write the XMLRPC command to set the given parameter to the given value
		wout.write(XML_HEADER + data);

		wout.flush();
		out.close();

		// Parse the response XML
		InputStream in = connection.getInputStream();
		RosXmlRpcResponseBase response = new RosXmlRpcResponseBase(in);

		in.close();
		out.close();

		return response;
	}
}

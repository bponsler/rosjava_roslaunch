package org.ros.rosjava.roslaunch.xmlrpc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The RosXmlRpcResponseBase class
 *
 * This class is responsible for encapsulating the XMLRPC data
 * returned from requests made to an ROS XMLRPC server.
 */
public class RosXmlRpcResponseBase
{
	/** The list of all response data returned. */
	protected List<Object> m_responseData;

	/**
	 * Constructor
	 *
	 * Create an empty RosXmlRpcResponseBase object.
	 */
	protected RosXmlRpcResponseBase()
	{
		m_responseData = new ArrayList<Object>();
	}

	/**
	 * Copy constructor
	 *
	 * Create a copy of an RosXmlRpcResponseBase object.
	 * @throws a RuntimeException if a fault is received
	 */
	protected RosXmlRpcResponseBase(final RosXmlRpcResponseBase other)
	{
		// Copy the data
		m_responseData = new ArrayList<Object>(other.m_responseData);
	}

	/**
	 * Constructor
	 *
	 * Create a RosXmlRpcResponseBase object from an input stream.
	 * @throws a RuntimeException if a fault is received
	 */
	public RosXmlRpcResponseBase(final InputStream inputStream)
	{
		this();

		//// XML response should look something like:
		//
		// <?xml version='1.0'?>
		// <methodResponse>
		//   <params>
		//     <param>
		//       <value>
		//         <array>
		//           <data>
		//             <value><int>1</int></value>
		//             <value><string>parameter /my_bool_param set</string></value>
		//             <value><int>0</int></value>
		//           </data>
		//         </array>
		//       </value>
		//     </param>
		//   </params>
		// </methodResponse>

		// Attempt to parse the response as XML
		Document doc;
		try
		{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(inputStream);

			parseDocument(doc);
		}
		catch (Exception e)	{
			throw new RuntimeException(
				"ERROR: failed to parse XMLRPC response: " + e.getMessage());
		}
	}

	/**
	 * Parse the given XML document.
	 *
	 * @param doc is the XML document
	 */
	private void parseDocument(final Document doc)
	{
		// Ensure we have the method response tag
		Element response = doc.getDocumentElement();
		if (response.getTagName().compareTo("methodResponse") == 0) {
			parseMethodResponse(response);
		}
	}

	/**
	 * Parse the methodResponse XML tag.
	 *
	 * @param responseMethod is the responseMethod XML element
	 */
	private void parseMethodResponse(final Element responseMethod)
	{
		// Iterate over children of the method response tag
		NodeList children = responseMethod.getChildNodes();
		for (int index = 0; index < children.getLength(); ++index)
		{
			Node responseChild = children.item(index);
			String responseChildTag = responseChild.getNodeName();

			// Only interested in the params child
			if (responseChild.getNodeType() == Node.ELEMENT_NODE)
			{
				if (responseChildTag.compareTo("params") == 0)
				{
					parseParams(responseChild);
				}
				else if (responseChildTag.compareTo("fault") == 0)
				{
					parseFault(responseChild);
				}
			}
		}
	}

	/**
	 * Parse the params XML tag.
	 *
	 * @param params is the params XML element
	 */
	private void parseParams(final Node params)
	{
		NodeList children = params.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i)
		{
			Node paramsChild = children.item(i);
			String paramsChildTag = paramsChild.getNodeName();

			// Only interested in param children
			if (paramsChild.getNodeType() == Node.ELEMENT_NODE &&
				paramsChildTag.compareTo("param") == 0)
			{
				parseParam(paramsChild);
			}
		}
	}

	/**
	 * Parse the param XML tag.
	 *
	 * @param param is the param XML element
	 */
	@SuppressWarnings("unchecked")
	private void parseParam(final Node param)
	{
		NodeList children = param.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i)
		{
			Node child = children.item(i);
			String tag = child.getNodeName();

			// Only interested in value children
			if (child.getNodeType() == Node.ELEMENT_NODE &&
				tag.compareTo("value") == 0)
			{
				Object data = XmlToObject.xmlToObject((Element)child);
				if (data != null && ObjectToXml.isList(data))
				{
					m_responseData = (List<Object>)data;
				}
			}
		}
	}

	/**
	 * Parse the fault XML tag.
	 *
	 * @param param is the fault XML element
	 * @throws a RuntimeException if a fault is received
	 */
	private void parseFault(final Node fault)
	{
		NodeList children = fault.getChildNodes();

		for (int i = 0; i < children.getLength(); ++i)
		{
			Node child = children.item(i);
			String tag = child.getNodeName();

			// Only interested in value children
			if (child.getNodeType() == Node.ELEMENT_NODE &&
				tag.compareTo("value") == 0)
			{
				throw new RuntimeException(
						"XMLRPC fault: " + child.getTextContent());
			}
		}
	}
}

package org.ros.rosjava.roslaunch.xmlrpc;

/**
 * The HasParamResponse class
 *
 * This class is responsible for encapsulating the XMLRPC response
 * data for the HasParam XMLRPC request.
 */
public class HasParamResponse extends RosXmlRpcResponseBase
{
	/** The response code. */
	protected int m_code;
	/** The status message. */
	protected String m_statusMessage;
	/** Whether or not the parameter exists. */
	protected boolean m_hasParam;

	/**
	 * Constructor
	 *
	 * Create a HasParamResponse object from an XMLRPC response.
	 *
	 * @param other is the RosXmlRpcResponseBase object
	 */
	public HasParamResponse(final RosXmlRpcResponseBase other)
	{
		super(other);

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
		//             <value><string>parameter /my_bool_param</string></value>
		//             <value><boolean>1</boolean></value>
		//           </data>
		//         </array>
		//       </value>
		//     </param>
		//   </params>
		// </methodResponse>

		// The return parameters are as follows:
		//     status code, status message, and has param
		m_code = -1;
		m_statusMessage = "Failed to parse";
		m_hasParam = false;

		// There should be exactly three response items
		if (m_responseData.size() == 3)
		{
			m_code = (Integer)m_responseData.get(0);
			m_statusMessage = (String)m_responseData.get(1);
			m_hasParam = (Boolean)m_responseData.get(2);
		}

		// Check for success
		if (m_code != 1) {
			throw new RuntimeException("ERROR: " + m_statusMessage);
		}
	}

	/**
	 * Get the response code.
	 *
	 * @return the response code
	 */
	public int getCode()
	{
		return m_code;
	}

	/**
	 * Get the status message.
	 *
	 * @return the status message
	 */
	public String getStatusMessage()
	{
		return m_statusMessage;
	}

	/**
	 * Determine if the parameter exists on the server.
	 *
	 * @return true if the parameter exists
	 */
	public boolean getParamValue()
	{
		return m_hasParam;
	}
}

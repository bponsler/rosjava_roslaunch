package org.ros.rosjava.roslaunch.xmlrpc;

/**
 * The GetParamResponse class
 *
 * This class is responsible for encapsulating the XMLRPC response
 * data for the GetParam XMLRPC request.
 */
public class GetParamResponse extends RosXmlRpcResponseBase
{
	/** The response code. */
	protected int m_code;
	/** The status message. */
	protected String m_statusMessage;
	/** The parameter value. */
	protected Object m_paramValue;

	/**
	 * Constructor
	 *
	 * Create a GetParamResponse object from an XMLRPC response.
	 *
	 * @param other is the RosXmlRpcResponseBase object
	 */
	public GetParamResponse(final RosXmlRpcResponseBase other)
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
		//             <value><string>parameter /my_bool_param set</string></value>
		//             <value><string>my_value</string></value>
		//           </data>
		//         </array>
		//       </value>
		//     </param>
		//   </params>
		// </methodResponse>

		// The return parameters are as follows:
		//     status code, status message, and parma value
		m_code = -1;
		m_statusMessage = "Failed to parse";
		m_paramValue = "";

		// There should be exactly three response items
		if (m_responseData.size() == 3)
		{
			m_code = (Integer)m_responseData.get(0);
			m_statusMessage = (String)m_responseData.get(1);
			m_paramValue = m_responseData.get(2);
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
	 * Get the param value.
	 *
	 * @return the param value
	 */
	public Object getParamValue()
	{
		return m_paramValue;
	}
}

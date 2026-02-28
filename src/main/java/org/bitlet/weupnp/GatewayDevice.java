/* 
 *              weupnp - Trivial upnp java library 
 *
 * Copyright (C) 2008 Alessandro Bahgat Shehata, Daniele Castagna
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 * Alessandro Bahgat Shehata - ale dot bahgat at gmail dot com
 * Daniele Castagna - daniele dot castagna at gmail dot com
 * 
 */
package org.bitlet.weupnp;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * A <tt>GatewayDevice</tt> is a class that abstracts UPnP-compliant gateways
 * <p/>
 * It holds all the information that comes back as UPnP responses, and
 * provides methods to issue UPnP commands to a gateway.
 *
 * @author casta
 */
public class GatewayDevice {

	/**
	 * Receive timeout when requesting data from device
	 */
    private static final int DEFAULT_HTTP_RECEIVE_TIMEOUT = 7000;
    
	private String st;
    private String location;
    private String serviceType;
    private String serviceTypeCIF;
    private String urlBase;
    private String controlURL;
    private String controlURLCIF;
    private String eventSubURL;
    private String eventSubURLCIF;
    private String sCPDURL;
    private String sCPDURLCIF;
    private String deviceType;
    private String deviceTypeCIF;

    // description data

    /**
     * The friendly (human readable) name associated with this device
     */
    private String friendlyName;

    /**
     * The device manufacturer name
     */
    private String manufacturer;

    /**
     * The model description as a string
     */
    private String modelDescription;

    /**
     * The URL that can be used to access the IGD interface
     */
    private String presentationURL;

    /**
     * The address used to reach this machine from the GatewayDevice
     */
    private InetAddress localAddress;

    /**
     * The model number (used by the manufacturer to identify the product)
     */
    private String modelNumber;

    /**
     * The model name
     */
    private String modelName;

    /**
     * Timeout in milliseconds for HTTP reads
     */
    private static int httpReadTimeout = DEFAULT_HTTP_RECEIVE_TIMEOUT;

    /**
     * Creates a new instance of GatewayDevice
     */
    public GatewayDevice() {
    }

    /**
     * Retrieves the properties and description of the GatewayDevice.
     * <p/>
     * Connects to the device's {@link #location} and parses the response
     * using a {@link GatewayDeviceHandler} to populate the fields of this
     * class
     *
     * @throws SAXException if an error occurs while parsing the request
     * @throws IOException  on communication errors
     * @see org.bitlet.weupnp.GatewayDeviceHandler
     */
    public void loadDescription() throws SAXException, IOException {

        URLConnection urlConn = new URL(getLocation()).openConnection();
        urlConn.setReadTimeout(httpReadTimeout);

        XMLReader parser = XMLReaderFactory.createXMLReader();
        parser.setContentHandler(new GatewayDeviceHandler(this));
        parser.parse(new InputSource(urlConn.getInputStream()));


        /* fix urls */
        String ipConDescURL;
        if (urlBase != null && urlBase.trim().length() > 0) {
            ipConDescURL = urlBase;
        } else {
            ipConDescURL = location;
        }

        int lastSlashIndex = ipConDescURL.indexOf('/', 7);
        if (lastSlashIndex > 0) {
            ipConDescURL = ipConDescURL.substring(0, lastSlashIndex);
        }


        sCPDURL = copyOrCatUrl(ipConDescURL, sCPDURL);
        controlURL = copyOrCatUrl(ipConDescURL, controlURL);
        controlURLCIF = copyOrCatUrl(ipConDescURL, controlURLCIF);
        presentationURL = copyOrCatUrl(ipConDescURL, presentationURL);
    }

    /**
     * Issues UPnP commands to a GatewayDevice that can be reached at the
     * specified <tt>url</tt>
     * <p/>
     * The command is identified by a <tt>service</tt> and an <tt>action</tt>
     * and can receive arguments
     *
     * @param url     the url to use to contact the device
     * @param service the service to invoke
     * @param action  the specific action to perform
     * @param args    the command arguments
     * @return the response to the performed command, as a name-value map.
     *         In case errors occur, the returned map will be <i>empty.</i>
     * @throws IOException  on communication errors
     * @throws SAXException if errors occur while parsing the response
     */
    public static Map<String, String> simpleUPnPcommand(String url,
                                                        String service, String action, Map<String, String> args)
            throws IOException, SAXException {
        String soapAction = "\"" + service + "#" + action + "\"";
        StringBuilder soapBody = new StringBuilder();

        soapBody.append("<?xml version=\"1.0\"?>\r\n" +
                "<SOAP-ENV:Envelope " +
                "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<SOAP-ENV:Body>" +
                "<m:" + action + " xmlns:m=\"" + service + "\">");

        if (args != null && args.size() > 0) {

            Set<Map.Entry<String, String>> entrySet = args.entrySet();

            for (Map.Entry<String, String> entry : entrySet) {
                soapBody.append("<" + entry.getKey() + ">" + entry.getValue() +
                        "</" + entry.getKey() + ">");
            }

        }

        soapBody.append("</m:" + action + ">");
        soapBody.append("</SOAP-ENV:Body></SOAP-ENV:Envelope>");

        URL postUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();

        conn.setRequestMethod("POST");
        conn.setConnectTimeout(httpReadTimeout);
        conn.setReadTimeout(httpReadTimeout);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("SOAPAction", soapAction);
        conn.setRequestProperty("Connection", "Close");

        byte[] soapBodyBytes = soapBody.toString().getBytes();

        conn.setRequestProperty("Content-Length",
                String.valueOf(soapBodyBytes.length));

        conn.getOutputStream().write(soapBodyBytes);

        Map<String, String> nameValue = new HashMap<String, String>();
        XMLReader parser = XMLReaderFactory.createXMLReader();
        parser.setContentHandler(new NameValueHandler(nameValue));
        if (conn.getResponseCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
            try {
                // attempt to parse the error message
                parser.parse(new InputSource(conn.getErrorStream()));
            } catch (SAXException e) {
                // ignore the exception
                // FIXME We probably need to find a better way to return
                // significant information when we reach this point
            }
            conn.disconnect();
            return nameValue;
        } else {
            parser.parse(new InputSource(conn.getInputStream()));
            conn.disconnect();
            return nameValue;
        }
    }

    /**
     * Retrieves the connection status of this device
     *
     * @return true if connected, false otherwise
     * @throws IOException
     * @throws SAXException
     * @see #simpleUPnPcommand(java.lang.String, java.lang.String,
     *      java.lang.String, java.util.Map)
     */
    public boolean isConnected() throws IOException, SAXException {
        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "GetStatusInfo", null);

        String connectionStatus = nameValue.get("NewConnectionStatus");
        if (connectionStatus != null
                && connectionStatus.equalsIgnoreCase("Connected")) {
            return true;
        }

        return false;
    }

    /**
     * Retrieves the external IP address associated with this device
     * <p/>
     * The external address is the address that can be used to connect to the
     * GatewayDevice from the external network
     *
     * @return the external IP
     * @throws IOException
     * @throws SAXException
     * @see #simpleUPnPcommand(java.lang.String, java.lang.String,
     *      java.lang.String, java.util.Map)
     */
    public String getExternalIPAddress() throws IOException, SAXException {
        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "GetExternalIPAddress", null);

        return nameValue.get("NewExternalIPAddress");
    }

    /**
     * Adds a new port mapping to the GatewayDevices using the supplied
     * parameters.
     *
     * @param externalPort   the external associated with the new mapping
     * @param internalPort   the internal port associated with the new mapping
     * @param internalClient the internal client associated with the new mapping
     * @param protocol       the protocol associated with the new mapping
     * @param description    the mapping description
     * @return true if the mapping was successfully added, false otherwise
     * @throws IOException
     * @throws SAXException
     * @see #simpleUPnPcommand(java.lang.String, java.lang.String,
     *      java.lang.String, java.util.Map)
     * @see PortMappingEntry
     */
    public boolean addPortMapping(int externalPort, int internalPort,
                                  String internalClient, String protocol, String description)
            throws IOException, SAXException {
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("NewRemoteHost", "");    // wildcard, any remote host matches
        args.put("NewExternalPort", Integer.toString(externalPort));
        args.put("NewProtocol", protocol);
        args.put("NewInternalPort", Integer.toString(internalPort));
        args.put("NewInternalClient", internalClient);
        args.put("NewEnabled", Integer.toString(1));
        args.put("NewPortMappingDescription", description);
        args.put("NewLeaseDuration", Integer.toString(0));

        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "AddPortMapping", args);

        return nameValue.get("errorCode") == null;
    }

    /**
     * Queries the GatewayDevice to retrieve a specific port mapping entry,
     * corresponding to specified criteria, if present.
     * <p/>
     * Retrieves the <tt>PortMappingEntry</tt> associated with
     * <tt>externalPort</tt> and <tt>protocol</tt>, if present.
     *
     * @param externalPort     the external port
     * @param protocol         the protocol (TCP or UDP)
     * @param portMappingEntry the entry containing the details, in any is
     *                         present, <i>null</i> otherwise. <i>(used as return value)</i>
     * @return true if a valid mapping is found
     * @throws IOException
     * @throws SAXException
     * @todo consider refactoring this method to make it consistent with
     * Java practices (return the port mapping)
     * @see #simpleUPnPcommand(java.lang.String, java.lang.String,
     *      java.lang.String, java.util.Map)
     * @see PortMappingEntry
     */
    public boolean getSpecificPortMappingEntry(int externalPort,
                                               String protocol, final PortMappingEntry portMappingEntry)
            throws IOException, SAXException {

        portMappingEntry.setExternalPort(externalPort);
        portMappingEntry.setProtocol(protocol);

        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("NewRemoteHost", ""); // wildcard, any remote host matches
        args.put("NewExternalPort", Integer.toString(externalPort));
        args.put("NewProtocol", protocol);

        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "GetSpecificPortMappingEntry", args);

        if (nameValue.isEmpty() || nameValue.containsKey("errorCode"))
            return false;

        if (!nameValue.containsKey("NewInternalClient") ||
                !nameValue.containsKey("NewInternalPort"))
            return false;

        portMappingEntry.setProtocol(protocol);
        portMappingEntry.setEnabled(nameValue.get("NewEnabled"));
        portMappingEntry.setInternalClient(nameValue.get("NewInternalClient"));
        portMappingEntry.setExternalPort(externalPort);
        portMappingEntry.setPortMappingDescription(nameValue.get("NewPortMappingDescription"));
        portMappingEntry.setRemoteHost(nameValue.get("NewRemoteHost"));

        try {
            portMappingEntry.setInternalPort(Integer.parseInt(nameValue.get("NewInternalPort")));
        } catch (NumberFormatException nfe) {
            // skip bad port
        }


        return true;
    }

    /**
     * Returns a specific port mapping entry, depending on a the supplied index.
     *
     * @param index            the index of the desired port mapping
     * @param portMappingEntry the entry containing the details, in any is
     *                         present, <i>null</i> otherwise. <i>(used as return value)</i>
     * @return true if a valid mapping is found
     * @throws IOException
     * @throws SAXException
     * @todo consider refactoring this method to make it consistent with
     * Java practices (return the port mapping)
     * @see #simpleUPnPcommand(java.lang.String, java.lang.String,
     *      java.lang.String, java.util.Map)
     * @see PortMappingEntry
     */
    public boolean getGenericPortMappingEntry(int index,
                                              final PortMappingEntry portMappingEntry)
            throws IOException, SAXException {
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("NewPortMappingIndex", Integer.toString(index));

        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "GetGenericPortMappingEntry", args);

        if (nameValue.isEmpty() || nameValue.containsKey("errorCode"))
            return false;

        portMappingEntry.setRemoteHost(nameValue.get("NewRemoteHost"));
        portMappingEntry.setInternalClient(nameValue.get("NewInternalClient"));
        portMappingEntry.setProtocol(nameValue.get("NewProtocol"));
        portMappingEntry.setEnabled(nameValue.get("NewEnabled"));
        portMappingEntry.setPortMappingDescription(
                nameValue.get("NewPortMappingDescription"));

        try {
            portMappingEntry.setInternalPort(
                    Integer.parseInt(nameValue.get("NewInternalPort")));
        } catch (Exception e) {
        }

        try {
            portMappingEntry.setExternalPort(
                    Integer.parseInt(nameValue.get("NewExternalPort")));
        } catch (Exception e) {
        }

        return true;
    }

    /**
     * Retrieves the number of port mappings that are registered on the
     * GatewayDevice.
     *
     * @return the number of port mappings
     * @throws IOException
     * @throws SAXException
     */
    public Integer getPortMappingNumberOfEntries()
            throws IOException, SAXException {
        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "GetPortMappingNumberOfEntries", null);

        Integer portMappingNumber = null;

        try {
            portMappingNumber = Integer.valueOf(
                    nameValue.get("NewPortMappingNumberOfEntries"));
        } catch (Exception e) {
        }

        return portMappingNumber;
    }

    /**
     * Deletes the port mapping associated to <tt>externalPort</tt> and
     * <tt>protocol</tt>
     *
     * @param externalPort the external port
     * @param protocol     the protocol
     * @return true if removal was successful
     * @throws IOException
     * @throws SAXException
     */
    public boolean deletePortMapping(int externalPort, String protocol)
            throws IOException, SAXException {
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("NewRemoteHost", "");
        args.put("NewExternalPort", Integer.toString(externalPort));
        args.put("NewProtocol", protocol);
        Map<String, String> nameValue = simpleUPnPcommand(controlURL,
                serviceType, "DeletePortMapping", args);

        return true;
    }

    // getters and setters

    /**
     * Gets the local address to connect the gateway through
     *
     * @return the {@link #localAddress}
     */
    public InetAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * Sets the {@link #localAddress}
     *
     * @param localAddress the address to set
     */
    public void setLocalAddress(InetAddress localAddress) {
        this.localAddress = localAddress;
    }

    public String getSt() {
        return st;
    }

    public void setSt(String st) {
        this.st = st;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceTypeCIF() {
        return serviceTypeCIF;
    }

    public void setServiceTypeCIF(String serviceTypeCIF) {
        this.serviceTypeCIF = serviceTypeCIF;
    }

    public String getControlURL() {
        return controlURL;
    }

    public void setControlURL(String controlURL) {
        this.controlURL = controlURL;
    }

    public String getControlURLCIF() {
        return controlURLCIF;
    }

    public void setControlURLCIF(String controlURLCIF) {
        this.controlURLCIF = controlURLCIF;
    }

    public String getEventSubURL() {
        return eventSubURL;
    }

    public void setEventSubURL(String eventSubURL) {
        this.eventSubURL = eventSubURL;
    }

    public String getEventSubURLCIF() {
        return eventSubURLCIF;
    }

    public void setEventSubURLCIF(String eventSubURLCIF) {
        this.eventSubURLCIF = eventSubURLCIF;
    }

    public String getSCPDURL() {
        return sCPDURL;
    }

    public void setSCPDURL(String sCPDURL) {
        this.sCPDURL = sCPDURL;
    }

    public String getSCPDURLCIF() {
        return sCPDURLCIF;
    }

    public void setSCPDURLCIF(String sCPDURLCIF) {
        this.sCPDURLCIF = sCPDURLCIF;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDeviceTypeCIF() {
        return deviceTypeCIF;
    }

    public void setDeviceTypeCIF(String deviceTypeCIF) {
        this.deviceTypeCIF = deviceTypeCIF;
    }

    public String getURLBase() {
        return urlBase;
    }

    public void setURLBase(String uRLBase) {
        this.urlBase = uRLBase;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModelDescription() {
        return modelDescription;
    }

    public void setModelDescription(String modelDescription) {
        this.modelDescription = modelDescription;
    }

    public String getPresentationURL() {
        return presentationURL;
    }

    public void setPresentationURL(String presentationURL) {
        this.presentationURL = presentationURL;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    /**
     * Gets the timeout for actions on the device.
     * @return timeout in milliseconds
     */
    public static int getHttpReadTimeout() {
        return httpReadTimeout;
    }

    /**
     * Sets the timeout for actions on the device.
     * @param milliseconds the new timeout in milliseconds
     */
    public static void setHttpReadTimeout(int milliseconds) {
        httpReadTimeout = milliseconds;
    }

    // private methods
    private String copyOrCatUrl(String dst, String src) {
        if (src != null) {
            if (src.startsWith("http://")) {
                dst = src;
            } else {
                if (!src.startsWith("/")) {
                    dst += "/";
                }
                dst += src;
            }
        }
        return dst;
    }
}

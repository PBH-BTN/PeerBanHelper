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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A SAX handler used to parse XML data representing a GatewayDevice
 *
 * @see org.xml.sax.helpers.DefaultHandler
 */
public class GatewayDeviceHandler extends DefaultHandler {

    /**
     * The device that should be populated with data coming from the stream
     * being parsed
     */
    private GatewayDevice device;

    /**
     * Creates a new instance of GatewayDeviceHandler that will populate the
     * fields of the supplied device
     *
     * @param device the device to configure
     */
    public GatewayDeviceHandler(final GatewayDevice device) {
        this.device = device;
    }

    /** state variables */
    private String currentElement;
    private int level = 0;
    private short state = 0;
    
    /**
     * Receive notification of the start of an element.
     *
     * Caches the element as {@link #currentElement}, and keeps track of some
     * basic state information.
     *
     * @param uri The Namespace URI, or the empty string if the
     *        element has no Namespace URI or if Namespace
     *        processing is not being performed.
     * @param localName The local name (without prefix), or the
     *        empty string if Namespace processing is not being
     *        performed.
     * @param qName The qualified name (with prefix), or the
     *        empty string if qualified names are not available.
     * @param attributes The attributes attached to the element.  If
     *        there are no attributes, it shall be an empty
     *        Attributes object.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly
     *            wrapping another exception.
     * @see org.xml.sax.ContentHandler#startElement
     */
    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {
        currentElement = localName;
        level++;
        if (state < 1 && "serviceList".compareTo(currentElement) == 0) {
            state = 1;
        }
    }

    /**
     * Receive notification of the end of an element.
     *
     * Used to update state information.
     *
     * <p>By default, do nothing.  Application writers may override this
     * method in a subclass to take specific actions at the end of
     * each element (such as finalising a tree node or writing
     * output to a file).</p>
     *
     * @param uri The Namespace URI, or the empty string if the
     *        element has no Namespace URI or if Namespace
     *        processing is not being performed.
     * @param localName The local name (without prefix), or the
     *        empty string if Namespace processing is not being
     *        performed.
     * @param qName The qualified name (with prefix), or the
     *        empty string if qualified names are not available.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly
     *            wrapping another exception.
     * @see org.xml.sax.ContentHandler#endElement
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        currentElement = "";
        level--;
        if (localName.compareTo("service")==0){
            if (device.getServiceTypeCIF() != null &&
                    device.getServiceTypeCIF().compareTo("urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1") == 0)
                state = 2;
            if (device.getServiceType() != null &&
                    (
                            device.getServiceType().contains("urn:schemas-upnp-org:service:WANIPConnection:") ||
                                    device.getServiceType().contains("urn:schemas-upnp-org:service:WANPPPConnection:")
                    ))
                state = 3;
        }
    }

    /**
     * Receive notification of character data inside an element.
     *
     * It is used to read the values of the relevant fields of the device being
     * configured.
     *
     * @param ch The characters.
     * @param start The start position in the character array.
     * @param length The number of characters to use from the
     *               character array.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly
     *            wrapping another exception.
     * @see org.xml.sax.ContentHandler#characters
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentElement.compareTo("URLBase") == 0)
            device.setURLBase(new String(ch,start,length));
        else if (state<=1) {
            if (state == 0) {
                if ("friendlyName".compareTo(currentElement) == 0)
                    device.setFriendlyName(new String(ch,start,length));
                else if ("manufacturer".compareTo(currentElement) == 0)
                    device.setManufacturer(new String(ch,start,length));
                else if ("modelDescription".compareTo(currentElement) == 0)
                    device.setModelDescription(new String(ch,start,length));
                else if ("presentationURL".compareTo(currentElement) == 0)
                    device.setPresentationURL(new String(ch,start,length));
                else if ("modelNumber".compareTo(currentElement) == 0)
                    device.setModelNumber(new String(ch,start,length));
                else if ("modelName".compareTo(currentElement) == 0)
                    device.setModelName(new String(ch,start,length));
            }
            if( currentElement.compareTo("serviceType") == 0 )
                device.setServiceTypeCIF(new String(ch,start,length));
            else if( currentElement.compareTo( "controlURL") == 0)
                device.setControlURLCIF(new String(ch,start,length));
            else if( currentElement.compareTo( "eventSubURL") == 0 )
                device.setEventSubURLCIF(new String(ch,start,length));
            else if( currentElement.compareTo( "SCPDURL") == 0  )
                device.setSCPDURLCIF(new String(ch,start,length));
            else if( currentElement.compareTo( "deviceType") == 0 )
                device.setDeviceTypeCIF(new String(ch,start,length));
        }else if (state==2){
            if( currentElement.compareTo("serviceType") == 0 )
                device.setServiceType(new String(ch,start,length));
            else if( currentElement.compareTo( "controlURL") == 0)
                device.setControlURL(new String(ch,start,length));
            else if( currentElement.compareTo( "eventSubURL") == 0 )
                device.setEventSubURL(new String(ch,start,length));
            else if( currentElement.compareTo( "SCPDURL") == 0  )
                device.setSCPDURL(new String(ch,start,length));
            else if( currentElement.compareTo( "deviceType") == 0 )
                device.setDeviceType(new String(ch,start,length));
            
        }
    }
    
}

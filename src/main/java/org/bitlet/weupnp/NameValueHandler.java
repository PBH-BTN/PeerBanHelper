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

import java.util.Map;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A simple SAX handler that is used to parse XML name value pairs in the form
 * &lt;name&gt;value&lt;/name&gt;
 *
 * @see org.xml.sax.helpers.DefaultHandler
 */
public class NameValueHandler extends DefaultHandler {

    /**
     * A reference to the name-value map to populate with the data being read
     */
    private Map<String,String> nameValue;

    /**
     * The last read element
     */
    private String currentElement;


    /**
     * Creates a new instance of a <tt>NameValueHandler</tt>, storing values in
     * the supplied map
     *
     * @param nameValue the map to store name-value pairs in
     */
    public NameValueHandler(Map<String,String> nameValue) {
        this.nameValue = nameValue;
    }

    /**
     * Receive notification of the start of an element.
     *
     * Caches the element as {@link #currentElement}, so that it will be stored
     * as a map key when the corresponding value will be read.
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
    }

    /**
     * Receive notification of the end of an element.
     *
     * It is used to reset currentElement when the XML node is closed.
     * Note: this works only when the data we are interested in does not contain
     * child nodes.
     *
     * Based on a patch provided by christophercyll and attached to issue #4:
     * http://code.google.com/p/weupnp/issues/detail?id=4
     *
     * @param uri The Namespace URI, or the empty string if the
     *        element has no Namespace URI or if Namespace
     *        processing is not being performed.
     * @param localName The local name (without prefix), or the
     *        empty string if Namespace processing is not being
     *        performed.
     * @param qName The qualified name (with prefix), or the
     *        empty string if qualified names are not available.
     * @throws SAXException Any SAX exception, possibly
     *            wrapping another exception.
     */
    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        currentElement = null;
    }

    /**
     * Receive notification of character data inside an element.
     *
     * Stores the characters as value, using {@link #currentElement} as a key 
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
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (currentElement != null) {
            String value = new String(ch,start,length);
            String old = nameValue.put(currentElement, value);
            if (old != null) {
                nameValue.put(currentElement, old + value);
            }
        }
    }
    

    
}

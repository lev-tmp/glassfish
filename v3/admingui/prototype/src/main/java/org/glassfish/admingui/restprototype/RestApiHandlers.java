/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.admingui.common.handlers;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jsftemplating.annotation.Handler;
import com.sun.jsftemplating.annotation.HandlerInput;
import com.sun.jsftemplating.annotation.HandlerOutput;
import com.sun.jsftemplating.layout.descriptors.handler.HandlerContext;
import org.glassfish.admingui.common.util.GuiUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.ws.rs.core.MultivaluedMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import org.glassfish.admin.amx.config.AMXConfigProxy;
import org.w3c.dom.NamedNodeMap;

/**
 * @author jasonlee
 */
public class RestApiHandlers {

    @Handler(id = "getDefaultProxyAttrsViaRest",
             input = {
        @HandlerInput(name = "parentObjectNameStr", type = String.class, required = true),
        @HandlerInput(name = "childType", type = String.class, required = true),
        @HandlerInput(name = "orig", type = Map.class)
    },
             output = {
        @HandlerOutput(name = "valueMap", type = Map.class)})
    public static void getDefaultValues(HandlerContext handlerCtx) {
        try {
            String endpoint = getRestEndPoint((String) handlerCtx.getInputValue("parentObjectNameStr"),
                    (String) handlerCtx.getInputValue("childType"));
            Map<String, String> orig = (Map) handlerCtx.getInputValue("orig");

            Map<String, String> defaultValues = buildDefaultValueMap(endpoint);

            if (orig == null) {
                handlerCtx.setOutputValue("valueMap", defaultValues);
            } else {
                //we only want to fill in any default value that is available. Preserve all other fields user has entered.
                for (String origKey : orig.keySet()) {
                    String defaultV = defaultValues.get(origKey);
                    if (defaultV != null) {
                        orig.put(origKey, defaultV);
                    }
                }
                handlerCtx.setOutputValue("valueMap", orig);
            }
        } catch (Exception ex) {
            GuiUtil.handleException(handlerCtx, ex);
        }
    }

    /*  Get the simpleAttributes of the bean based on the objectNameString.
     *  simpleAttributes means those NOT of Element, ie all attributes.
     *  This requires the use of AMXConfigHelper, which thus causes the limitation that
     *  this mbean has to be AMXConfigProxy, not runtiime.
     *  For runtime mbeans, you need to use getRuntimeProxyAttrs.
     */
    @Handler(id = "getProxyAttrsViaRest",
             input = {
        @HandlerInput(name = "objectNameStr", type = String.class, required = true)},
             output = {
        @HandlerOutput(name = "valueMap", type = Map.class)})
    public static void getProxyAttrs(HandlerContext handlerCtx) {
        try {
            String endpoint = getRestEndPoint((String) handlerCtx.getInputValue("objectNameStr"));
            String entity = get(endpoint, "application/xml");

            handlerCtx.setOutputValue("valueMap", getEntityAttrs(entity));
        } catch (Exception ex) {
            ex.printStackTrace();
            handlerCtx.setOutputValue("valueMap", new HashMap());
        }
    }

    protected static Map<String, String> buildDefaultValueMap(String endpoint) throws ParserConfigurationException, SAXException, IOException {
        Map<String, String> defaultValues = new HashMap<String, String>();

        String options = options(endpoint, "application/xml");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(options.getBytes()));
        Element root = doc.getDocumentElement();
        NodeList nl = root.getElementsByTagName("messageParameters");
        if (nl.getLength() > 0) {
            NodeList params = nl.item(0).getChildNodes();
            Node child;
            for (int i = 0; i < params.getLength(); i++) {
                child = params.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String defaultValue = ((Element) child).getAttribute("defaultValue");
                    if (!"".equals(defaultValue) && (defaultValue != null)) { // null test necessary?
                        String nodeName = child.getNodeName();
                        nodeName = nodeName.substring(0, 1).toUpperCase() + nodeName.substring(1);
                        defaultValues.put(nodeName, defaultValue);
                    }
                }
            }
        }
        return defaultValues;
    }

    @Handler(id = "createProxyViaRest",
             input = {
        @HandlerInput(name = "parentObjectNameStr", type = String.class, required = true),
        @HandlerInput(name = "childType", type = String.class, required = true),
        @HandlerInput(name = "attrs", type = Map.class),
        @HandlerInput(name = "skipAttrs", type = List.class),
        @HandlerInput(name = "onlyUseAttrs", type = List.class),
        @HandlerInput(name = "convertToFalse", type = List.class)},
             output = {
        @HandlerOutput(name = "result", type = String.class)})
    public static void createProxy(HandlerContext handlerCtx) {
        Map<String, String> attrs = (Map) handlerCtx.getInputValue("attrs");
        if (attrs == null) {
            attrs = new HashMap<String, String>();
        }
        String endpoint = getRestEndPoint((String) handlerCtx.getInputValue("parentObjectNameStr"),
                (String) handlerCtx.getInputValue("childType"));
        List<String> skipAttrs = (List) handlerCtx.getInputValue("skipAttrs");
        List<String> onlyUseAttrs = (List) handlerCtx.getInputValue("onlyUseAttrs");
        List<String> convertToFalse = (List) handlerCtx.getInputValue("convertToFalse");

        int status = sendCreateRequest(endpoint, attrs, skipAttrs, onlyUseAttrs, convertToFalse);
        if ((status != 200) && (status != 201)) {
            GuiUtil.getLogger().severe("CreateProxy failed.  parent=" + endpoint + "; attrs =" + attrs);
            GuiUtil.handleError(handlerCtx, GuiUtil.getMessage("msg.error.checkLog"));
            return;
        }
        handlerCtx.setOutputValue("result", endpoint);

    }



    protected static int sendCreateRequest(String endpoint, Map<String, String> attrs, List<String> skipAttrs, List<String> onlyUseAttrs, List<String> convertToFalse) {
        //Should specify either skipAttrs or onlyUseAttrs
        removeSpecifiedAttrs(attrs, skipAttrs);

        if (onlyUseAttrs != null) {
            Map newAttrs = new HashMap();
            for (String key : onlyUseAttrs) {
                if (attrs.keySet().contains(key)) {
                    newAttrs.put(key, attrs.get(key));
                }
            }
            attrs = newAttrs;
        }
        attrs = convertNullValuesToFalse(attrs, convertToFalse);
        attrs = fixKeyNames(attrs);

        return post(endpoint, attrs, "text/html");
    }

    // This method may be a really bad idea. :P
    protected static String getRestEndPoint(String parent, String child) {
        String endpoint = parent;

        if (endpoint != null) {
            if (endpoint.startsWith("amx")) {
                // amx:pp=/domain,type=resources
                endpoint = endpoint.substring(endpoint.indexOf("/")); // Strip amx:...
                endpoint = endpoint.replaceAll(",type=", "/");
                endpoint = endpoint.replaceAll(",name=", "/");
            }
        }

        if (child != null) {
            endpoint += "/" + child;
        }

        return "http://localhost:4848/management" + endpoint;
    }

    protected static String getRestEndPoint(String parent) {
        return getRestEndPoint(parent, null);
    }

    protected static Map<String, String> fixKeyNames(Map<String, String> map) {
        Map<String, String> results = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey().substring(0, 1).toLowerCase() + entry.getKey().substring(1);
            String value = entry.getValue();
            results.put(key, value);
        }

        return results;
    }

    protected static void removeSpecifiedAttrs(Map<String, String> attrs, List<String> removeList) {
        if (removeList == null || removeList.size() <= 0) {
            return;
        }
        Set<Map.Entry<String, String>> attrSet = attrs.entrySet();
        Iterator<Map.Entry<String, String>> iter = attrSet.iterator();
        while (iter.hasNext()) {
            Map.Entry<String, String> oneEntry = iter.next();
            if (removeList.contains(oneEntry.getKey())) {
                iter.remove();
            }
        }
    }

    // This is ugly, but I'm trying to figure out why the cleaner code doesn't work :(
    protected static Map<String, String> convertNullValuesToFalse(Map<String, String> attrs, List<String> convertToFalse) {
        if (convertToFalse != null) {
            Map<String, String> newAttrs = new HashMap<String, String>();
            String key;

            for (Map.Entry<String, String> entry : attrs.entrySet()) {
                key = entry.getKey();
                if (convertToFalse.contains(key) && ((entry.getValue() == null) || "null".equals(entry.getValue()))) {
                    newAttrs.put(key, "false");
                } else {
                    newAttrs.put(key, entry.getValue());
                }
            }
            return newAttrs;
        } else {
            return attrs;
        }
    }

    protected static Map<String, String> getEntityAttrs(String entity) {
        Map<String, String> attrs = new HashMap<String, String>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(entity.getBytes()));
            Element root = doc.getDocumentElement();
            NamedNodeMap nnm = root.getAttributes();
            for (int i = 0; i < nnm.getLength(); i++) {
                Node node = nnm.item(i);
                attrs.put(upperCaseFirstLetter(node.getNodeName()), node.getNodeValue());
            }
        } catch (Exception e) {
        }

        return attrs;
    }

    /**
     * Converts the first letter of the given string to Uppercase.
     *
     * @param string the input string
     * @return the string with the Uppercase first letter
     */
    public static String upperCaseFirstLetter(String string) {
        if (string == null || string.length() <= 0) {
            return string;
        }
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    //******************************************************************************************************************
    // Jersey client methods
    //******************************************************************************************************************
    protected static String get(String address, String responseType) {
        return Client.create().resource(address).accept(responseType).get(String.class);
    }

    protected static int post(String address, Map<String, String> payload, String responseType) {
        WebResource webResource = Client.create().resource(address);

        MultivaluedMap formData = new MultivaluedMapImpl();
        for (final Map.Entry<String, String> entry : payload.entrySet()) {
            formData.putSingle(entry.getKey(), entry.getValue());
        }
        ClientResponse cr = webResource.type("application/x-www-form-urlencoded").accept(responseType).post(ClientResponse.class, formData);
        return cr.getStatus();
    }

    protected static String put(String address) {
        throw new UnsupportedOperationException();
    }

    protected static String delete(String address) {
        throw new UnsupportedOperationException();
    }

    protected static String options(String address, String responseType) {
        return Client.create().resource(address).accept(responseType).options(String.class);
    }
}
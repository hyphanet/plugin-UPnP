/******************************************************************
*
*    CyberXML for Java
*
*    Copyright (C) Satoshi Konno 2004
*
*   Author: Markus Thurner (http://thoean.com)
*
*    File: JaxpParser.java
*
*    Revision;
*
*    06/15/04
*        - first revision.
*
******************************************************************/

package plugins.UPnP.org.cybergarage.xml.parser;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import plugins.UPnP.org.cybergarage.xml.Node;
import plugins.UPnP.org.cybergarage.xml.Parser;
import plugins.UPnP.org.cybergarage.xml.ParserException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.InputSource;


public class JaxpParser extends Parser
{

    public JaxpParser()
    {
        super();
    }
    
    ////////////////////////////////////////////////
    //    parse (Node)
    ////////////////////////////////////////////////

    public plugins.UPnP.org.cybergarage.xml.Node parse(plugins.UPnP.org.cybergarage.xml.Node parentNode, org.w3c.dom.Node domNode, int rank)
    {
        int domNodeType = domNode.getNodeType();
//        if (domNodeType != Node.ELEMENT_NODE)
//            return;
            
        String domNodeName = domNode.getNodeName();
        String domNodeValue = domNode.getNodeValue();

//        Debug.message("[" + rank + "] ELEM : " + domNodeName + ", " + domNodeValue + ", type = " + domNodeType + ", attrs = " + arrrsLen);

        if (domNodeType == org.w3c.dom.Node.TEXT_NODE) {
            parentNode.setValue(domNodeValue);
            return parentNode;
        }

        if (domNodeType != org.w3c.dom.Node.ELEMENT_NODE)
            return parentNode;

        plugins.UPnP.org.cybergarage.xml.Node node = new plugins.UPnP.org.cybergarage.xml.Node();
        node.setName(domNodeName);
        node.setValue(domNodeValue);

        if (parentNode != null)
            parentNode.addNode(node);

        NamedNodeMap attrMap = domNode.getAttributes(); 
        int attrLen = attrMap.getLength();
        //Debug.message("attrLen = " + attrLen);
        for (int n = 0; n<attrLen; n++) {
            org.w3c.dom.Node attr = attrMap.item(n);
            String attrName = attr.getNodeName();
            String attrValue = attr.getNodeValue();
            node.setAttribute(attrName, attrValue);
        }
        
        org.w3c.dom.Node child = domNode.getFirstChild();
        while (child != null) {
            parse(node, child, rank+1);
            child = child.getNextSibling();
        }
        
        return node;
    }

    public plugins.UPnP.org.cybergarage.xml.Node parse(plugins.UPnP.org.cybergarage.xml.Node parentNode, org.w3c.dom.Node domNode)
    {
        return parse(parentNode, domNode, 0);
    }

    /* (non-Javadoc)
     * @see plugins.UPnP.org.cybergarage.xml.Parser#parse(java.io.InputStream)
     */
    public Node parse(InputStream inStream) throws ParserException
    {
        plugins.UPnP.org.cybergarage.xml.Node root = null;
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource inSrc = new InputSource(inStream);
            Document doc = builder.parse(inSrc);

            org.w3c.dom.Element docElem = doc.getDocumentElement();

            if (docElem != null)
                root = parse(root, docElem);
/*
            NodeList rootList = doc.getElementsByTagName("root");
            Debug.message("rootList = " + rootList.getLength());
            
            if (0 < rootList.getLength())
                root = parse(root, rootList.item(0));
*/
        }
        catch (Exception e) {
            throw new ParserException(e);
        }
        
        return root;
    }

}

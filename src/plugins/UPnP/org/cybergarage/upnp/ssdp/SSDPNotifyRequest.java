/******************************************************************
*
*    CyberUPnP for Java
*
*    Copyright (C) Satoshi Konno 2002
*
*    File: SSDPMSearchRequest.java
*
*    Revision;
*
*    01/14/03
*        - first revision.
*
******************************************************************/

package plugins.UPnP.org.cybergarage.upnp.ssdp;

import plugins.UPnP.org.cybergarage.http.*;

public class SSDPNotifyRequest extends SSDPRequest
{
    ////////////////////////////////////////////////
    //    Constructor
    ////////////////////////////////////////////////

    public SSDPNotifyRequest()
    {
        setMethod(HTTP.NOTIFY);
        setURI("*");
    }
}

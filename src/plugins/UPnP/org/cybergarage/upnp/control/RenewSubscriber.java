/******************************************************************
*
*   CyberUPnP for Java
*
*   Copyright (C) Satoshi Konno 2002
*
*   File: RenewSubscriber.java
*
*   Revision:
*
*   07/07/04
*       - first revision.
*
******************************************************************/


package plugins.UPnP.org.cybergarage.upnp.control;

import plugins.UPnP.org.cybergarage.util.*;
import plugins.UPnP.org.cybergarage.upnp.*;

public class RenewSubscriber extends ThreadCore {
    public final static long INTERVAL = 120;

    ////////////////////////////////////////////////
    // Constructor
    ////////////////////////////////////////////////
    public RenewSubscriber(ControlPoint ctrlp) {
        setControlPoint(ctrlp);
    }

    ////////////////////////////////////////////////
    // Member
    ////////////////////////////////////////////////
    private ControlPoint ctrlPoint;

    public void setControlPoint(ControlPoint ctrlp) {
        ctrlPoint = ctrlp;
    }

    public ControlPoint getControlPoint() {
        return ctrlPoint;
    }

    ////////////////////////////////////////////////
    // Thread
    ////////////////////////////////////////////////
    public void run() {
        ControlPoint ctrlp         = getControlPoint();
        long         renewInterval = INTERVAL * 1000;

        while (isRunnable() == true) {
            try {
                Thread.sleep(renewInterval);
            } catch (InterruptedException e) {}

            ctrlp.renewSubscriberService();
        }
    }
}

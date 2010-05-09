package com.armyofevilrobots.dctv;

import org.red5.server.adapter.ApplicationAdapter;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.IScope;
import org.red5.server.api.stream.IServerStream;

public class Application extends MultiThreadedApplicationAdapter {//ApplicationAdapter {

	private IScope appScope;

	private IServerStream serverStream;
	
	/** {@inheritDoc} */
    @Override
	public boolean appStart(IScope app) {
	    super.appStart(app);
		log.warn("***DCTV*** APPSTART!");
		appScope = app;
		return true;
	}

	/** {@inheritDoc} */
    @Override
	public boolean appConnect(IConnection conn, Object[] params) {
		log.warn("**DCTV*** appConnect");
		// Trigger calling of "onBWDone", required for some FLV players
        log.debug("Client is %s",conn.getRemoteAddress());
	
        log.warn("The total # of params is "+params.length);
		for (int i=0;i<params.length;i++){
            try{
                log.warn("Param: "+(String)params[i]);
            }catch(Exception e){
                log.warn("Couldn't exchange param %d", i);
            }
        }

        log.warn("About to return appConnect");
		 
		return super.appConnect(conn, params);
	}

	/** {@inheritDoc} */
    @Override
	public void appDisconnect(IConnection conn) {
		log.info("***DCTV Disconnect***");
		if (appScope == conn.getScope() && serverStream != null) {
			serverStream.close();
		}
		super.appDisconnect(conn);
	}
}

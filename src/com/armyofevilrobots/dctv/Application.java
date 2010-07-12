package com.armyofevilrobots.dctv;

import java.util.*;
import org.red5.server.adapter.ApplicationAdapter;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.IScope;
import org.red5.server.api.stream.IServerStream;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.stream.ClientBroadcastStream;
//import org.red5.server.api.stream.support.SimpleBandwidthConfigure;
import com.armyofevilrobots.dctv.AuthAdaptor;

public class Application extends MultiThreadedApplicationAdapter {

	private IScope appScope;

	private IServerStream serverStream;

    private AuthAdaptor authAdaptor;
	
	/** {@inheritDoc} */
    @Override
	public boolean appStart(IScope app) {
	    super.appStart(app);
		log.warn("***DCTV*** APPSTART!");
		appScope = app;
        authAdaptor = new AuthAdaptor("com.mysql.jdbc.Driver", "jdbc:mysql://127.0.0.1/dctv_auth", "root", "password");
        setClientTTL(30);

		return true;
	}

    public boolean verifyAuthDCTV(IConnection conn, Object[] params){
        if (params.length < 1){
            log.warn("There were only {} parameters passed.", params.length);
            return false;
        }
        boolean auth = authAdaptor.checkToken((String)params[0], "broadcast");
        log.warn("Auth result is {}", auth);
        if (auth){
            log.warn("Auth succeed!");
            return true;
        }else{
            log.warn("Auth failed. Bad Token? '{}'", params[0]);
        }

        return false;
    }


	/** {@inheritDoc} */
    @Override
    public void streamPublishStart(IBroadcastStream stream){
        ClientBroadcastStream cbs = ((ClientBroadcastStream)stream.getProvider());
        boolean auth=false;
        try{
            auth = cbs.getConnection().getBoolAttribute("dctv_broadcast_auth");
        }catch(Exception e){
            auth = false;
        }
        

        log.warn("Intercepted the streamPublishStart.");
        log.warn("Scope is {}", stream.getScope());
        log.warn("Provider is {}", stream.getProvider());
        log.warn("Found stream {}", stream);
        log.warn("Provider:provider is {}", ((ClientBroadcastStream)stream.getProvider()).getConnection());
        log.warn("Provider:publishedName is {}", ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
        log.warn("Provider:publishedName is {}", ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
        log.warn("BroadcastStreamNames are {}", getBroadcastStreamNames(appScope));
        log.warn("Auth is {}", auth);
        if (!auth){
            log.warn("No auth; rejecting client.");
            cbs.close();
            return;
        }else{
            //Looks like a good auth. Let's kill the old stream now.

            IBroadcastStream oldStream = getBroadcastStream(appScope, ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
            log.warn("New stream is {} and old stream is {}", stream, oldStream);

            //Oh look, not C, but still ptr operations. Fucking java.
            if (oldStream != null && oldStream != stream){
                log.warn("Closing old broadcast stream for {}", ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
                oldStream.stop();
                oldStream.close();
                streamBroadcastClose(oldStream);
                ((ClientBroadcastStream)oldStream.getProvider()).close();
                killGhostConnections();
            }
        }
        super.streamPublishStart(stream);
    }

	/** {@inheritDoc} */
    @Override
	public boolean appConnect(IConnection conn, Object[] params) {
		log.warn("**DCTV*** appConnect");
		// Trigger calling of "onBWDone", required for some FLV players
        log.debug("Client is %s",conn.getRemoteAddress());
	
        log.warn("The total # of params is {}", params.length);
		for (int i=0;i<params.length;i++){
            try{
                log.warn("Param: "+(String)params[i]);
            }catch(Exception e){
                log.warn("Couldn't exchange param {}", i);
            }
        }

        if (!verifyAuthDCTV(conn, params)){
            log.warn("This client is not authorized to broadcast.");
            //rejectClient();
            conn.setAttribute("dctv_broadcast_auth", false);
        }else{
            conn.setAttribute("dctv_broadcast_auth", true);
            //We terminate the previous serverStream too...
        }

        //Now we try with the URL added name instead...
        //
        String scopeName=null;
        Iterator scopeNameIterator=conn.getScope().getScopeNames();
        while(scopeNameIterator.hasNext()){
            log.warn("Got a scope of {}", scopeNameIterator.next());
        }


        //Some bandwidth tunings.
        /*
        IStreamCapableConnection streamConn = (IStreamCapableConnection) conn; 
        SimpleBandwidthConfigure sbc = new SimpleBandwidthConfigure();
        sbc.setOverallBandwidth(32768*8);
        streamConn.setBandwidthConfigure (sbc);
        */

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

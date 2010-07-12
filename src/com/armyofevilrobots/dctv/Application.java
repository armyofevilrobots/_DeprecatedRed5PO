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


    public static Map<String, String> getQueryMap(String query)
    {
        if(query.startsWith("?")) query = query.substring(1);

        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();
        for (String param : params)
        {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }
        return map;
    }



    /** {@inheritDoc} */
    @Override
        public boolean appConnect(IConnection conn, Object[] params) {
            conn.setAttribute("dctv_broadcast_auth", false);
            log.warn("**DCTV*** appConnect");
            // Trigger calling of "onBWDone", required for some FLV players
            log.debug("Client is %s",conn.getRemoteAddress());
            if(conn.getConnectParams().get("queryString") != null){
                log.warn("Querystring is {}", conn.getConnectParams().get("queryString"));
                Map<String, String> qparams = getQueryMap((String)(conn.getConnectParams().get("queryString")));
                log.warn("Query map is {}", qparams);
                String mytoken = qparams.get("token");
                if (mytoken != null && authAdaptor.checkToken(mytoken, "broadcast")){
                    log.warn("Setting auth for broadcast via querystring token: {}", mytoken);
                    conn.setAttribute("dctv_broadcast_auth", true);
                }
            }



            log.warn("The total # of params is {}", params.length);
            for (int i=0;i<params.length;i++){
                try{
                    log.warn("Param: "+(String)params[i]);
                }catch(Exception e){
                    log.warn("Couldn't exchange param {}", i);
                }
            }

            //We only check again if the token wasn't in the querystring
            if (conn.getBoolAttribute("dctv_broadcast_auth")!=true && verifyAuthDCTV(conn, params)){
                log.warn("This client is authorized to broadcast.");
                conn.setAttribute("dctv_broadcast_auth", true);
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

package com.armyofevilrobots.dctv;

import java.security.SignatureException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import javax.servlet.*;
import com.mysql.jdbc.Driver;
import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;
import java.security.SecureRandom;
import java.math.BigInteger;

public class AuthAdaptor {
    Connection _conn = null;
    //Inited by the constructor.
    String _driver=null;
    String _url=null;
    String _username=null;
    String _password=null;
    private SecureRandom random = new SecureRandom();

	private static Logger log = Red5LoggerFactory.getLogger(DemoService.class, "dctv");
	
	{
		log.info("DCTV AuthAdaptor created");
	}


    private boolean _conn_is_alive(){
        if(_conn==null) return false;
        ResultSet pout=null;
        PreparedStatement pstmpt = null;
        log.warn("Checking connection...");
        try{
            //Do some cheap query
            pout = _conn.prepareStatement("do 1").executeQuery();
        }catch(SQLException e){
            log.warn("Dead conn at pstmt. {}", e);
            return false;
        }
        return true;
        /*
            rs.next();
            log.warn("Result: {}",pout);
            if(pout==null){
                return false;
            }
            log.warn("_conn is ok.");
            return true;
        }catch(SQLException e){
            //On failure, regenerate.
            log.warn("_conn failed: {}",e);
            return false;
        }catch(Exception e){
            log.error("Unexpected exception {}",e);
            return false;

        }*/
    }

    private Connection conn(){
        boolean retried=false;
        do{
            if(_conn==null){
                log.warn("Recycling sql connection. It is null.");
                try{
                    Class c = Class.forName(_driver);
                    log.warn("Driver class {}",c);
                    _conn = DriverManager.getConnection(System.getenv("dctv_dburi"), System.getenv("dctv_dbuser"), System.getenv("dctv_dbpass"));
                    log.warn("_conn {}",_conn);
                    log.warn("Generated DCTV auth tool.");
                    return _conn;
                }catch(java.lang.ClassNotFoundException e){
                    log.error("Failed to load driver: {}", e);
                    return null;
                    //throw new ClassNotFoundException();
                }catch(SQLException e){
                    log.error("Failed to connect to mysql: {}", e);
                    return null;
                    //throw e;
                }catch(Exception e){
                    log.error("Unexpected {}",e);
                }//if
            }//conn is null
            if(!_conn_is_alive()){
                log.warn("Conn is not alive. Recycle? Retried: {}",retried);
                _conn=null;
                if(retried) return null;
                retried=true;
            }
        log.warn("End of do/while retried:{}, _conn:{}",retried,_conn);
        }while(!retried && _conn==null);
        return _conn;
    }


    private String genToken(int uid, String username, int timestamp, String disposition){
        String token = null;
        try{
            log.warn("Generating token...");
            PreparedStatement pstmpt = conn().prepareStatement("insert into tokens (user_id, token, disposition) values (?, ?, ?)");
            pstmpt.setInt(1, uid);
            token = new BigInteger(160, random).toString(32);
            pstmpt.setString(2, token);
            pstmpt.setString(3, disposition);
            pstmpt.executeUpdate();
            ResultSet rs = pstmpt.getGeneratedKeys();
            if (rs.next()){
                int id = rs.getInt(1);
                pstmpt = conn().prepareStatement("select token from tokens where id=?");
                pstmpt.setInt(1,id);
                ResultSet nrs = pstmpt.executeQuery();
                nrs.next();
                return nrs.getString(1);
            }
        }catch(SQLException e){
            log.error("Could not get token due to {}", e);
        }
        return null;
    }


    private int genTimestamp(){
        return 1;
    }

    public AuthAdaptor(String driver, String url, String username, String password){
        _driver=driver;
        _url=url;
        _username=username;
        _password=password;
    }

    public String checkCredentials(String username, String password, String disposition){
        //Check if this login/pass are OK, and return a valid token if so
        log.warn("Checking {} {}", username, password);
        String query = "SELECT id , enabled from credentials WHERE username = ? AND password = ? and enabled=1";
        int uid = 0;
        int active = 0;
        try{
            PreparedStatement pstmt = conn().prepareStatement(query); // create a statement
            pstmt.setString(1, username); // set input parameters
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            rs.next();
            uid = rs.getInt(1);
            active = rs.getInt(1);
        }catch(SQLException e){
            log.error("Exception getting sql result {}", e);
            return null;
        }
        log.warn("Got OK auth. {}", disposition);
        return genToken(uid, username, genTimestamp(), disposition);
        //Otherwise, we would return null
    }

    public boolean checkToken(String token, String subject){
        //Check if the token is valid, expire and return TRUE if so.
        //Subject is the room/whatever we are authing for.
        log.warn("token: "+token+" subject: "+subject);
        PreparedStatement pstmt = null;
        int tid=-1;
        try{
            log.warn("Conn is {}",_conn);
            pstmt = conn().prepareStatement("select id from tokens where token=? and disposition=?");
            pstmt.setString(1,token);
            pstmt.setString(2,subject);
            ResultSet rs = pstmt.executeQuery();
            if(rs.next()){
                tid = rs.getInt(1);
            }
        }catch(SQLException e){
            log.error("Auth failed due to {}", e);
            return false;
        }
        try{
            pstmt = conn().prepareStatement("delete from tokens where id=?");
            pstmt.setInt(1,tid);
            pstmt.executeUpdate();
        }catch(SQLException e){
            log.error("Auth failed due to {}", e);
            return false;
        }

        return true;
    }
}


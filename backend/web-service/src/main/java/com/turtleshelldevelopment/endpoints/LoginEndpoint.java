package com.turtleshelldevelopment.endpoints;

import com.auth0.jwt.JWT;
import com.turtleshelldevelopment.Issuers;
import com.turtleshelldevelopment.WebServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import spark.Request;
import spark.Response;
import spark.Route;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@SuppressWarnings("unchecked")
public class LoginEndpoint implements Route {

    /***
     * Handles Login
     * Implementation requires a HTML Form
     * example query: ?username=XXXXXXXXX&password=XXXXXXXXXXX
     */
    @Override
    public Object handle(Request request, Response response) {
        String username, password;
        try {
            JSONObject body = (JSONObject) new JSONParser().parse(request.body());

            //Validate Authentication POST Request
            username = (String) body.get("username");
            password = (String) body.get("password");
            if(validate(username, password)) {
                //System.out.println("user: " + username + ", password: " + password);
                JSONObject success = new JSONObject();
                success.put("success", true);
                success.put("2faRequired", true);
                generateMfaJWTToken(username, response);
                response.status(200);
                return success;
            } else {
                JSONObject failure = new JSONObject();
                response.status(401);
                failure.put("success", false);
                failure.put("message","Invalid Username or Password");
                return failure;
            }
        } catch (SQLException e) {
            response.status(500);
            WebServer.serverLogger.warn(String.format("Error on handling login: %s", e.getMessage()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | ParseException e) {
            response.status(401);
            //WebServer.serverLogger.warn(String.format("Error on handling login: %s", e.getMessage()));
        }
        return "";
    }

    private boolean validate(String username, String password) throws SQLException, NoSuchAlgorithmException, InvalidKeySpecException {
        if(username == null || password == null || username.equals("") || password.equals("")) return false;
        //Get User From database if they exist
        CallableStatement getUser = WebServer.database.getConnection().prepareCall("CALL GET_USER(?)");
        getUser.setString(1, username);
        //Check for a valid user
        ResultSet rs;
        if((rs = getUser.executeQuery()).next()) {
            byte[] correct_password_hash = rs.getBytes("password_hash");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), rs.getBytes("salt"), 65536, 64 * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] password_hash = factory.generateSecret(spec).getEncoded();
            //WebServer.serverLogger.debug("Password Hash: " + Arrays.toString(password_hash));
            int diff = correct_password_hash.length ^ password_hash.length;
            for(int i = 0; i < correct_password_hash.length && i < password_hash.length; i++)
            {
                diff |= correct_password_hash[i] ^ password_hash[i];
            }
            //WebServer.serverLogger.debug("diff is " + diff);
            getUser.close();
            return diff == 0;
        } else {
            return false;
        }
    }

    private void generateMfaJWTToken(String username, Response response) {
        LocalDateTime time = LocalDateTime.now();
        Instant inst = time.plusMinutes(3).toInstant(ZoneOffset.UTC);
        String jwt = JWT.create()
                .withIssuer(Issuers.MFA_LOGIN.getIssuer())
                .withSubject(username)
                .withClaim("mfa", true)
                .withNotBefore(time.toInstant(ZoneOffset.UTC))
                .withExpiresAt(inst)
                .sign(WebServer.JWT_ALGO);
        response.cookie("/","token", jwt, 180, true, true);
    }
}
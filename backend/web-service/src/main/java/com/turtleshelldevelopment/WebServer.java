package com.turtleshelldevelopment;

import com.auth0.jwt.algorithms.Algorithm;
import com.turtleshelldevelopment.endpoints.LoginEndpoint;
import com.turtleshelldevelopment.endpoints.NewAccountEndpoint;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Logger;

import static spark.Service.ignite;
import static spark.Spark.*;

public class WebServer {
    public static Dotenv env = Dotenv.load();
    public static Logger serverLogger;
    public static Algorithm JWT_ALGO;
    public static Database database;


    /***
     * Created By: Colin Kinzel
     * Modified By: Colin (9/20/22)
     */
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        serverLogger = Logger.getLogger("Dashboard-Backend");
        serverLogger.info("Setting up JWT...");

        serverLogger.info("Connecting to Database...");
        database = new Database();
        serverLogger.info("Successfully connected to Database!");
        KeyPair jwtPair = loadOrGenerate();
        JWT_ALGO = Algorithm.RSA512((RSAPublicKey) jwtPair.getPublic(), (RSAPrivateKey) jwtPair.getPrivate());

        port(80);
        path("/api", () -> {
            post("/login", new LoginEndpoint());
            path("/account", () -> {
                post("/new", new NewAccountEndpoint());
            });
        });
        ignite();
    }

    /***
     * Loads or generate public and private key for JWT Authentication
     */
    private static KeyPair loadOrGenerate() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        File privateKey = new File("priv.key");
        File publicKey  = new File("key.pub");
        if(privateKey.exists() && publicKey.exists()) {
            //Load Files
            byte[] privKey = Files.readAllBytes(privateKey.toPath());
            byte[] pubKey = Files.readAllBytes(publicKey.toPath());

            PKCS8EncodedKeySpec priv = new PKCS8EncodedKeySpec(privKey);
            X509EncodedKeySpec pub = new X509EncodedKeySpec(pubKey);
            KeyFactory keyFactory =KeyFactory.getInstance("RSA");
            PrivateKey loadPrivateKey = keyFactory.generatePrivate(priv);
            PublicKey loadPublicKey = keyFactory.generatePublic(pub);
            if(!(loadPublicKey instanceof RSAPublicKey)) {
                throw new IllegalArgumentException("Public Key is not an RSA Public Key");
            } else if(!(loadPrivateKey instanceof RSAPrivateKey)) {
                throw new IllegalArgumentException("Private Key is not an RSA Private Key");
            }
            return new KeyPair(loadPublicKey, loadPrivateKey);
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            FileOutputStream privateKeyFile = new FileOutputStream(privateKey);
            FileOutputStream publicKeyFile = new FileOutputStream(publicKey);
            privateKeyFile.write(kp.getPrivate().getEncoded());
            publicKeyFile.write(kp.getPublic().getEncoded());
            privateKeyFile.close();
            publicKeyFile.close();
            return kp;
        }
    }
}

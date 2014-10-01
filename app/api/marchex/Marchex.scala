package bravo.api.marchex

import java.net._
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.XmlRpcException;


object Marchex {
  
  def makeCall(url: String, user: String, pass: String): Unit = {
    val config = new XmlRpcClientConfigImpl();
    config.setServerURL(new URL("http://api.voicestar.com/api/xmlrpc/1"));
    config.setBasicUserName("apiuser@mydomain.com");
    config.setBasicPassword("ChangeMe123@");
    val client = new XmlRpcClient()
    client.setConfig(config)
    config.setEncoding("ISO-8859-1");
    //client.setTypeFacotry(new TypeFactoryIso8601(client))
    client.setTransportFactory(new XmlRpcSunHttpTransportFactory(client));

}
/*
                                                                                                                                   */

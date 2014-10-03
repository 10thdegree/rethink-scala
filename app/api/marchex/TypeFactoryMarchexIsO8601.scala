package bravo.api.marchex

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcController;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.DateParser;
import org.apache.xmlrpc.parser.TypeParser;
import org.apache.xmlrpc.serializer.DateSerializer;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.apache.ws.commons.util.NamespaceContextImpl;
import org.xml.sax.SAXException;

class TypeFactoryMarchexIso8601(pController: XmlRpcController) extends TypeFactoryImpl(pController) {

  private def newFormat(): DateFormat = {
    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
  }

  override def getParser( pConfig: XmlRpcStreamConfig,  pContext: NamespaceContextImpl,  pURI: String,  pLocalName: String): TypeParser = {
    if (DateSerializer.DATE_TAG.equals(pLocalName)) {
      new DateParser(newFormat())
    } else {
      super.getParser(pConfig, pContext, pURI, pLocalName)
    }
  }

  override def getSerializer(pConfig: XmlRpcStreamConfig, pObject: Object): TypeSerializer = {
    if (pObject.isInstanceOf[DateFormat]) {
      new DateSerializer(newFormat());
    } else {
      super.getSerializer(pConfig, pObject);
    }
  }
}

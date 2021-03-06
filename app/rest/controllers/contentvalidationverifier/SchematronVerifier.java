package rest.controllers.contentvalidationverifier;

import org.beybunproject.xmlContentVerifier.Schema;
import org.beybunproject.xmlContentVerifier.XmlContentVerifier;
import rest.controllers.common.Constants;

import java.net.MalformedURLException;
import java.net.URL;

import static rest.controllers.common.Constants.FAILURE;
import static rest.controllers.common.Constants.SUCCESS;

/**
 * This class subclasses SchemaVerifier and implements verify methods. If an exception received from Minder core,
 * it simply throws the exception. In case of exception-free validation, Minder does not throw any exception.
 * The verify methods return the schematron report(provided by Minder), which states the affirmative result or gives the failures.
 *
 * The Schematron validation of Minder produces just two result: Success or fail.
 *
 *
 * @author: Melis Ozgur Cetinkaya Demir
 * @date: 26/10/15.
 */
public class SchematronVerifier extends SchemaVerifier{
    @Override
    public String verify(Schema schema, byte[] xml) throws RuntimeException{
        return XmlContentVerifier.verifySchematron(schema, xml, null);
    }

    @Override
    public String verify(String url, byte[] xml) throws RuntimeException, MalformedURLException {
        return XmlContentVerifier.verifySchematron(url, xml,null);
    }

    @Override
    public String getResult(String report){
        if (report.contains("<svrl:failed-assert")) {
            return FAILURE;
        }
        return SUCCESS;
    }

}


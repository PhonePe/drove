package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

@UtilityClass
public class XmlParsingUtils {

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = createFactory();

    @SneakyThrows
    private static DocumentBuilderFactory createFactory() {
        val factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory;
    }

    @SneakyThrows
    public static DocumentBuilder newDocumentBuilder() {
        return DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
    }

    public static Document parseXml(final String xmlContent) throws Exception {
        val builder = newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xmlContent)));
    }
}

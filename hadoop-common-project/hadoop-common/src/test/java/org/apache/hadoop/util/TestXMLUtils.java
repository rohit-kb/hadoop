/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.util;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.hadoop.test.AbstractHadoopTestBase;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXMLUtils extends AbstractHadoopTestBase {

  @Test
  public void testSecureDocumentBuilderFactory() throws Exception {
    DocumentBuilder db = XMLUtils.newSecureDocumentBuilderFactory().newDocumentBuilder();
    Document doc = db.parse(new InputSource(new StringReader("<root/>")));
    assertThat(doc).describedAs("parsed document").isNotNull();
  }

  @Test
  public void testExternalDtdWithSecureDocumentBuilderFactory() throws Exception {
    assertThrows(SAXException.class, () -> {
      DocumentBuilder db = XMLUtils.newSecureDocumentBuilderFactory().newDocumentBuilder();
      try (InputStream stream = getResourceStream("/xml/external-dtd.xml")) {
        Document doc = db.parse(stream);
      }
    });
  }

  @Test
  public void testEntityDtdWithSecureDocumentBuilderFactory() throws Exception {
    assertThrows(SAXException.class, () -> {
      DocumentBuilder db = XMLUtils.newSecureDocumentBuilderFactory().newDocumentBuilder();
      try (InputStream stream = getResourceStream("/xml/entity-dtd.xml")) {
        Document doc = db.parse(stream);
      }
    });
  }

  @Test
  public void testSecureSAXParserFactory() throws Exception {
    SAXParser parser = XMLUtils.newSecureSAXParserFactory().newSAXParser();
    parser.parse(new InputSource(new StringReader("<root/>")), new DefaultHandler());
  }

  @Test
  public void testExternalDtdWithSecureSAXParserFactory() throws Exception {
    assertThrows(SAXException.class, () -> {
      SAXParser parser = XMLUtils.newSecureSAXParserFactory().newSAXParser();
      try (InputStream stream = getResourceStream("/xml/external-dtd.xml")) {
        parser.parse(stream, new DefaultHandler());
      }
    });
  }

  @Test
  public void testEntityDtdWithSecureSAXParserFactory() throws Exception {
    assertThrows(SAXException.class, () -> {
      SAXParser parser = XMLUtils.newSecureSAXParserFactory().newSAXParser();
      try (InputStream stream = getResourceStream("/xml/entity-dtd.xml")) {
        parser.parse(stream, new DefaultHandler());
      }
    });
  }

  @Test
  public void testSecureTransformerFactory() throws Exception {
    Transformer transformer = XMLUtils.newSecureTransformerFactory().newTransformer();
    DocumentBuilder db = XMLUtils.newSecureDocumentBuilderFactory().newDocumentBuilder();
    Document doc = db.parse(new InputSource(new StringReader("<root/>")));
    try (StringWriter stringWriter = new StringWriter()) {
      transformer.transform(new DOMSource(doc), new StreamResult(stringWriter));
      assertThat(stringWriter.toString()).contains("<root");
    }
  }

  @Test
  public void testExternalDtdWithSecureTransformerFactory() throws Exception {
    assertThrows(TransformerException.class, () -> {
      Transformer transformer = XMLUtils.newSecureTransformerFactory().newTransformer();
      try (
          InputStream stream = getResourceStream("/xml/external-dtd.xml");
          StringWriter stringWriter = new StringWriter()
      ) {
        transformer.transform(new StreamSource(stream), new StreamResult(stringWriter));
      }
    });
  }

  @Test
  public void testSecureSAXTransformerFactory() throws Exception {
    Transformer transformer = XMLUtils.newSecureSAXTransformerFactory().newTransformer();
    DocumentBuilder db = XMLUtils.newSecureDocumentBuilderFactory().newDocumentBuilder();
    Document doc = db.parse(new InputSource(new StringReader("<root/>")));
    try (StringWriter stringWriter = new StringWriter()) {
      transformer.transform(new DOMSource(doc), new StreamResult(stringWriter));
      assertThat(stringWriter.toString()).contains("<root");
    }
  }

  @Test
  public void testExternalDtdWithSecureSAXTransformerFactory() throws Exception {
    assertThrows(TransformerException.class, () -> {
      Transformer transformer = XMLUtils.newSecureSAXTransformerFactory().newTransformer();
      try (
          InputStream stream = getResourceStream("/xml/external-dtd.xml");
          StringWriter stringWriter = new StringWriter()
      ) {
        transformer.transform(new StreamSource(stream), new StreamResult(stringWriter));
      }
    });
  }

  @Test
  public void testBestEffortSetAttribute() throws Exception {
    TransformerFactory factory = TransformerFactory.newInstance();
    AtomicBoolean flag1 = new AtomicBoolean(true);
    XMLUtils.bestEffortSetAttribute(factory, flag1, "unsupportedAttribute false", "abc");
    assertFalse(flag1.get(), "unexpected attribute results in return of false?");
    AtomicBoolean flag2 = new AtomicBoolean(true);
    XMLUtils.bestEffortSetAttribute(factory, flag2, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    assertTrue(flag2.get(), "expected attribute results in return of true?");
    AtomicBoolean flag3 = new AtomicBoolean(false);
    XMLUtils.bestEffortSetAttribute(factory, flag3, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    assertFalse(flag3.get(),
        "expected attribute results in return of false if input flag is false?");
  }

  private static InputStream getResourceStream(final String filename) {
    return TestXMLUtils.class.getResourceAsStream(filename);
  }
}

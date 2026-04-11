package dev.deepcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

class CheckstyleConfigFilesTest {

    private static final Path MAIN_CHECKSTYLE_PATH = Path.of("config", "checkstyle", "checkstyle.xml");
    private static final Path TEST_CHECKSTYLE_PATH = Path.of("config", "checkstyle", "checkstyle-test.xml");

    @Test
    void checkstyleFiles_exist() {
        assertTrue(Files.exists(MAIN_CHECKSTYLE_PATH));
        assertTrue(Files.exists(TEST_CHECKSTYLE_PATH));
    }

    @Test
    void checkstyleMain_isWellFormedAndContainsExpectedRules() throws Exception {
        Document document = parseXml(MAIN_CHECKSTYLE_PATH);
        Element checker = document.getDocumentElement();

        assertEquals("module", checker.getTagName());
        assertEquals("Checker", checker.getAttribute("name"));

        assertEquals("UTF-8", getPropertyValue(checker, "charset"));

        List<String> moduleNames = collectModuleNames(checker);
        assertTrue(moduleNames.contains("FileTabCharacter"));
        assertTrue(moduleNames.contains("JavadocPackage"));
        assertTrue(moduleNames.contains("TreeWalker"));
        assertTrue(moduleNames.contains("JavadocMethod"));
        assertTrue(moduleNames.contains("MissingJavadocMethod"));
    }

    @Test
    void checkstyleTest_isWellFormedAndContainsFocusedRules() throws Exception {
        Document document = parseXml(TEST_CHECKSTYLE_PATH);
        Element checker = document.getDocumentElement();

        assertEquals("module", checker.getTagName());
        assertEquals("Checker", checker.getAttribute("name"));
        assertEquals("UTF-8", getPropertyValue(checker, "charset"));

        List<String> moduleNames = collectModuleNames(checker);
        assertTrue(moduleNames.contains("FileTabCharacter"));
        assertTrue(moduleNames.contains("TreeWalker"));
        assertTrue(moduleNames.contains("AvoidStarImport"));
        assertTrue(moduleNames.contains("UnusedImports"));
        assertTrue(moduleNames.contains("NeedBraces"));
        assertFalse(moduleNames.contains("JavadocPackage"));
    }

    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        EntityResolver noOpResolver = (publicId, systemId) -> new InputSource(new StringReader(""));
        builder.setEntityResolver(noOpResolver);

        try (var in = Files.newInputStream(path)) {
            return builder.parse(in);
        }
    }

    private static String getPropertyValue(Element checker, String propertyName) {
        NodeList children = checker.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"property".equals(element.getTagName())) {
                continue;
            }
            if (propertyName.equals(element.getAttribute("name"))) {
                return element.getAttribute("value");
            }
        }
        return null;
    }

    private static List<String> collectModuleNames(Element root) {
        List<String> names = new ArrayList<>();
        collectModuleNamesRecursive(root, names);
        return names;
    }

    private static void collectModuleNamesRecursive(Element element, List<String> names) {
        if ("module".equals(element.getTagName())) {
            String name = element.getAttribute("name");
            if (!name.isBlank()) {
                names.add(name);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child) {
                collectModuleNamesRecursive(child, names);
            }
        }
    }
}

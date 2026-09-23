package com.nextgis.mobile.view;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

/** Guards against adding recording controls to only the default phone resource. */
public class MapLayoutContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private File resources() {
        File root = new File("src/main/res");
        return root.isDirectory() ? root : new File("app/src/main/res");
    }

    private Document read(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File(resources(), path));
    }

    @Test public void allMapConfigurationsUseTheSameCompleteContent() throws Exception {
        for (String path : new String[]{"layout/fragment_map.xml", "layout-land/fragment_map.xml",
                "layout/fragment_map_tab.xml"}) {
            NodeList includes = read(path).getElementsByTagName("include");
            assertEquals(path, 1, includes.getLength());
            assertEquals(path, "@layout/layout_map_content",
                    ((Element) includes.item(0)).getAttribute("layout"));
        }
        NodeList elements = read("layout/layout_map_content.xml").getElementsByTagName("*");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String id = element.getAttributeNS(ANDROID, "id");
            if (!id.isEmpty()) assertTrue("Duplicate view " + id, ids.add(id));
            if (id.equals("@+id/walk_recording_panel")) {
                assertEquals("@id/map_action_row", element.getAttributeNS(ANDROID, "layout_above"));
                assertEquals("@id/map_control_rail", element.getAttributeNS(ANDROID, "layout_toStartOf"));
            }
            if (id.equals("@+id/multiple_actions")) {
                assertEquals("left", element.getAttributeNS("http://schemas.android.com/apk/res-auto",
                        "fab_expandDirection"));
            }
        }
        for (String id : new String[]{"action_track_status", "walk_recording_panel", "action_azimuth",
                "action_ruler", "action_zoom_in", "action_zoom_out", "multiple_actions", "fl_attributes"}) {
            assertTrue("Missing map control " + id, ids.contains("@+id/" + id));
        }
    }
}

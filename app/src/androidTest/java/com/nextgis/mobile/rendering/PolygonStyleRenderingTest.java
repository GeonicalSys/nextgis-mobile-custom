package com.nextgis.mobile.rendering;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.content.ContentValues;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.*;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.style.layers.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** SQL -> saved category JSON -> production factories -> actual phone GPU pixels. */
@RunWith(AndroidJUnit4.class)
public class PolygonStyleRenderingTest {
    private static SimplePolygonStyle style(int pattern) {
        SimplePolygonStyle style=new SimplePolygonStyle(Color.YELLOW,Color.BLUE);
        style.setFill(false);style.setAlpha(0);style.setOutAlpha(255);style.setWidth(8);style.setFillPattern(pattern);
        style.setPatternOptions(new PolygonPatternOptions());style.getPatternOptions().setColor(Color.RED);style.getPatternOptions().setAlpha(255);
        style.getPatternOptions().setSpacing(12);style.getPatternOptions().setWidth(2);
        style.setOutlineOptions(new PolygonOutlineOptions());style.getOutlineOptions().setType(PolygonOutlineOptions.DASH);
        style.getOutlineOptions().setCasing(true);style.getOutlineOptions().setCasingColor(Color.GREEN);
        style.getOutlineOptions().setCasingWidth(3);return style;
    }
    private static int pixels(Bitmap bitmap,int channel) {
        int count=0;
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++) {
            int c=bitmap.getPixel(x,y);int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            if(channel==0 && r>180 && g<100 && b<100 || channel==1 && g>180 && r<100 && b<100
                    || channel==2 && b>180 && r<100 && g<100)count++;
        }return count;
    }
    @Test public void independentPatternsAndAdvancedContoursRenderAndCleanUpInMixedCategories() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            VectorLayerUI layer=f.add(GeoConstants.GTPolygon);f.insert(layer,0,"A");f.insert(layer,1,"B");f.insert(layer,2,null);
            SimplePolygonStyle advanced=style(2);
            SimplePolygonStyle legacy=new SimplePolygonStyle(Color.YELLOW,Color.BLACK);legacy.setFill(true);legacy.setAlpha(255);legacy.setOutAlpha(255);
            SimplePolygonStyle disabled=style(3);disabled.getPatternOptions().setEnabled(false);disabled.getOutlineOptions().setEnabled(false);
            FieldStyleRule rules=new FieldStyleRule(layer);rules.setKey("kind");rules.setOtherStyle(legacy);rules.setStyle("A",advanced);rules.setStyle("B",disabled);
            f.screen.onActivity(activity->((MapDrawable)f.app.getMap()).setZoomAndCenter(16,new GeoPoint(0,100),false,0));
            f.bind(layer,legacy,rules);f.zoom(16);
            Bitmap image=f.snapshot();assertTrue("Pattern renders with the background OFF",pixels(image,0)>80);
            assertTrue("Blue dashes are drawn",pixels(image,2)>20);assertTrue("Green secondary contour is drawn",pixels(image,1)>20);
            assertEquals(3,f.picked(layer));
            String key=ConfigurablePolygonPatternRegistry.imageName(advanced);
            f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                assertNotNull(nativeStyle.getImage(key));
                LineLayer primary=(LineLayer)nativeStyle.getLayer("nglayer-layer-"+layer.getId()+"_outline-layout-contour-1-primary");
                assertArrayEquals(new Float[]{2f,2f},primary.getLineDasharray().getValue());
                assertEquals("round",primary.getLineCap().getValue());
            });
            VectorStyleRenderingTest.saveFireflySnapshot(f,image,"polygon-mixed-categories");
            f.zoom(17.5);assertTrue("Fractional zoom preserves visible pattern",pixels(f.snapshot(),0)>80);
            advanced.getPatternOptions().setAlpha(0);rules.setStyle("A",advanced);f.bind(layer,legacy,rules);
            assertEquals("Pattern alpha is independent of both contours",0,pixels(f.snapshot(),0));
            assertTrue(pixels(f.snapshot(),2)>20);
            f.screen.onActivity(activity->{layer.setVisible(false);((MapDrawable)f.app.getMap()).checkLayerVisibility(layer.getId());});
            f.renderedFrame();assertEquals(0,pixels(f.snapshot(),2));
            f.screen.onActivity(activity->{layer.setVisible(true);((MapDrawable)f.app.getMap()).checkLayerVisibility(layer.getId());});
            f.bind(layer,legacy,null);
            f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                assertNull("Last owner releases procedural texture",nativeStyle.getImage(key));
                for(org.maplibre.android.style.layers.Layer nativeLayer:nativeStyle.getLayers()) assertFalse(nativeLayer.getId().startsWith("nglayer-layer-"+layer.getId()+"_outline-layout-contour-"));
            });
        }
    }
    @Test public void singleAndMultiPolygonHolesStayClearForAllPatternsAndDoubleContours() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            for(int type:new int[]{GeoConstants.GTPolygon,GeoConstants.GTMultiPolygon}) {
                VectorLayerUI layer=f.add(type);
                GeoPolygon polygon=new GeoPolygon();polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                for(int ringIndex=0;ringIndex<2;ringIndex++) {
                    int size=ringIndex==0?140:30;GeoLinearRing ring=new GeoLinearRing();ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                    for(double[] xy:new double[][]{{-size,-size},{size,-size},{size,size},{-size,size},{-size,-size}})ring.add(new GeoPoint(xy[0],xy[1]));
                    if(ringIndex==0)polygon.setOuterRing(ring);else polygon.addInnerRing(ring);
                }
                GeoGeometry geometry=polygon;
                if(type==GeoConstants.GTMultiPolygon){GeoMultiPolygon multi=new GeoMultiPolygon();multi.setCRS(GeoConstants.CRS_WEB_MERCATOR);multi.add(polygon);geometry=multi;}
                ContentValues values=new ContentValues();values.put(Constants.FIELD_GEOM,geometry.toBlob());values.put("kind","A");values.put("name","Pattern with hole");
                assertTrue(layer.insertAddChanges(values)!=Constants.NOT_FOUND);
                f.zoom(17);
                AtomicReference<PointF> center=new AtomicReference<>();f.screen.onActivity(activity->center.set(((MapDrawable)f.app.getMap()).getMaplibreMap().getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(0,0))));
                SimplePolygonStyle blank=new SimplePolygonStyle();blank.setFill(false);blank.setOutAlpha(0);f.bind(layer,blank,null);
                int background=f.snapshot().getPixel(Math.round(center.get().x),Math.round(center.get().y));
                for(int pattern=1;pattern<=6;pattern++) {
                    SimplePolygonStyle style=style(pattern);style.getPatternOptions().setAngle(27);style.getPatternOptions().setSecondAngle(115);
                    style.getOutlineOptions().setType(PolygonOutlineOptions.SOLID);style.getOutlineOptions().setGap(3);
                    f.bind(layer,style,null);Bitmap image=f.snapshot();assertTrue("Actual built-in pattern "+pattern,pixels(image,0)>100);
                    int color=image.getPixel(Math.round(center.get().x),Math.round(center.get().y));
                    assertEquals("Interior ring retains the independently captured map background",background,color);
                    if(pattern==2)VectorStyleRenderingTest.saveFireflySnapshot(f,image,"polygon-holes-"+type);
                }
                f.screen.onActivity(activity->{layer.setVisible(false);((MapDrawable)f.app.getMap()).checkLayerVisibility(layer.getId());});
            }
        }
    }
}

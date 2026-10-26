package com.nextgis.mobile.rendering;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.FieldStyleRule;
import com.nextgis.maplib.display.LabelAttributes;
import com.nextgis.maplib.display.SimpleLineStyle;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.MPLFeaturesUtils;
import com.nextgis.maplib.map.VisibleLineLabelLayout;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Native camera/source, complete Russian glyphs and current-extent placement, not a geometry mock. */
@RunWith(AndroidJUnit4.class)
public class VisibleLineLabelsTest {
    private static void await(BooleanSupplier condition) throws Exception {
        long end=SystemClock.uptimeMillis()+30000;
        while(!condition.getAsBoolean() && SystemClock.uptimeMillis()<end)Thread.sleep(80);
        assertTrue("The visible line labels did not settle",condition.getAsBoolean());
    }
    private static GeoLineString line(double... xy) {
        GeoLineString line=new GeoLineString();line.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        for(int i=0;i<xy.length;i+=2)line.add(new GeoPoint(xy[i],xy[i+1]));return line;
    }
    private static long insert(VectorLayerUI layer,GeoGeometry geometry,String kind,String text) throws Exception {
        ContentValues row=new ContentValues();row.put(Constants.FIELD_GEOM,geometry.toBlob());
        row.put("kind",kind);row.put("name",text);
        long id=layer.insertAddChanges(row);assertTrue(id!=Constants.NOT_FOUND);return id;
    }
    private static SimpleLineStyle style() {
        SimpleLineStyle style=new SimpleLineStyle();style.setColor(Color.BLUE);style.setOutColor(Color.BLUE);style.setWidth(1);
        style.setField("name");style.setTextColor(Color.RED);style.setTextSize(5);
        LabelAttributes labels=LabelAttributes.fromStyle(style);labels.setTextFont("Open Sans Bold");
        labels.setTextAllowOverlap(true);labels.setTextHaloWidth(0);labels.setTextKeepUpright(true);
        // Old repeat/curvature settings cannot split or hide the new one-per-visible-object label.
        labels.setLineLabelRepeat(true);labels.setTextMaxAngle(1);return style;
    }
    private static void background(VectorStyleRenderingTest.Fixture f) {
        f.screen.onActivity(a -> ((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().addLayerAt(
                new BackgroundLayer("line-label-test-background").withProperties(PropertyFactory.backgroundColor(Color.WHITE)),0));
    }
    private static Map<String,Feature> labels(VectorStyleRenderingTest.Fixture f,VectorLayerUI layer) {
        Map<String,Feature> labels=new LinkedHashMap<>();
        f.screen.onActivity(a -> {
            GeoJsonSource source=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle()
                    .getSourceAs(layer.getPath()+MPLFeaturesUtils.source_polygon_text);
            if(source!=null)for(Feature feature:source.querySourceFeatures(null)) {
                assertTrue("A whole label uses one point anchor",feature.geometry() instanceof Point);
                labels.put(feature.getStringProperty(MPLFeaturesUtils.prop_featureid),feature);
            }
        });return labels;
    }
    private static Feature label(VectorStyleRenderingTest.Fixture f,VectorLayerUI layer,long id) throws Exception {
        AtomicReference<Feature> result=new AtomicReference<>();
        await(() -> {result.set(labels(f,layer).get(Long.toString(id)));return result.get()!=null;});
        return result.get();
    }
    private static void center(VectorStyleRenderingTest.Fixture f,double x,double y,float zoom) {
        f.screen.onActivity(a -> ((MapDrawable)f.app.getMap()).setZoomAndCenter(zoom,new GeoPoint(x,y),false,0));
    }
    private static void inside(VectorStyleRenderingTest.Fixture f,Feature label) {
        Point point=(Point)label.geometry();
        f.screen.onActivity(a -> {
            MapDrawable map=(MapDrawable)f.app.getMap();PointF position=map.getMaplibreMap().getProjection().toScreenLocation(
                    new org.maplibre.android.geometry.LatLng(point.latitude(),point.longitude()));
            assertTrue(position.x>=-1 && position.x<=map.getMaplibreMapView().getWidth()+1);
            assertTrue(position.y>=-1 && position.y<=map.getMaplibreMapView().getHeight()+1);
        });
    }
    private static double imageAxis(Bitmap image) {
        double x=0,y=0,xx=0,xy=0,yy=0,count=0;
        for(int row=0;row<image.getHeight();row++)for(int col=0;col<image.getWidth();col++) {
            int color=image.getPixel(col,row);
            if(Color.red(color)>150 && Color.green(color)<90 && Color.blue(color)<90) {
                count++;x+=col;y+=row;xx+=col*(double)col;xy+=col*(double)row;yy+=row*(double)row;
            }
        }
        assertTrue("The full Russian word must actually render",count>60);
        return Math.toDegrees(Math.atan2(2*(xy-x*y/count),xx-x*x/count-yy+y*y/count)/2);
    }
    private static void save(VectorStyleRenderingTest.Fixture f,Bitmap image,String name) throws Exception {
        File directory=new File(f.app.getFilesDir(),"line-label-qa");assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));
        }
    }

    @Test public void completelyWindingLineKeepsOneWholeRussianWordWithoutAnyLongStraightRun() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            background(f);VectorLayerUI layer=f.add(GeoConstants.GTLineString);
            GeoLineString curve=new GeoLineString();curve.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            for(int x=-400;x<=400;x+=10)curve.add(new GeoPoint(x,30*Math.sin(x/30.0)));
            long id=insert(layer,curve,"A","ПРОЕЗД СУЩЕСТВУЮЩИЙ");f.bind(layer,style(),null);
            Feature label=label(f,layer,id);inside(f,label);assertEquals(1,labels(f,layer).size());
            assertEquals("ПРОЕЗД СУЩЕСТВУЮЩИЙ",label.getStringProperty(MPLFeaturesUtils.prop_signature_text));
            f.screen.onActivity(a -> {assertEquals("point",f.labels(layer).getSymbolPlacement().getValue());
                assertEquals(0,f.labels(layer).getTextMaxWidth().getValue(),.001);});
            f.renderedFrame();Bitmap image=f.snapshot();save(f,image,"winding-whole-word");
            assertEquals(label.getNumberProperty(VisibleLineLabelLayout.ANGLE).doubleValue(),imageAxis(image),8);image.recycle();
            assertTrue("Original line picking survives the label-source change",f.picked(layer)>0);
        }
    }

    @Test public void panningAndZoomingUseTheVisibleDirectionRatherThanTheOffscreenWholeObject() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            background(f);VectorLayerUI layer=f.add(GeoConstants.GTLineString);
            long id=insert(layer,line(-2500,1500,1500,1500,1500,-2500),"A","ЛИНИЯ");
            center(f,1500,0,18);f.bind(layer,style(),null);
            await(() -> {Feature current=labels(f,layer).get(Long.toString(id));return current!=null
                    && Math.abs(Math.abs(current.getNumberProperty(VisibleLineLabelLayout.ANGLE).doubleValue())-90)<1;});
            Feature first=label(f,layer,id);inside(f,first);
            center(f,0,1500,18);
            await(() -> {Feature current=labels(f,layer).get(Long.toString(id));return current!=null
                    && Math.abs(current.getNumberProperty(VisibleLineLabelLayout.ANGLE).doubleValue())<1;});
            Feature second=label(f,layer,id);inside(f,second);
            assertNotEquals(((Point)first.geometry()).longitude(),((Point)second.geometry()).longitude(),.001);
            f.zoom(17);inside(f,label(f,layer,id));assertEquals(1,labels(f,layer).size());
            f.screen.onActivity(a -> {layer.setVisible(false);((MapDrawable)f.app.getMap()).checkLayerVisibility(layer.getId());});
            f.zoom(18);await(() -> labels(f,layer).isEmpty());
            f.screen.onActivity(a -> {layer.setVisible(true);((MapDrawable)f.app.getMap()).checkLayerVisibility(layer.getId());});
            inside(f,label(f,layer,id));assertEquals(1,labels(f,layer).size());
            f.renderedFrame();Bitmap image=f.snapshot();imageAxis(image);save(f,image,"visible-part-after-pan");image.recycle();
        }
    }

    @Test public void multipartCategoriesKeepOneLabelItsPaintAndAnUprightWholeWordAfterRotationAndReload() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            background(f);VectorLayerUI layer=f.add(GeoConstants.GTMultiLineString);
            GeoMultiLineString multi=new GeoMultiLineString();multi.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            multi.add(line(-300,-20,0,20,300,-20));multi.add(line(-200,40,200,40));
            long id=insert(layer,multi,"A","МНОГОСОСТАВНАЯ ЛИНИЯ");
            SimpleLineStyle other=style(),category=style();LabelAttributes.fromStyle(category).setTextFont("Open Sans Italic");
            FieldStyleRule rules=new FieldStyleRule(layer);rules.setKey("kind");rules.setOtherStyle(other);rules.setStyle("A",category);
            f.bind(layer,other,rules);label(f,layer,id);assertEquals(1,labels(f,layer).size());
            f.screen.onActivity(a -> {MapDrawable map=(MapDrawable)f.app.getMap();map.getMaplibreMap().moveCamera(
                    CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder(map.getMaplibreMap().getCameraPosition()).bearing(135).build()));});
            await(() -> {Feature current=labels(f,layer).get(Long.toString(id));if(current==null)return false;
                double relative=current.getNumberProperty(VisibleLineLabelLayout.UPRIGHT_ANGLE).doubleValue()-135;
                return relative>=-90 && relative<=90;});
            inside(f,label(f,layer,id));f.renderedFrame();Bitmap image=f.snapshot();
            save(f,image,"rotated-multipart-category");
            double expected=label(f,layer,id).getNumberProperty(VisibleLineLabelLayout.UPRIGHT_ANGLE).doubleValue()-135;
            assertEquals("The rotated label remains one straight block",expected,imageAxis(image),12);image.recycle();
            f.bind(layer,other,rules);assertEquals(1,labels(f,layer).size());
        }
    }
}

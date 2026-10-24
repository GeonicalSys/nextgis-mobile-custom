package com.nextgis.mobile.rendering;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimpleMarkerStyle;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.fragment.StyleFragment;
import com.nextgis.maplibui.mapui.VectorLayerUI;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Actual editor, scrolling and touches; configuration belongs only to this test window. */
@RunWith(AndroidJUnit4.class)
public class PolygonStyleEditorTest {
    public static class ConfiguredEditor extends StyleFragment {
        Configuration configuration;
        boolean dark;
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            // Retain the Activity's WindowManager token while overriding only this window's resources.
            mContext = new android.view.ContextThemeWrapper(requireActivity(),
                    dark ? R.style.SdfTheme_Dark : R.style.SdfTheme_Light);
            mContext.applyOverrideConfiguration(configuration);
        }
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
            return super.onCreateView(inflater.cloneInContext(mContext), container, state);
        }
    }

    private static void textFits(TextView text) {
        assertNotNull(text); assertTrue(text.isShown());
        android.text.Layout layout = text.getLayout(); assertNotNull(layout);
        assertTrue(text.getText() + " fits vertically", layout.getHeight() <= text.getHeight()
                - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
        assertEquals(text.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(text.getText() + " has no ellipsis", 0, layout.getEllipsisCount(line));
            assertTrue(text.getText() + " fits horizontally", layout.getLineMax(line) <= layout.getWidth() + 1);
        }
    }

    private static void reveal(VectorStyleRenderingTest.Fixture f, StyleFragment editor, int id) {
        f.screen.onActivity(activity -> {
            View view = editor.requireView().findViewById(id);
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void touch(VectorStyleRenderingTest.Fixture f, StyleFragment editor, int id) {
        NativeStyleTouch.tap(f, () -> editor.requireView().findViewById(id));
    }

    private static void saveEditorScreenshot(VectorStyleRenderingTest.Fixture f,String name) throws Exception {
        java.io.File folder=new java.io.File(f.app.getFilesDir(),"polygon-editor-qa");assertTrue(folder.isDirectory()||folder.mkdirs());
        java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
        f.screen.onActivity(activity->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(frame::countDown)));
        assertTrue(frame.await(3,java.util.concurrent.TimeUnit.SECONDS));
        android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));
        }finally{screenshot.recycle();}
    }

    private static android.graphics.Bitmap previewSnapshot(VectorStyleRenderingTest.Fixture f,com.nextgis.maplibui.util.StylePreviewView preview) throws Exception {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);AtomicReference<android.graphics.Bitmap> image=new AtomicReference<>();
        f.screen.onActivity(activity->preview.getPreviewMap().snapshot(bitmap->{image.set(bitmap);done.countDown();}));
        assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS));assertNotNull(image.get());return image.get();
    }
    private static int redPixels(android.graphics.Bitmap bitmap) {
        int count=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++) {
            int color=bitmap.getPixel(x,y);if(Color.red(color)>170&&Color.green(color)<110&&Color.blue(color)<110)count++;
        }return count;
    }
    private static android.graphics.Bitmap awaitPreviewPixels(VectorStyleRenderingTest.Fixture f,com.nextgis.maplibui.util.StylePreviewView preview,boolean red) throws Exception {
        long deadline=SystemClock.uptimeMillis()+15000;android.graphics.Bitmap bitmap;
        do {
            bitmap=previewSnapshot(f,preview);int count=redPixels(bitmap);
            if(red?count>30:count==0)return bitmap;
            bitmap.recycle();Thread.sleep(50);
        }while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Live native preview did not show expected red coverage="+red);
    }
    @Test public void livePreviewRendersPointsLinesAndPolygonsAndTracksRealOpacityTouchesBeforeSave() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            f.screen.onActivity(activity->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            AtomicBoolean portrait=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+15000;
            while(!portrait.get()&&SystemClock.uptimeMillis()<deadline){f.screen.onActivity(activity->portrait.set(activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT));if(!portrait.get())Thread.sleep(50);}
            assertTrue(portrait.get());
            for(int kind=0;kind<3;kind++) {
                final int index=kind;
                com.nextgis.maplib.display.Style source;
                if(kind==0)source=new SimpleMarkerStyle(Color.RED,Color.BLUE,18,SimpleMarkerStyle.MarkerStyleCircle);
                else if(kind==1){com.nextgis.maplib.display.SimpleLineStyle line=new com.nextgis.maplib.display.SimpleLineStyle();line.setType(com.nextgis.maplib.display.SimpleLineStyle.LineStyleDash);source=line;}
                else {
                    com.nextgis.maplib.display.SimplePolygonStyle polygon=new com.nextgis.maplib.display.SimplePolygonStyle(Color.YELLOW,Color.BLUE);
                    polygon.setFill(false);polygon.setFillPattern(1);polygon.setPatternOptions(new com.nextgis.maplib.display.PolygonPatternOptions());
                    polygon.getPatternOptions().setColor(Color.RED);polygon.getPatternOptions().setAlpha(255);source=polygon;
                }
                source.setColor(Color.RED);source.setAlpha(255);source.setOutAlpha(255);source.setWidth(8);
                String original=source.toJSON().toString();com.nextgis.maplib.display.Style edited=source.clone();
                int geometry=kind==0?GeoConstants.GTPoint:kind==1?GeoConstants.GTLineString:GeoConstants.GTPolygon;
                VectorLayerUI layer=f.add(geometry);ConfiguredEditor editor=new ConfiguredEditor();
                f.screen.onActivity(activity->{
                    Configuration config=new Configuration(activity.getResources().getConfiguration());config.setLocale(new Locale("ru"));config.fontScale=1;
                    editor.configuration=config;editor.dark=false;editor.setStyle(edited);editor.setLayer(layer);editor.setTitle("Живой образец");
                    editor.setPositiveText("Сохранить");editor.setNegativeText("Отменить");editor.setOnNegativeClickedListener(editor::dismiss);
                    editor.showNow(activity.getSupportFragmentManager(),"live-preview-"+index);
                    editor.requireDialog().getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    Rect usable=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(usable);
                    editor.requireDialog().getWindow().setLayout(Math.min(usable.width(),Math.round(360*editor.requireContext().getResources().getDisplayMetrics().density)),Math.min(usable.height(),Math.round(640*editor.requireContext().getResources().getDisplayMetrics().density)));
                });
                AtomicReference<com.nextgis.maplibui.util.StylePreviewView> preview=new AtomicReference<>();f.screen.onActivity(activity->preview.set(editor.requireView().findViewById(R.id.style_live_preview)));
                AtomicBoolean ready=new AtomicBoolean();deadline=SystemClock.uptimeMillis()+15000;
                while(!ready.get()&&SystemClock.uptimeMillis()<deadline){f.screen.onActivity(activity->ready.set(preview.get().getRevision()>0));if(!ready.get())Thread.sleep(30);}
                assertTrue(ready.get());android.graphics.Bitmap before=awaitPreviewPixels(f,preview.get(),true);
                java.io.File folder=new java.io.File(f.app.getFilesDir(),"polygon-editor-qa");assertTrue(folder.isDirectory()||folder.mkdirs());
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(folder,"live-"+kind+"-before.png"))){assertTrue(before.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}finally{before.recycle();}
                int opacity=kind==2?R.id.pattern_alpha:R.id.fill_alpha_seek;reveal(f,editor,opacity);
                NativeStyleTouch.minimum(f, () -> editor.requireView().findViewById(opacity));
                f.screen.onActivity(activity->{android.widget.SeekBar bar=editor.requireView().findViewById(opacity);assertTrue("Actual opacity touch reached the minimum",bar.getProgress()<=2);});
                android.graphics.Bitmap after=awaitPreviewPixels(f,preview.get(),false);
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(folder,"live-"+kind+"-after.png"))){assertTrue(after.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}finally{after.recycle();}
                touch(f,editor,R.id.button_negative);assertEquals("The working symbol was never mutated",original,source.toJSON().toString());
            }
        }
    }

    private static void touchRuleRow(VectorStyleRenderingTest.Fixture f,androidx.fragment.app.Fragment rules) {
        f.screen.onActivity(activity->{android.widget.ListView list=rules.requireView().findViewById(R.id.rules);assertEquals(1,list.getCount());});
        NativeStyleTouch.tap(f, () -> ((android.widget.ListView)rules.requireView().findViewById(R.id.rules)).getChildAt(0));
    }
    @Test public void actualCategoryEntryUsesInheritedPreviewAndCancelCannotMutateTheRule() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            f.screen.onActivity(activity->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            AtomicBoolean portrait=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+15000;
            while(!portrait.get()&&SystemClock.uptimeMillis()<deadline){f.screen.onActivity(activity->portrait.set(activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT));if(!portrait.get())Thread.sleep(50);}
            assertTrue(portrait.get());VectorLayerUI layer=f.add(GeoConstants.GTPolygon);f.insert(layer,0,"A");
            com.nextgis.maplib.display.SimplePolygonStyle other=new com.nextgis.maplib.display.SimplePolygonStyle(Color.YELLOW,Color.BLUE);
            other.setFill(false);other.setFillPattern(1);other.setPatternOptions(new com.nextgis.maplib.display.PolygonPatternOptions());
            other.getPatternOptions().setSpacing(20);other.getPatternOptions().setColor(Color.RED);other.getPatternOptions().setAlpha(255);
            other.setOutlineOptions(new com.nextgis.maplib.display.PolygonOutlineOptions());other.getOutlineOptions().setCasing(true);
            com.nextgis.maplib.display.SimplePolygonStyle original=new com.nextgis.maplib.display.SimplePolygonStyle(Color.YELLOW,Color.BLUE);
            original.setFill(false);original.setFillPattern(1);
            com.nextgis.maplib.display.FieldStyleRule rules=new com.nextgis.maplib.display.FieldStyleRule(layer);rules.setKey("kind");rules.setOtherStyle(other);rules.setStyle("A",original);
            String originalJson=original.toJSON().toString(),otherJson=other.toJSON().toString();
            com.nextgis.maplib.display.RuleFeatureRenderer renderer=new com.nextgis.maplib.display.RuleFeatureRenderer(layer,rules,other);
            com.nextgis.maplibui.display.RuleFeatureRendererUI controller=new com.nextgis.maplibui.display.RuleFeatureRendererUI(renderer,layer);
            f.screen.onActivity(activity->{layer.setRenderer(renderer);assertTrue(layer.save());});
            androidx.fragment.app.Fragment settings=controller.getSettingsScreen(layer);AtomicReference<android.widget.FrameLayout> host=new AtomicReference<>();
            try {
                f.screen.onActivity(activity->{
                    android.widget.FrameLayout frame=new android.widget.FrameLayout(activity);frame.setId(View.generateViewId());host.set(frame);
                    ((ViewGroup)activity.findViewById(android.R.id.content)).addView(frame,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
                    activity.getSupportFragmentManager().beginTransaction().add(frame.getId(),settings,"rule-entry-qa").commitNow();
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                for(boolean save:new boolean[]{false,true}) {
                    touchRuleRow(f,settings);AtomicReference<StyleFragment> modal=new AtomicReference<>();
                    f.screen.onActivity(activity->{
                        modal.set((StyleFragment)activity.getSupportFragmentManager().findFragmentByTag("STYLE_DIALOG_FRAGMENT"));assertNotNull(modal.get());
                        assertTrue(((android.widget.CheckBox)modal.get().requireView().findViewById(R.id.pattern_configure)).isChecked());
                        assertTrue(((android.widget.CheckBox)modal.get().requireView().findViewById(R.id.outline_configure)).isChecked());
                        android.widget.EditText spacing=modal.get().requireView().findViewById(R.id.pattern_spacing);
                        assertEquals("20",spacing.getText().toString());spacing.setText("24");
                        assertNull("The rule remains raw until Save",((com.nextgis.maplib.display.SimplePolygonStyle)rules.getStyle("A")).getPatternOptions());
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    touch(f,modal.get(),save?R.id.button_positive:R.id.button_negative);
                    assertEquals("The Other symbol never changes",otherJson,other.toJSON().toString());
                    if(save) {
                        assertTrue(layer.save());
                        assertEquals(24,((com.nextgis.maplib.display.SimplePolygonStyle)rules.getStyle("A")).getPatternOptions().getSpacing(),0);
                        assertNotSame(original,rules.getStyle("A"));
                        com.nextgis.maplib.display.FieldStyleRule restored=new com.nextgis.maplib.display.FieldStyleRule(layer);restored.fromJSON(rules.toJSON());
                        assertEquals(24,((com.nextgis.maplib.display.SimplePolygonStyle)restored.resolveEffectiveStyle("A",other)).getPatternOptions().getSpacing(),0);
                    }else assertEquals("Cancel from the actual category row leaves its JSON unchanged",originalJson,rules.getStyle("A").toJSON().toString());
                    assertEquals(originalJson,original.toJSON().toString());
                }
                NativeStyleTouch.tap(f, () -> ((android.widget.ListView)settings.requireView().findViewById(R.id.rules)).getChildAt(0).findViewById(R.id.rule_remove));
                assertNull("The separate remove action still removes only its category",rules.getStyle("A"));
                assertEquals(otherJson,other.toJSON().toString());
                f.screen.onActivity(activity->assertEquals(0,((android.widget.ListView)settings.requireView().findViewById(R.id.rules)).getCount()));
            }finally{
                f.screen.onActivity(activity->{
                    androidx.fragment.app.Fragment modal=activity.getSupportFragmentManager().findFragmentByTag("STYLE_DIALOG_FRAGMENT");
                    if(modal instanceof androidx.fragment.app.DialogFragment)((androidx.fragment.app.DialogFragment)modal).dismissNow();
                    if(settings.isAdded())activity.getSupportFragmentManager().beginTransaction().remove(settings).commitNow();
                    if(host.get()!=null)((ViewGroup)activity.findViewById(android.R.id.content)).removeView(host.get());
                });
            }
        }
    }

    @Test public void russianPolygonEditorKeepsLivePreviewAndControlsUsableAcrossLargeInterfaceMatrix() throws Exception {
        int[][] screens={{360,640,420},{640,360,420},{320,568,540},{568,320,540},{280,500,620},{500,280,620}};
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            VectorLayerUI layer=f.add(GeoConstants.GTPolygon);
            for(float font:new float[]{1,1.3f,2})for(int[] screen:screens)for(boolean dark:new boolean[]{false,true})for(boolean cancel:new boolean[]{false,true}) {
                int orientation=screen[0]>screen[1]?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
                f.screen.onActivity(activity->activity.setRequestedOrientation(orientation==Configuration.ORIENTATION_LANDSCAPE
                        ?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                AtomicBoolean rotated=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+15000;
                while(!rotated.get()&&SystemClock.uptimeMillis()<deadline){f.screen.onActivity(activity->rotated.set(activity.getResources().getConfiguration().orientation==orientation));if(!rotated.get())Thread.sleep(50);}
                assertTrue(rotated.get());
                com.nextgis.maplib.display.SimplePolygonStyle original=new com.nextgis.maplib.display.SimplePolygonStyle(Color.YELLOW,Color.BLUE);
                original.setFill(false);original.setFillPattern(1);
                com.nextgis.maplib.display.SimplePolygonStyle edited=original.clone();ConfiguredEditor editor=new ConfiguredEditor();AtomicBoolean saved=new AtomicBoolean();
                f.screen.onActivity(activity->{
                    Configuration config=new Configuration(activity.getResources().getConfiguration());config.setLocale(new Locale("ru"));config.fontScale=font;config.densityDpi=screen[2];
                    config.screenWidthDp=screen[0];config.screenHeightDp=screen[1];config.smallestScreenWidthDp=Math.min(screen[0],screen[1]);config.orientation=orientation;
                    config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
                    editor.configuration=config;editor.dark=dark;editor.setStyle(edited);editor.setLayer(layer);editor.setTitle("Стиль полигона");
                    editor.setPositiveText("Сохранить");editor.setNegativeText("Отменить");
                    editor.setOnPositiveClickedListener(()->{layer.setRenderer(new SimpleFeatureRenderer(layer,edited));assertTrue(layer.save());saved.set(true);editor.dismiss();});
                    editor.setOnNegativeClickedListener(editor::dismiss);editor.showNow(activity.getSupportFragmentManager(),"polygon-ui");
                    editor.requireDialog().getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    ((android.view.inputmethod.InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.requireView().getWindowToken(),0);
                    Rect usable=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(usable);float density=screen[2]/160f;
                    editor.requireDialog().getWindow().setLayout(Math.min(usable.width(),Math.round(screen[0]*density)),Math.min(usable.height(),Math.round(screen[1]*density)));
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                AtomicReference<com.nextgis.maplibui.util.StylePreviewView> preview=new AtomicReference<>();
                f.screen.onActivity(activity->preview.set(editor.requireView().findViewById(R.id.style_live_preview)));
                AtomicBoolean ready=new AtomicBoolean();deadline=SystemClock.uptimeMillis()+15000;
                while(!ready.get()&&SystemClock.uptimeMillis()<deadline){f.screen.onActivity(activity->ready.set(preview.get().getRevision()>0));if(!ready.get())Thread.sleep(30);}
                assertTrue("Native preview is ready",ready.get());int before=preview.get().getRevision();
                System.out.println("Polygon editor profile font="+font+" screen="+screen[0]+"x"+screen[1]+" dark="+dark+" cancel="+cancel);
                reveal(f,editor,R.id.pattern_configure);
                if(font==1 && screen==screens[0] && !dark && !cancel) saveEditorScreenshot(f,"before-configure");
                f.screen.onActivity(activity->{
                    android.widget.CheckBox check=editor.requireView().findViewById(R.id.pattern_configure);
                    assertTrue("Pattern checkbox is interactive",check.isClickable());assertNotNull(check.getButtonDrawable());
                });
                touch(f,editor,R.id.pattern_configure);
                f.screen.onActivity(activity->{
                    android.widget.CheckBox check=editor.requireView().findViewById(R.id.pattern_configure);
                    assertTrue("Real pattern touch: enabled="+check.isEnabled()+" checked="+check.isChecked()+" saved="+saved.get()+" showing="+editor.requireDialog().isShowing(),check.isChecked());
                    assertNotNull("Pattern options after actual touch",edited.getPatternOptions());
                });
                reveal(f,editor,R.id.outline_configure);touch(f,editor,R.id.outline_configure);
                f.screen.onActivity(activity->{
                    android.widget.CheckBox check=editor.requireView().findViewById(R.id.outline_configure);
                    assertTrue("Real contour touch: enabled="+check.isEnabled()+" checked="+check.isChecked(),check.isChecked());
                    assertNotNull("Contour options after actual touch",edited.getOutlineOptions());
                });
                f.screen.onActivity(activity->{
                    assertNotNull(edited.getPatternOptions());assertNotNull(edited.getOutlineOptions());
                    ((android.widget.EditText)editor.requireView().findViewById(R.id.pattern_angle)).setText("27");
                    ((android.widget.EditText)editor.requireView().findViewById(R.id.pattern_spacing)).setText("16");
                    ((Spinner)editor.requireView().findViewById(R.id.outline_type)).setSelection(1);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                reveal(f,editor,R.id.outline_casing);touch(f,editor,R.id.outline_casing);
                reveal(f,editor,R.id.outline_casing_color);
                f.screen.onActivity(activity->{
                    assertTrue("The native sample updates before Save",preview.get().getRevision()>before);
                    for(int id:new int[]{R.id.pattern_configure,R.id.pattern_angle_label,R.id.pattern_spacing_label,R.id.pattern_width_label,
                            R.id.pattern_color_label,R.id.pattern_color,R.id.pattern_alpha_label,R.id.outline_configure,R.id.outline_type_label,R.id.outline_casing,
                            R.id.outline_casing_width_label,R.id.outline_casing_color_label,R.id.outline_casing_color,R.id.button_positive,R.id.button_negative})textFits(editor.requireView().findViewById(id));
                    assertTrue(edited.getOutlineOptions().isCasing());
                    TextView colourLabel=editor.requireView().findViewById(R.id.outline_casing_color_label);
                    double contrast=androidx.core.graphics.ColorUtils.calculateContrast(colourLabel.getCurrentTextColor()|0xff000000,
                            dark?Color.rgb(68,68,68):Color.rgb(240,240,240));
                    assertTrue("Caption contrast in the actual dialog theme: "+contrast,contrast>=4.5);
                    View colourAction=editor.requireView().findViewById(R.id.outline_casing_color);Rect visible=new Rect();
                    assertTrue(colourAction.getGlobalVisibleRect(visible));assertEquals("The last colour action remains fully reachable",colourAction.getHeight(),visible.height());
                    Rect bounds=new Rect();assertTrue(preview.get().getGlobalVisibleRect(bounds));assertEquals(preview.get().getHeight(),bounds.height());
                });
                if(!cancel && (font==1 && screen==screens[0] && !dark || font==2 && (screen==screens[4] || screen==screens[5])))
                    saveEditorScreenshot(f,"controls-"+font+"-"+screen[0]+"-"+dark);
                touch(f,editor,cancel?R.id.button_negative:R.id.button_positive);assertEquals(!cancel,saved.get());
                assertNull("Cancel/editing never touches the original",original.getPatternOptions());assertNull(original.getOutlineOptions());
                if(!cancel){com.nextgis.maplib.display.SimplePolygonStyle restored=new com.nextgis.maplib.display.SimplePolygonStyle();restored.fromJSON(edited.toJSON());assertEquals(27,restored.getPatternOptions().getAngle(),0);assertTrue(restored.getOutlineOptions().isCasing());}
            }
        }
    }
}

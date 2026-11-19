package com.nextgis.mobile.rendering;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.ScriptNoticeDialogs;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.ProjectScriptFormController;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Real platform dialog and physical touches; only this window receives font/density overrides. */
@RunWith(AndroidJUnit4.class)
public class ScriptNoticeDialogTest {
    private GISApplication app;
    private CollectorProjectRegistry.ProjectInfo original,owned;
    @Before public void enterOwnedWorkspace() {
        app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);original=CollectorProjectRegistry.getActiveProject(app);assertNotNull(original);
        owned=CollectorProjectRegistry.createLocalProject(app,"Script notice "+java.util.UUID.randomUUID());assertNotNull(owned);
        assertTrue(CollectorProjectRegistry.activateProject(app,owned.getProjectUid()));
    }
    @After public void restoreOriginalWorkspace() {
        if(owned==null)return;
        assertNotEquals(original.getProjectUid(),owned.getProjectUid());
        assertTrue(CollectorProjectRegistry.activateProject(app,owned.getProjectUid()));
        assertTrue(CollectorProjectRegistry.deleteActiveProject(app,owned.getProjectUid(),"Local",()->true).isSuccess());
        assertTrue(CollectorProjectRegistry.activateProject(app,original.getProjectUid()));
    }
    private static void await(String message,BooleanSupplier check) throws Exception {
        long deadline=SystemClock.uptimeMillis()+20000;
        while(!check.getAsBoolean()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(40);
        assertTrue(message,check.getAsBoolean());
    }
    private static ScrollView scroll(TextView text) {
        for(ViewParent parent=text.getParent();parent instanceof View;parent=parent.getParent())
            if(parent instanceof ScrollView)return (ScrollView)parent;
        throw new AssertionError("Platform message must own a bounded scroll viewport");
    }
    private static Rect bounds(View view) {
        int[] xy=new int[2];view.getLocationOnScreen(xy);return new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());
    }
    private static void complete(View view) {
        assertNotNull(view);assertTrue(view.isShown());Rect visible=new Rect();assertTrue(view.getGlobalVisibleRect(visible));
        assertTrue(view.getLocalVisibleRect(visible));
        String label=view.getClass().getSimpleName()+" id="+view.getId()+(view instanceof TextView?" text="+((TextView)view).getText():"");
        assertEquals("Complete notice view cannot be clipped: "+label,new Rect(0,0,view.getWidth(),view.getHeight()),visible);
        Rect usable=new Rect();view.getWindowVisibleDisplayFrame(usable);assertTrue("Action/title must remain in physical usable window",usable.contains(bounds(view)));
        for(View current=view;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null){
            assertEquals("Script text must not inherit a scale transform",1f,current.getScaleX(),.001f);
            assertEquals("Script text must not inherit a scale transform",1f,current.getScaleY(),.001f);
        }
    }
    private static TextView title(View view,String text) {
        if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            TextView found=title(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    private static String longMessage() {
        StringBuilder text=new StringBuilder();
        for(int notice=0;notice<32;notice++){
            if(notice>0)text.append("\n\n");StringBuilder line=new StringBuilder("Предупреждение "+notice+". Подрядчик 🌲. ");
            if(notice==0)for(int i=0;i<200;i++)line.append('Д');
            while(line.length()<1000)line.append("История аудита доступна для чтения. ");
            String tail="\nКонец сообщения "+notice+(notice==31?"\nПОСЛЕДНЯЯ СТРОКА 32":"");
            line.setLength(1000-tail.length());line.append(tail);
            assertEquals(1000,line.length());text.append(line);
        }
        assertEquals(32062,text.length());assertEquals(-1,text.indexOf("\u0000"));return text.toString();
    }
    private static void screenshot(VectorStyleRenderingTest.Fixture f,String name) throws Exception {
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        String run=InstrumentationRegistry.getArguments().getString("nightRun","");
        if(!run.matches("[a-f0-9]{32}"))run=java.util.UUID.randomUUID().toString().replace("-","");
        try(FileOutputStream out=new FileOutputStream(new File(f.app.getFilesDir(),"verification-script-notice-"+run+"-"+name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        finally{bitmap.recycle();}
    }
    @Test public void shortAndMaximumMessagesKeepReadableTextAndFixedActionsAcrossLocalWindowProfiles() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()){
            String maximum=longMessage();int profiles=0;
            for(boolean landscape:new boolean[]{false,true}){
                int orientation=landscape?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
                f.screen.onActivity(a->a.setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                await("Requested Activity orientation",()->{AtomicReference<Boolean> yes=new AtomicReference<>(false);f.screen.onActivity(a->yes.set(a.getResources().getConfiguration().orientation==orientation));return yes.get();});
                for(float font:new float[]{1f,2f})for(int width:new int[]{360,320,280})for(boolean dark:new boolean[]{false,true}){
                    int density=width==280?620:width==320?540:420;
                    AtomicReference<Context> context=new AtomicReference<>();AtomicReference<AlertDialog> current=new AtomicReference<>();AtomicInteger actions=new AtomicInteger();
                    f.screen.onActivity(a->{
                        Configuration c=new Configuration(a.getResources().getConfiguration());c.fontScale=font;c.densityDpi=density;
                        c.screenWidthDp=landscape?500:width;c.screenHeightDp=landscape?width:640;c.smallestScreenWidthDp=width;c.orientation=orientation;c.setLocale(new Locale("ru"));
                        c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
                        ContextThemeWrapper themed=new ContextThemeWrapper(a,dark?R.style.SdfTheme_Dark:R.style.SdfTheme_Light);themed.applyOverrideConfiguration(c);context.set(themed);
                        AlertDialog dialog=ScriptNoticeDialogs.builder(themed).setTitle(R.string.project_scripts_notice_title).setMessage("Короткое сообщение")
                            .setPositiveButton(R.string.project_scripts_continue,(d,w)->actions.incrementAndGet())
                            .setNegativeButton(R.string.project_scripts_return,(d,w)->actions.addAndGet(10)).create();dialog.show();
                        dialog.getWindow().setLayout(Math.round((landscape?500:width)*themed.getResources().getDisplayMetrics().density),ViewGroup.LayoutParams.WRAP_CONTENT);current.set(dialog);
                    });
                    try{
                        await("Short dialog owns focused laid-out window",()->{AtomicReference<Boolean> ready=new AtomicReference<>(false);f.screen.onActivity(a->ready.set(current.get().getWindow().getDecorView().hasWindowFocus()&&current.get().findViewById(android.R.id.message).getHeight()>0));return ready.get();});
                        AtomicReference<Float> shortSize=new AtomicReference<>();
                        f.screen.onActivity(a->{TextView text=current.get().findViewById(android.R.id.message);shortSize.set(text.getTextSize());complete(text);complete(title(current.get().getWindow().getDecorView(),context.get().getString(R.string.project_scripts_notice_title)));complete(current.get().getButton(AlertDialog.BUTTON_POSITIVE));complete(current.get().getButton(AlertDialog.BUTTON_NEGATIVE));
                            ScrollView viewport=scroll(text);assertFalse(viewport.canScrollVertically(1));
                            assertTrue("Short message must not reserve an empty full-height viewport",viewport.getHeight()<=text.getHeight()+viewport.getPaddingTop()+viewport.getPaddingBottom()+2);
                            current.get().dismiss();});
                        f.screen.onActivity(a->{AlertDialog dialog=ScriptNoticeDialogs.builder(context.get()).setTitle(R.string.project_scripts_notice_title).setMessage(maximum)
                            .setPositiveButton(R.string.project_scripts_continue,(d,w)->actions.incrementAndGet())
                            .setNegativeButton(R.string.project_scripts_return,(d,w)->actions.addAndGet(10)).create();dialog.show();
                            dialog.getWindow().setLayout(Math.round((landscape?500:width)*context.get().getResources().getDisplayMetrics().density),ViewGroup.LayoutParams.WRAP_CONTENT);current.set(dialog);});
                        await("Long dialog owns focused laid-out window",()->{AtomicReference<Boolean> ready=new AtomicReference<>(false);f.screen.onActivity(a->ready.set(current.get().getWindow().getDecorView().hasWindowFocus()&&current.get().findViewById(android.R.id.message).getHeight()>0));return ready.get();});
                        AtomicReference<Rect> positive=new AtomicReference<>(),negative=new AtomicReference<>();
                        f.screen.onActivity(a->{TextView text=current.get().findViewById(android.R.id.message);assertEquals("Message length cannot shrink text",shortSize.get(),text.getTextSize(),.01f);
                            assertEquals(maximum,text.getText().toString());ScrollView viewport=scroll(text);assertTrue(viewport.getHeight()>0);assertTrue(viewport.canScrollVertically(1));assertTrue(viewport.isVerticalScrollBarEnabled());assertFalse(viewport.isScrollbarFadingEnabled());
                            complete(title(current.get().getWindow().getDecorView(),context.get().getString(R.string.project_scripts_notice_title)));complete(current.get().getButton(AlertDialog.BUTTON_POSITIVE));complete(current.get().getButton(AlertDialog.BUTTON_NEGATIVE));
                            positive.set(bounds(current.get().getButton(AlertDialog.BUTTON_POSITIVE)));negative.set(bounds(current.get().getButton(AlertDialog.BUTTON_NEGATIVE)));viewport.fullScroll(View.FOCUS_DOWN);
                        });
                        await("Every accepted line is reachable",()->{AtomicReference<Boolean> bottom=new AtomicReference<>(false);f.screen.onActivity(a->bottom.set(!scroll(current.get().findViewById(android.R.id.message)).canScrollVertically(1)));return bottom.get();});
                        f.screen.onActivity(a->{TextView text=current.get().findViewById(android.R.id.message);ScrollView viewport=scroll(text);assertTrue(viewport.getScrollY()>0);
                            assertEquals(positive.get(),bounds(current.get().getButton(AlertDialog.BUTTON_POSITIVE)));assertEquals(negative.get(),bounds(current.get().getButton(AlertDialog.BUTTON_NEGATIVE)));
                            int[] xy=new int[2];text.getLocationOnScreen(xy);int line=text.getLayout().getLineForOffset(maximum.lastIndexOf("ПОСЛЕДНЯЯ"));
                            // getLocalVisibleRect of a ScrollView includes its own scroll offset.
                            // Convert the clipped root-coordinate rectangle to physical screen coordinates.
                            Rect visible=new Rect();assertTrue(viewport.getGlobalVisibleRect(visible));int[] root=new int[2];viewport.getRootView().getLocationOnScreen(root);visible.offset(root[0],root[1]);
                            Rect usable=new Rect();viewport.getWindowVisibleDisplayFrame(usable);assertTrue(visible.intersect(usable));
                            int origin=xy[1]+text.getTotalPaddingTop()-text.getScrollY();
                            int top=origin+text.getLayout().getLineTop(line),bottom=origin+text.getLayout().getLineBottom(line);
                            assertTrue("Last line must be physically visible: line="+top+".."+bottom+" viewport="+visible,top>=visible.top-1&&bottom<=visible.bottom+1);
                            for(int i=0;i<text.getLayout().getLineCount();i++){
                                assertEquals(0,text.getLayout().getEllipsisCount(i));
                                // getLineWidth includes trailing spaces, which may extend beyond the
                                // break without drawing text. getLineMax excludes those spaces.
                                float extent=text.getLayout().getLineMax(i);int available=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight();
                                int first=text.getLayout().getLineStart(i),end=text.getLayout().getLineEnd(i);
                                int visibleEnd=text.getLayout().getLineVisibleEnd(i),inkEnd=visibleEnd;
                                while(inkEnd>first&&(Character.isWhitespace(text.getText().charAt(inkEnd-1))||Character.isSpaceChar(text.getText().charAt(inkEnd-1))))inkEnd--;
                                // Android's getLineVisibleEnd returns immediately at a hard newline,
                                // retaining preceding spaces. Their advance is not clipped visible ink.
                                if(inkEnd<visibleEnd) {
                                    Rect ink=new Rect();text.getPaint().getTextBounds(text.getText().toString(),first,inkEnd,ink);
                                    extent=text.getLayout().getLineLeft(i)+ink.right;
                                }
                                String fragment=text.getText().subSequence(first,Math.min(end,first+90)).toString().replace('\n',' ');
                                assertTrue("Visible line "+i+" must wrap: extent="+extent+" available="+available+" layout="+text.getLayout().getWidth()+" text="+fragment,extent<=available+1);
                            }
                            android.view.accessibility.AccessibilityNodeInfo scrollNode=viewport.createAccessibilityNodeInfo();
                            assertTrue(scrollNode.isScrollable());scrollNode.recycle();
                            android.view.accessibility.AccessibilityNodeInfo action=current.get().getButton(AlertDialog.BUTTON_POSITIVE).createAccessibilityNodeInfo();
                            assertTrue(action.isVisibleToUser());assertTrue(action.isClickable());assertNotNull(action.getText());action.recycle();
                        });
                        if(font==2f&&width==280)screenshot(f,(landscape?"landscape":"portrait")+"-"+(dark?"dark":"light"));
                        NativeStyleTouch.tap(f,()->current.get().getButton(AlertDialog.BUTTON_POSITIVE));assertEquals(1,actions.get());assertFalse(current.get().isShowing());profiles++;
                    }catch(Exception|AssertionError failure){
                        try{screenshot(f,"failure-"+(landscape?"landscape":"portrait")+"-"+(dark?"dark":"light")+"-"+width+"-"+font);}catch(Exception diagnostic){failure.addSuppressed(diagnostic);}
                        throw new AssertionError("Script notice profile "+(landscape?"landscape":"portrait")+" / "+(dark?"dark":"light")+" / width="+width+" / font="+font+": "+failure.getMessage(),failure);
                    }finally{f.screen.onActivity(a->{if(current.get()!=null)current.get().dismiss();});}
                }
            }
            assertEquals(24,profiles);
        }
    }
    @Test public void noticeActionsRemainReachableAfterTheRealKeyboardWasOpened() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            AtomicReference<android.widget.EditText> editor=new AtomicReference<>();AtomicReference<AlertDialog> dialog=new AtomicReference<>();
            AtomicReference<android.view.inputmethod.InputMethodManager> keyboard=new AtomicReference<>();AtomicInteger answer=new AtomicInteger();
            AtomicInteger previousMode=new AtomicInteger();String original="Синтетический черновик";
            f.screen.onActivity(a->{
                previousMode.set(a.getWindow().getAttributes().softInputMode);
                a.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                android.widget.EditText field=new android.widget.EditText(a);field.setText(original);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
                android.widget.FrameLayout.LayoutParams params=new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,android.view.Gravity.BOTTOM);
                ((ViewGroup)a.findViewById(android.R.id.content)).addView(field,params);editor.set(field);field.requestFocus();
                keyboard.set((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE));
                field.post(()->keyboard.get().showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_FORCED));
            });
            try {
                await("Real keyboard must be visible before the notice",()->{
                    AtomicReference<Boolean> visible=new AtomicReference<>(false);f.screen.onActivity(a->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(editor.get());visible.set(insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));});return visible.get();
                });
                f.screen.onActivity(a->{AlertDialog notice=ScriptNoticeDialogs.builder(a).setTitle(R.string.project_scripts_notice_title).setMessage(longMessage())
                    .setPositiveButton(R.string.project_scripts_continue,(d,w)->answer.set(1)).setNegativeButton(R.string.project_scripts_return,(d,w)->answer.set(2)).create();notice.show();dialog.set(notice);});
                await("Notice must receive focus and layout",()->dialog.get().getWindow().getDecorView().hasWindowFocus()&&dialog.get().getButton(AlertDialog.BUTTON_POSITIVE).getHeight()>0);
                f.screen.onActivity(a->{complete(dialog.get().getButton(AlertDialog.BUTTON_POSITIVE));complete(dialog.get().getButton(AlertDialog.BUTTON_NEGATIVE));assertEquals(original,editor.get().getText().toString());assertEquals(0,answer.get());});
                NativeStyleTouch.tap(f,()->dialog.get().getButton(AlertDialog.BUTTON_NEGATIVE));assertEquals(2,answer.get());
                f.screen.onActivity(a->assertEquals(original,editor.get().getText().toString()));
            } finally {
                f.screen.onActivity(a->{if(dialog.get()!=null)dialog.get().dismiss();if(editor.get()!=null){keyboard.get().hideSoftInputFromWindow(editor.get().getWindowToken(),0);((ViewGroup)editor.get().getParent()).removeView(editor.get());}a.getWindow().setSoftInputMode(previousMode.get());});
            }
        }
    }
    @Test public void controllerKeepsWarningAcknowledgementReturnBackBlockAndCloseDecisions() throws Exception {
        try(VectorStyleRenderingTest.Fixture f=new VectorStyleRenderingTest.Fixture()) {
            AtomicReference<ProjectScriptFormController> controller=new AtomicReference<>();
            AtomicReference<AlertDialog> dialog=new AtomicReference<>();AtomicReference<Boolean> answer=new AtomicReference<>();
            Class<?> message=Class.forName(ProjectScriptFormController.class.getName()+"$Message");
            java.lang.reflect.Constructor<?> constructor=message.getDeclaredConstructor(String.class,String.class,boolean.class);constructor.setAccessible(true);
            java.lang.reflect.Method show=ProjectScriptFormController.class.getDeclaredMethod("show",java.util.List.class,boolean.class,java.util.function.Consumer.class);show.setAccessible(true);
            java.lang.reflect.Field current=ProjectScriptFormController.class.getDeclaredField("dialog");current.setAccessible(true);
            f.screen.onActivity(a->controller.set(new ProjectScriptFormController(a,null,java.util.Collections.emptyMap(),android.content.ContentValues::new,()->-1,()->false,"")));
            class Notices {
                void open(String key,boolean blocking,boolean save) throws Exception {
                    answer.set(null);java.util.List<Object> messages=new java.util.ArrayList<>();
                    messages.add(constructor.newInstance(key,"Первая строка\nВторая строка",false));
                    if(blocking)messages.add(constructor.newInstance("block","Сохранение запрещено",true));
                    f.screen.onActivity(a->{try{show.invoke(controller.get(),messages,save,(java.util.function.Consumer<Boolean>)answer::set);dialog.set((AlertDialog)current.get(controller.get()));}catch(Exception failure){throw new AssertionError(failure);}});
                }
                void ready() throws Exception {await("Controller notice must own the window",()->dialog.get()!=null&&dialog.get().isShowing()&&dialog.get().getWindow().getDecorView().hasWindowFocus());}
            }
            Notices notices=new Notices();
            try {
                notices.open("warn",false,true);notices.ready();assertNull("Reading does not acknowledge",answer.get());
                NativeStyleTouch.tap(f,()->dialog.get().getButton(AlertDialog.BUTTON_NEGATIVE));await("Return answers false",()->answer.get()!=null);assertFalse(answer.get());
                notices.open("warn",false,true);notices.ready();
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
                await("Back answers false",()->answer.get()!=null);assertFalse(answer.get());
                notices.open("warn",false,true);notices.ready();NativeStyleTouch.tap(f,()->dialog.get().getButton(AlertDialog.BUTTON_POSITIVE));
                await("Continue answers true",()->answer.get()!=null);assertTrue(answer.get());
                notices.open("warn",false,true);assertEquals(Boolean.TRUE,answer.get());assertFalse(dialog.get().isShowing());
                notices.open("different-history",false,false);notices.ready();
                assertEquals(f.app.getString(android.R.string.ok),dialog.get().getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
                NativeStyleTouch.tap(f,()->dialog.get().getButton(AlertDialog.BUTTON_POSITIVE));await("OK acknowledges warning",()->answer.get()!=null);assertTrue(answer.get());
                notices.open("mixed-warning",true,true);notices.ready();assertNull(answer.get());
                assertEquals(f.app.getString(android.R.string.ok),dialog.get().getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
                NativeStyleTouch.tap(f,()->dialog.get().getButton(AlertDialog.BUTTON_POSITIVE));await("Block answers false",()->answer.get()!=null);assertFalse(answer.get());
                notices.open("mixed-warning",true,true);notices.ready();
                f.screen.onActivity(a->controller.get().close());assertFalse("Closing form must dismiss its notice",dialog.get().isShowing());
            } finally {f.screen.onActivity(a->controller.get().close());}
        }
    }
}

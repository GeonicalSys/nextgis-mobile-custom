package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplibui.util.NGIDUtils;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.Assert.*;

/** Real HTTP, private preferences and normal local export; fake identities, loopback endpoints only. */
@RunWith(AndroidJUnit4.class)
public class NGIDLoginDiagnosticsNativeTest {
    static final Pattern ATTEMPT=Pattern.compile("NGID_LOGIN attempt=([0-9a-f-]{36}) stage=attempt request=0 event=start");
    static final String[] KEYS={"ngid_url","access_token","refresh_token","username","email","first_name","last_name","ngwaccount","tracker_hub_url","collector_hub_url"};
    static class Login implements AutoCloseable {
        final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(app);
        final Map<String,Object> before=new HashMap<>();final Set<String> existing=new HashSet<>();
        final String oldNgid=NGWUtil.NGID;
        final String nonce=UUID.randomUUID().toString(),login="synthetic-"+nonce+"@example.invalid",password="MiXeD secret "+nonce;
        final String access="access-secret-"+nonce,refresh="refresh-secret-"+nonce;
        Login(String base){
            VerificationEnvironment.requireIsolated(app);
            Map<String,?> all=prefs.getAll();for(String key:KEYS)if(all.containsKey(key)){Object value=all.get(key);before.put(key,value instanceof Set?new HashSet<>((Set<?>)value):value);}
            for(String line:HyperLog.getDeviceLogsAsStringList(false)){Matcher m=ATTEMPT.matcher(line);while(m.find())existing.add(m.group(1));}
            assertTrue(prefs.edit().putString("ngid_url",base).putStringSet("ngwaccount",new HashSet<>()).remove("access_token").remove("refresh_token").commit());
        }
        HttpResponse run(long timeoutMillis) throws Exception {
            CompletableFuture<HttpResponse> response=new CompletableFuture<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->NGIDUtils.getToken(app,login,password,response::complete));
            return response.get(timeoutMillis,TimeUnit.MILLISECONDS);
        }
        List<String> exported(String expected) throws Exception {
            long until=android.os.SystemClock.elapsedRealtime()+15000;List<String> found=new ArrayList<>();String id=null;
            do{
                // The normal export drains persisted rows. Wait without consuming them so a
                // partially flushed asynchronous log batch cannot lose its start or result.
                String text=String.join("\n",HyperLog.getDeviceLogsAsStringList(false));
                Matcher starts=ATTEMPT.matcher(text);Set<String> fresh=new HashSet<>();while(starts.find())if(!existing.contains(starts.group(1)))fresh.add(starts.group(1));
                if(fresh.size()==1){id=fresh.iterator().next();found.clear();for(String line:text.split("\\R"))if(line.contains("NGID_LOGIN attempt="+id+" "))found.add(line);}
                if(found.stream().anyMatch(line->line.contains("stage=attempt request=0 event=result")&&line.contains("category="+expected)))break;
                Thread.sleep(100);
            }while(android.os.SystemClock.elapsedRealtime()<until);
            assertNotNull("Exactly one new attempt ID",id);
            assertEquals("Expected terminal category before the one ordinary export: "+found,1,found.stream().filter(x->x.contains("stage=attempt request=0 event=result")&&x.contains("category="+expected)).count());
            File file=HyperLog.getDeviceLogsInFile(app);assertNotNull("Normal log export exists",file);assertTrue(file.isFile());assertTrue(file.length()<=16*1024*1024);
            String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            for(String secret:new String[]{login,password,access,refresh})assertFalse("Seeded login/credential cannot appear in ordinary export",text.contains(secret));
            found.clear();for(String line:text.split("\\R"))if(line.contains("NGID_LOGIN attempt="+id+" "))found.add(line);
            assertEquals("One exported start: "+found,1,found.stream().filter(x->x.contains("stage=attempt request=0 event=start")).count());
            assertEquals("One exported terminal result: "+found,1,found.stream().filter(x->x.contains("stage=attempt request=0 event=result")&&x.contains("category="+expected)).count());
            Map<String,Integer> stageStarts=new HashMap<>(),stageResults=new HashMap<>();
            Pattern request=Pattern.compile("stage=(token|profile|settings|support) request=(\\d+) event=(start|result)");
            for(String line:found){assertTrue(line.contains("host=127.0.0.1")||line.contains("host=ngid-night.invalid"));Matcher m=request.matcher(line);if(m.find())("start".equals(m.group(3))?stageStarts:stageResults).merge(m.group(1)+":"+m.group(2),1,Integer::sum);}
            assertEquals("Every sent stage has one paired result",stageStarts,stageResults);assertFalse(stageStarts.isEmpty());
            for(int count:stageStarts.values())assertEquals("A sent stage is not duplicated",1,count);
            return found;
        }
        @Override public void close(){
            SharedPreferences.Editor editor=prefs.edit();for(String key:KEYS){editor.remove(key);if(before.containsKey(key)){Object value=before.get(key);if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);else editor.putString(key,(String)value);}}
            assertTrue(editor.commit());NGWUtil.NGID=oldNgid;
        }
    }
    static class Endpoint implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        final Set<Socket> sockets=ConcurrentHashMap.newKeySet();final CountDownLatch stop=new CountDownLatch(1);final Thread thread;
        volatile Throwable error;volatile int tokenStatus=200;volatile boolean badToken,badProfile,tlsFailure,stall;
        volatile String access="fake-access",refresh="fake-refresh";
        Endpoint() throws Exception {
            thread=new Thread(()->{
                while(!server.isClosed())try(Socket socket=server.accept()){
                    sockets.add(socket);socket.setSoTimeout(15000);
                    if(tlsFailure){socket.getOutputStream().write("HTTP/1.1 400 Test\\r\\nContent-Length: 0\\r\\nConnection: close\\r\\n\\r\\n".replace("\\r","\r").replace("\\n","\n").getBytes(StandardCharsets.US_ASCII));continue;}
                    InputStream input=socket.getInputStream();String request=line(input);assertNotNull(request);int size=0;String header;
                    while((header=line(input))!=null&&!header.isEmpty())if(header.toLowerCase(Locale.ROOT).startsWith("content-length:"))size=Integer.parseInt(header.substring(header.indexOf(':')+1).trim());
                    assertTrue(size>=0&&size<=1024*1024);for(int i=0;i<size;i++)assertTrue(input.read()>=0);
                    if(stall){stop.await(NetworkUtil.TIMEOUT_SOCKET+30000L,TimeUnit.MILLISECONDS);continue;}
                    boolean token=request.contains("/oauth2/token/");int status=token?tokenStatus:200;
                    String body=token?(badToken?"[":"{\"token_type\":\"Bearer\",\"access_token\":\""+access+"\",\"refresh_token\":\""+refresh+"\"}")
                        :request.contains("/user_info/")?(badProfile?"{":"{\"username\":\"synthetic-user\",\"email\":\"synthetic@example.invalid\",\"first_name\":\"Synthetic\",\"last_name\":\"User\",\"available_webgises\":[]}"):"{}";
                    if(status!=200)body="{\"error\":\"synthetic-only\"}";
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    String head="HTTP/1.1 "+status+" Test\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(bytes);socket.getOutputStream().flush();sockets.remove(socket);
                }catch(SocketException closed){if(!server.isClosed())error=closed;}catch(Throwable failure){if(!server.isClosed())error=failure;}
            },"ngid-diagnostic-loopback");thread.start();
        }
        static String line(InputStream in) throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')return out.toString("US-ASCII").replaceFirst("\\r$","");if(out.size()>8192)throw new IOException("Oversized synthetic request");out.write(b);}return null;}
        String url(){return (tlsFailure?"https":"http")+"://127.0.0.1:"+server.getLocalPort();}
        @Override public void close() throws Exception {stop.countDown();server.close();for(Socket socket:sockets)socket.close();thread.join(10000);assertFalse(thread.isAlive());assertNull(error);}
    }
    @Test public void successfulAttemptExportsCorrelatedTokenAndProfileWithoutSecrets() throws Exception {
        try(Endpoint endpoint=new Endpoint();Login login=new Login(endpoint.url())){endpoint.access=login.access;endpoint.refresh=login.refresh;assertTrue(login.run(30000).isOk());List<String> lines=login.exported("success");assertTrue(lines.stream().anyMatch(x->x.contains("stage=token")&&x.contains("http_status=200")));assertTrue(lines.stream().anyMatch(x->x.contains("stage=profile")&&x.contains("http_status=200")));}
    }
    @Test public void authorizationRefusalKeepsOriginal401InOrdinaryExport() throws Exception {
        try(Endpoint endpoint=new Endpoint();Login login=new Login(endpoint.url())){endpoint.tokenStatus=401;assertFalse(login.run(30000).isOk());assertTrue(login.exported("authorization").stream().anyMatch(x->x.contains("http_status=401")));}
    }
    @Test public void serverFailureKeepsOriginal503InOrdinaryExport() throws Exception {
        try(Endpoint endpoint=new Endpoint();Login login=new Login(endpoint.url())){endpoint.tokenStatus=503;assertFalse(login.run(30000).isOk());assertTrue(login.exported("server").stream().anyMatch(x->x.contains("http_status=503")));}
    }
    @Test public void malformedTokenIsDistinguishableFromHttpFailure() throws Exception {
        try(Endpoint endpoint=new Endpoint();Login login=new Login(endpoint.url())){endpoint.badToken=true;assertFalse(login.run(30000).isOk());assertTrue(login.exported("invalid_response").stream().anyMatch(x->x.contains("event=invalid_response")&&x.contains("http_status=200")));}
    }
    @Test public void malformedProfileIsReportedWhileLegacyCallbackMappingStaysIntact() throws Exception {
        try(Endpoint endpoint=new Endpoint();Login login=new Login(endpoint.url())){endpoint.badProfile=true;assertTrue("This diagnostic task preserves the legacy HTTP-200 callback",login.run(30000).isOk());assertTrue(login.exported("invalid_response").stream().anyMatch(x->x.contains("stage=profile")&&x.contains("event=invalid_response")));}
    }
    @Test public void dnsFailureHasNoInventedHttpResponse() throws Exception {
        try(Login login=new Login("https://ngid-night.invalid")){assertFalse(login.run(NetworkUtil.TIMEOUT_CONNECTION+30000L).isOk());assertTrue(login.exported("dns").stream().anyMatch(x->x.contains("category=dns")&&x.contains("http_status=unknown")));}
    }
    @Test public void tlsFailureIsDifferentFromAuthorizationAndDns() throws Exception {
        try(Endpoint endpoint=new Endpoint()){endpoint.tlsFailure=true;try(Login login=new Login(endpoint.url())){assertFalse(login.run(NetworkUtil.TIMEOUT_CONNECTION+30000L).isOk());login.exported("tls");}}
    }
    @Test public void actualReadTimeoutHasPairedResultWithoutChangingDeviceRadios() throws Exception {
        try(Endpoint endpoint=new Endpoint();Login login=new Login(endpoint.url())){endpoint.stall=true;assertFalse(login.run(NetworkUtil.TIMEOUT_SOCKET+45000L).isOk());login.exported("timeout");}
    }
}

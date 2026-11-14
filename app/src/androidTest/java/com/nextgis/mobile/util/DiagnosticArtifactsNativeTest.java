package com.nextgis.mobile.util;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import com.nextgis.maplib.util.UnderlayFiles;
import com.nextgis.mobile.reliability.EmulatorNetworkFixture;
import io.sentry.*;
import io.sentry.cache.EnvelopeCache;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DiagnosticArtifactsNativeTest {
    private Context context;
    private EmulatorNetworkFixture network;
    private DiagnosticArtifacts artifacts;
    private File root;
    @Before public void isolated() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();VerificationEnvironment.requireIsolated(context);
        assertTrue("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE));
        network=new EmulatorNetworkFixture(context);network.connected(false);
        root=new File(context.getNoBackupFilesDir(),"artifact-fixture-"+UUID.randomUUID());assertTrue(root.mkdirs());
    }
    @After public void close() throws Exception {
        if(context!=null) {
            WorkManager manager=WorkManager.getInstance(context);
            manager.cancelUniqueWork(AppDiagnostics.RETRY_WORK).getResult().get(10,TimeUnit.SECONDS);
            manager.cancelUniqueWork(AppDiagnostics.RETRY_WORK+"-upload").getResult().get(10,TimeUnit.SECONDS);
            await(()->manager.getWorkInfosForUniqueWork(AppDiagnostics.RETRY_WORK+"-upload").get(5,TimeUnit.SECONDS).stream()
                    .noneMatch(w->w.getState()==androidx.work.WorkInfo.State.RUNNING));
        }
        if(artifacts!=null)DiagnosticArtifacts.endTests(artifacts);
        if(network!=null)network.close();
        if(root!=null&&root.exists()) {UnderlayFiles.deleteChildTree(root.getParentFile(),root);assertFalse(root.exists());}
    }
    private interface Check {boolean ready() throws Exception;}
    private static void await(Check check) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+30000;
        do {if(check.ready())return;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);
        fail("Diagnostic delivery did not reach the expected state");
    }
    static class Endpoint implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        final List<byte[]> archives=new CopyOnWriteArrayList<>();
        final List<String> hashes=new CopyOnWriteArrayList<>();
        final AtomicInteger events=new AtomicInteger(),redirects=new AtomicInteger();
        volatile int status=201;
        volatile boolean wrongAcknowledgement;
        volatile Throwable error;
        final Thread thread;
        private static String line(InputStream input) throws IOException {
            ByteArrayOutputStream out=new ByteArrayOutputStream();int value;
            while((value=input.read())!=-1) {if(value=='\n')return out.toString("US-ASCII").replaceFirst("\r$","");
                if(out.size()>8192)throw new IOException("Fixture header too large");out.write(value);}
            return out.size()==0?null:out.toString("US-ASCII");
        }
        Endpoint() throws IOException {
            thread=new Thread(()->{
                while(!server.isClosed())try(Socket socket=server.accept()) {
                    socket.setSoTimeout(10000);InputStream input=socket.getInputStream();String request=line(input),header;int length=0;
                    String declared="";
                    while((header=line(input))!=null&&!header.isEmpty()) {
                        int split=header.indexOf(':');if(split<0)throw new IOException("Invalid fixture header");
                        String key=header.substring(0,split).toLowerCase(Locale.ROOT),value=header.substring(split+1).trim();
                        if(key.equals("content-length"))length=Integer.parseInt(value);
                        if(key.equals("x-content-sha256"))declared=value;
                    }
                    if(length<0||length>16*1024*1024)throw new IOException("Fixture body too large");
                    byte[] body=new byte[length];int done=0,count;
                    while(done<length&&(count=input.read(body,done,length-done))>0)done+=count;
                    if(done!=length)throw new EOFException();
                    boolean archive=request.startsWith("PUT /api/");int code=archive?status:200;String response="{}";
                    if(archive) {
                        String id=request.split(" ")[1].substring("/api/".length());
                        assertEquals(32,id.length());assertTrue(request.startsWith("PUT "));
                        String actual=UnderlayFiles.hex(UnderlayFiles.digest().digest(body));assertEquals(actual,declared);
                        archives.add(body);hashes.add(actual);
                        response=new JSONObject().put("event_id",id).put("sha256",wrongAcknowledgement?"0".repeat(64):actual).toString();
                    }else if(request.contains("/stolen"))redirects.incrementAndGet();else events.incrementAndGet();
                    byte[] answer=response.getBytes(StandardCharsets.UTF_8);
                    String redirect=code==302?"Location: "+url()+"/stolen\r\n":"";
                    socket.getOutputStream().write(("HTTP/1.1 "+code+" Result\r\n"+redirect+"Content-Type: application/json\r\nContent-Length: "+answer.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(answer);socket.getOutputStream().flush();
                }catch(SocketException stopped) {if(!server.isClosed())error=stopped;}
                catch(Throwable failed) {error=failed;}
            },"isolated-artifact-endpoint");thread.start();
        }
        String url() {return "http://127.0.0.1:"+server.getLocalPort();}
        @Override public void close() throws Exception {server.close();thread.join(10000);assertFalse(thread.isAlive());assertNull(error);}
    }
    private SentryOptions options() {
        SentryOptions options=new SentryOptions();options.setSerializer(new JsonSerializer(options));
        File cache=new File(root,"sdk");assertTrue("Create the SDK cache as its real initializer does",cache.mkdirs());
        options.setCacheDirPath(cache.getPath());options.setEnvelopeDiskCache(new EnvelopeCache(options,options.getCacheDirPath(),32));
        options.setTransportGate(()->new com.nextgis.maplib.util.NetworkUtil(context).isNetworkAvailable());
        options.setConnectionTimeoutMillis(5000);options.setReadTimeoutMillis(5000);return options;
    }
    @Test public void actualTransportAndWorkerPreserveOfflineBytesAndAckSurvivesNewInstanceReplay() throws Exception {
        try(Endpoint endpoint=new Endpoint()) {
            File queue=new File(root,"queue");artifacts=DiagnosticArtifacts.forTests(context,endpoint.url(),"synthetic-artifact-token",queue);
            SentryOptions options=options();SentryEvent event=new SentryEvent(new IllegalStateException("Synthetic approved diagnostic"));
            SentryEnvelope envelope=SentryEnvelope.from(options.getSerializer(),event,null);
            try(DiagnosticsTransport transport=new DiagnosticsTransport(options,new RequestDetails(endpoint.url()+"/envelope",Collections.emptyMap()))) {
                transport.send(envelope,new Hint());transport.flush(10000);
                assertTrue("The archive must be durable while offline",artifacts.pending());
                assertTrue("The SDK envelope must remain in its real disk cache",options.getEnvelopeDiskCache().iterator().hasNext());
                assertEquals(0,endpoint.events.get());
                File zip=new File(queue,event.getEventId()+".zip");byte[] original=Files.readAllBytes(zip.toPath());
                assertTrue(endpoint.archives.isEmpty());network.connected(true);transport.send(envelope,new Hint());transport.flush(10000);
                await(()->!artifacts.pending()&&endpoint.events.get()>0);
                assertFalse(options.getEnvelopeDiskCache().iterator().hasNext());assertFalse(endpoint.archives.isEmpty());
                for(byte[] received:endpoint.archives)assertArrayEquals(original,received);
                int attempts=endpoint.archives.size();DiagnosticArtifacts.endTests(artifacts);
                artifacts=DiagnosticArtifacts.forTests(context,endpoint.url(),"synthetic-artifact-token",queue);
                event.setExtra("replayed_after_update",true);artifacts.store(options.getSerializer(),SentryEnvelope.from(options.getSerializer(),event,null));
                assertTrue(artifacts.retry());assertFalse(artifacts.pending());assertEquals(attempts,endpoint.archives.size());
            }
        }
    }
    @Test public void falseAcknowledgementRedirectAndServerFailureNeverConsumeTheImmutableArchive() throws Exception {
        try(Endpoint endpoint=new Endpoint()) {
            artifacts=DiagnosticArtifacts.forTests(context,endpoint.url(),"synthetic-artifact-token",new File(root,"queue"));
            SentryOptions options=options();SentryEvent event=new SentryEvent(new IllegalStateException("Synthetic negative ACK"));
            artifacts.store(options.getSerializer(),SentryEnvelope.from(options.getSerializer(),event,null));
            endpoint.wrongAcknowledgement=true;network.connected(true);assertFalse(artifacts.retry());assertTrue(artifacts.pending());
            endpoint.status=302;endpoint.wrongAcknowledgement=false;assertFalse(artifacts.retry());assertEquals(0,endpoint.redirects.get());
            endpoint.status=503;assertFalse(artifacts.retry());assertTrue(artifacts.pending());
            endpoint.status=409;assertFalse(artifacts.retry());assertTrue(artifacts.pending());
            endpoint.status=201;assertTrue(artifacts.retry());assertFalse(artifacts.pending());
            assertEquals(1,new HashSet<>(endpoint.hashes).size());
        }
    }
}

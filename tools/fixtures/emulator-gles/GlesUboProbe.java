package org.geonical.emulator.glesprobe;
import android.opengl.EGL14;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedHashSet;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GlesUboProbe {
    private static final String VERTEX = "#version 300 es\nlayout(std140) uniform Shared { vec4 shared_color; };\nlayout(location=0) in vec2 position;\nout float from_vertex;\nvoid main() {\n    gl_Position = vec4(position, 0, 1);\n    from_vertex = shared_color.r;\n}\n";
    private static final String HEADER = "#version 300 es\nprecision highp float;\n";
    private static final String SHARED = "layout(std140) uniform Shared { vec4 shared_color; };\n";
    private static final String FRAGMENT_ONLY = "layout(std140) uniform FragmentOnly { vec4 fragment_color; };\n";
    private static final String BODY = "in float from_vertex;\nout vec4 color;\nvoid main() {\n    color = vec4(shared_color.g, fragment_color.r, from_vertex, 1);\n}\n";

    private static void require(boolean okay, String description) {
        if (!okay) throw new IllegalStateException(description);
    }

    private static void egl(boolean okay, String description) {
        require(okay, description + ": " + Integer.toHexString(EGL14.eglGetError()));
    }

    private static int shader(int kind, String source) {
        int shader = GLES30.glCreateShader(kind);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        require(status[0] == GLES30.GL_TRUE, GLES30.glGetShaderInfoLog(shader));
        return shader;
    }

    private static FloatBuffer floats(float... values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private static int uniform(int program, int slot, int buffer, String name, float... values) {
        int index = GLES30.glGetUniformBlockIndex(program, name);
        require(index != -1, "Missing uniform block " + name);
        GLES30.glUniformBlockBinding(program, index, slot);
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, buffer);
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, values.length * 4, floats(values),
                GLES30.GL_STATIC_DRAW);
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, slot, buffer);
        return index;
    }

    private static JSONObject draw(String fragmentSource, boolean uniforms) throws Exception {
        int vertex = shader(GLES30.GL_VERTEX_SHADER, VERTEX);
        int fragment = shader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES30.glCreateProgram();
        int[] buffers = new int[3];
        try {
            GLES30.glAttachShader(program, vertex);
            GLES30.glAttachShader(program, fragment);
            GLES30.glLinkProgram(program);
            int[] status = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
            require(status[0] == GLES30.GL_TRUE, GLES30.glGetProgramInfoLog(program));
            GLES30.glUseProgram(program);
            GLES30.glGenBuffers(buffers.length, buffers, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[2]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 24,
                    floats(-.9f, -.9f, .9f, -.9f, 0f, .9f), GLES30.GL_STATIC_DRAW);
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0);
            GLES30.glEnableVertexAttribArray(0);
            JSONArray indices = new JSONArray();
            if (uniforms) {
                indices.put(uniform(program, 0, buffers[0], "Shared", .7f, .2f, 0f, 0f));
                indices.put(uniform(program, 1, buffers[1], "FragmentOnly", .3f, .4f, 0f, 0f));
            }
            GLES30.glClearColor(0f, 0f, 0f, 0f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
            GLES30.glFinish();
            ByteBuffer pixel = ByteBuffer.allocateDirect(4);
            GLES30.glReadPixels(4, 4, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel);
            int error = GLES30.glGetError();
            require(error == GLES30.GL_NO_ERROR, "GL error " + Integer.toHexString(error));
            JSONArray channels = new JSONArray();
            for (int i = 0; i < 4; i++) channels.put(pixel.get(i) & 0xff);
            return new JSONObject().put("rgba", channels).put("block_indices", indices);
        } finally {
            GLES30.glDeleteBuffers(buffers.length, buffers, 0);
            GLES30.glUseProgram(0);
            GLES30.glDeleteProgram(program);
            GLES30.glDeleteShader(vertex);
            GLES30.glDeleteShader(fragment);
        }
    }

    private static JSONArray loadedLibraries() throws Exception {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int start = line.indexOf('/');
                if (start < 0) continue;
                String path = line.substring(start);
                if (path.contains("libEGL") || path.contains("libGLES") || path.contains("libvulkan")) {
                    paths.add(path);
                }
            }
        }
        return new JSONArray(paths);
    }

    public static void main(String[] arguments) throws Exception {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        egl(display != EGL14.EGL_NO_DISPLAY, "eglGetDisplay");
        int[] version = new int[2];
        egl(EGL14.eglInitialize(display, version, 0, version, 1), "eglInitialize");
        int[] attributes = {EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE};
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        egl(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
                && count[0] == 1, "eglChooseConfig");
        EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        egl(context != EGL14.EGL_NO_CONTEXT, "eglCreateContext");
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                new int[] {EGL14.EGL_WIDTH, 8, EGL14.EGL_HEIGHT, 8, EGL14.EGL_NONE}, 0);
        egl(surface != EGL14.EGL_NO_SURFACE, "eglCreatePbufferSurface");
        try {
            egl(EGL14.eglMakeCurrent(display, surface, surface, context), "eglMakeCurrent");
            GLES30.glViewport(0, 0, 8, 8);
            JSONObject proof = new JSONObject()
                    .put("vendor", GLES30.glGetString(GLES30.GL_VENDOR))
                    .put("renderer", GLES30.glGetString(GLES30.GL_RENDERER))
                    .put("version", GLES30.glGetString(GLES30.GL_VERSION))
                    .put("loaded_libraries", loadedLibraries())
                    .put("constant_control", draw(HEADER +
                            "out vec4 color; void main() { color = vec4(0.1, 0.5, 0.9, 1); }\n", false))
                    .put("fragment_first", draw(HEADER + FRAGMENT_ONLY + SHARED + BODY, true))
                    .put("shared_first", draw(HEADER + SHARED + FRAGMENT_ONLY + BODY, true));
            System.out.println(proof.toString());
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, surface);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }
    }
}

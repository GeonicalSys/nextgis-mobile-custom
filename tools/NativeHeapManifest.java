/* Developer-only APK preparation. Never shipped in the Android application. */
import com.android.aapt.Resources;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/** Uses installed official AAPT2 protobuf/apksig APIs. No secrets in arguments or output. */
public final class NativeHeapManifest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final int SHELL_ATTRIBUTE = 16844180;

    private static Resources.XmlNode read(Path path) throws Exception {
        if (Files.size(path) > 2 * 1024 * 1024) throw new IllegalArgumentException("Manifest size");
        return Resources.XmlNode.parseFrom(Files.readAllBytes(path));
    }

    private static Resources.XmlElement.Builder application(Resources.XmlNode.Builder root) {
        if (!root.hasElement() || !root.getElement().getName().equals("manifest"))
            throw new IllegalArgumentException("Manifest root");
        Resources.XmlElement.Builder result = null;
        for (Resources.XmlNode.Builder child : root.getElementBuilder().getChildBuilderList()) {
            if (child.hasElement() && child.getElement().getName().equals("application")) {
                if (result != null) throw new IllegalArgumentException("Duplicate application");
                result = child.getElementBuilder();
            }
        }
        if (result == null) throw new IllegalArgumentException("Missing application");
        return result;
    }

    private static boolean isProfileable(Resources.XmlNode child) {
        return child.hasElement() && child.getElement().getNamespaceUri().isEmpty()
                && child.getElement().getName().equals("profileable");
    }

    private static Resources.XmlNode patch(Resources.XmlNode original) {
        Resources.XmlNode.Builder root = original.toBuilder();
        Resources.XmlElement.Builder app = application(root);
        for (Resources.XmlNode child : app.getChildList())
            if (isProfileable(child)) throw new IllegalArgumentException("Already profileable");
        app.addChild(Resources.XmlNode.newBuilder().setElement(Resources.XmlElement.newBuilder()
                .setName("profileable").addAttribute(Resources.XmlAttribute.newBuilder()
                        .setNamespaceUri(ANDROID).setName("shell").setResourceId(SHELL_ATTRIBUTE)
                        .setValue("true").setCompiledItem(Resources.Item.newBuilder()
                                .setPrim(Resources.Primitive.newBuilder().setBooleanValue(true))))));
        return root.build();
    }

    /** Ignore source positions/raw spelling; preserve every executable XML value and child order. */
    private static Object canonical(Resources.XmlNode node) {
        if (!node.hasElement()) return List.of("text", node.getText());
        Resources.XmlElement element = node.getElement();
        List<Object> attributes = new ArrayList<>();
        List<Resources.XmlAttribute> sorted = new ArrayList<>(element.getAttributeList());
        sorted.sort(Comparator.comparing(Resources.XmlAttribute::getNamespaceUri)
                .thenComparing(Resources.XmlAttribute::getName).thenComparingInt(Resources.XmlAttribute::getResourceId));
        for (Resources.XmlAttribute attr : sorted)
            attributes.add(Arrays.asList(attr.getNamespaceUri(), attr.getName(), attr.getResourceId(),
                    attr.hasCompiledItem() ? attr.getCompiledItem() : attr.getValue()));
        List<Object> namespaces = new ArrayList<>();
        List<Resources.XmlNamespace> declarations = new ArrayList<>(element.getNamespaceDeclarationList());
        declarations.sort(Comparator.comparing(Resources.XmlNamespace::getPrefix).thenComparing(Resources.XmlNamespace::getUri));
        for (Resources.XmlNamespace declaration : declarations)
            namespaces.add(List.of(declaration.getPrefix(), declaration.getUri()));
        List<Object> children = new ArrayList<>();
        for (Resources.XmlNode child : element.getChildList()) children.add(canonical(child));
        return List.of("element", element.getNamespaceUri(), element.getName(), namespaces, attributes, children);
    }

    private static void verifyManifest(Resources.XmlNode before, Resources.XmlNode after) {
        Resources.XmlNode.Builder root = after.toBuilder();
        Resources.XmlElement.Builder app = application(root);
        int found = -1;
        for (int i = 0; i < app.getChildCount(); i++) if (isProfileable(app.getChild(i))) {
            if (found >= 0) throw new IllegalArgumentException("Duplicate profileable");
            found = i;
        }
        if (found < 0) throw new IllegalArgumentException("Missing profileable");
        Resources.XmlElement profile = app.getChild(found).getElement();
        if (profile.getAttributeCount() != 1 || profile.getChildCount() != 0)
            throw new IllegalArgumentException("Unexpected profileable content");
        Resources.XmlAttribute attr = profile.getAttribute(0);
        if (!attr.getNamespaceUri().equals(ANDROID) || !attr.getName().equals("shell")
                || attr.getResourceId() != SHELL_ATTRIBUTE || !attr.hasCompiledItem()
                || !attr.getCompiledItem().hasPrim() || !attr.getCompiledItem().getPrim().getBooleanValue())
            throw new IllegalArgumentException("Profileable is not shell=true");
        app.removeChild(found);
        if (!canonical(before).equals(canonical(root.build())))
            throw new IllegalArgumentException("Manifest semantics changed outside profileable");
    }

    private static String digest(X509Certificate certificate) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static List<X509Certificate> certificates(Path apk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk.toFile()).build().verify();
        if (!result.isVerified() || result.getSignerCertificates().size() != 1)
            throw new IllegalArgumentException("Expected one verified APK signer");
        return result.getSignerCertificates();
    }

    private static void sign(Path input, Path output, Path propertiesFile, Path baseline, int minimumSdk,
                             Path signingBase) throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(propertiesFile)) { properties.load(stream); }
        for (String key : List.of("GEONICAL_STORE_FILE", "GEONICAL_STORE_PASSWORD", "GEONICAL_KEY_ALIAS", "GEONICAL_KEY_PASSWORD"))
            if (properties.getProperty(key) == null || properties.getProperty(key).isEmpty())
                throw new IllegalArgumentException("Incomplete signing properties");
        Path store = Path.of(properties.getProperty("GEONICAL_STORE_FILE"));
        if (!store.isAbsolute()) store = signingBase.resolve(store);
        char[] storePassword = properties.getProperty("GEONICAL_STORE_PASSWORD").toCharArray();
        char[] keyPassword = properties.getProperty("GEONICAL_KEY_PASSWORD").toCharArray();
        try {
            KeyStore keys = KeyStore.getInstance(store.toFile(), storePassword);
            String alias = properties.getProperty("GEONICAL_KEY_ALIAS");
            List<X509Certificate> chain = new ArrayList<>();
            for (java.security.cert.Certificate certificate : keys.getCertificateChain(alias))
                chain.add((X509Certificate) certificate);
            if (chain.isEmpty() || !digest(chain.get(0)).equals(digest(certificates(baseline).get(0))))
                throw new IllegalArgumentException("Signing certificate differs from baseline");
            PrivateKey key = (PrivateKey) keys.getKey(alias, keyPassword);
            ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("GEONICAL", key, chain).build();
            new ApkSigner.Builder(List.of(signer)).setInputApk(input.toFile()).setOutputApk(output.toFile())
                    .setMinSdkVersion(minimumSdk).setV1SigningEnabled(true).setV2SigningEnabled(true)
                    .setV3SigningEnabled(true).setV4SigningEnabled(false).setOtherSignersSignaturesPreserved(false)
                    .setLibraryPageAlignmentBytes(16384).build().sign();
            if (!digest(certificates(output).get(0)).equals(digest(certificates(baseline).get(0))))
                throw new IllegalArgumentException("Output certificate differs from baseline");
        } finally {
            Arrays.fill(storePassword, '\0'); Arrays.fill(keyPassword, '\0'); properties.clear();
        }
    }

    public static void main(String[] args) {
        try {
            switch (args[0]) {
                case "patch" -> Files.write(Path.of(args[2]), patch(read(Path.of(args[1]))).toByteArray());
                case "verify-manifest" -> {
                    verifyManifest(read(Path.of(args[1])), read(Path.of(args[2])));
                    System.out.println("manifest-only-profileable");
                }
                case "certificate" -> System.out.println(digest(certificates(Path.of(args[1])).get(0)));
                case "sign" -> sign(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]),
                        Integer.parseInt(args[5]), Path.of(args[6]));
                default -> throw new IllegalArgumentException("Unknown operation");
            }
        } catch (Exception failure) {
            // KeyStore/APK library messages can contain private signing paths or aliases.
            System.err.println("NativeHeapManifest operation failed: " + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
}

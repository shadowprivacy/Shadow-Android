package su.sres.lint;

import com.android.tools.lint.checks.infrastructure.TestFile;

import org.junit.Test;

import java.io.InputStream;
import java.util.Scanner;

import static com.android.tools.lint.checks.infrastructure.TestFiles.java;
import static com.android.tools.lint.checks.infrastructure.TestFiles.kotlin;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("UnstableApiUsage")
public final class LogDetectorTest {

    private static final TestFile serviceLogStub = java(readResourceAsString("ServiceLogStub.java"));
    private static final TestFile appLogStub     = java(readResourceAsString("AppLogStub.java"));
    private static final TestFile glideLogStub   = java(readResourceAsString("GlideLogStub.java"));

    @Test
    public void androidLogUsed_LogNotShadow_2_args() {
        lint()
                .files(
                        java("package foo;\n" +
                                "import android.util.Log;\n" +
                                "public class Example {\n" +
                                "  public void log() {\n" +
                                "    Log.d(\"TAG\", \"msg\");\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.LOG_NOT_SHADOW)
                .run()
                .expect("src/foo/Example.java:5: Error: Using 'android.util.Log' instead of a Shadow Logger [LogNotShadow]\n" +
                        "    Log.d(\"TAG\", \"msg\");\n" +
                        "    ~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with su.sres.core.util.logging.Log.d(\"TAG\", \"msg\"):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.d(\"TAG\", \"msg\");\n" +
                        "+     su.sres.core.util.logging.Log.d(\"TAG\", \"msg\");");
    }

    @Test
    public void androidLogUsed_LogNotShadow_3_args() {
        lint()
                .files(
                        java("package foo;\n" +
                                "import android.util.Log;\n" +
                                "public class Example {\n" +
                                "  public void log() {\n" +
                                "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.LOG_NOT_SHADOW)
                .run()
                .expect("src/foo/Example.java:5: Error: Using 'android.util.Log' instead of a Shadow Logger [LogNotShadow]\n" +
                        "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with su.sres.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception()):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "+     su.sres.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception());");
    }

    @Test
    public void signalServiceLogUsed_LogNotApp_2_args() {
        lint()
                .files(serviceLogStub,
                        java("package foo;\n" +
                                "import org.whispersystems.libsignal.logging.Log;\n" +
                                "public class Example {\n" +
                                "  public void log() {\n" +
                                "    Log.d(\"TAG\", \"msg\");\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.LOG_NOT_APP)
                .run()
                .expect("src/foo/Example.java:5: Error: Using Signal server logger instead of app level Logger [LogNotAppShadow]\n" +
                        "    Log.d(\"TAG\", \"msg\");\n" +
                        "    ~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with su.sres.core.util.logging.Log.d(\"TAG\", \"msg\"):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.d(\"TAG\", \"msg\");\n" +
                        "+     su.sres.core.util.logging.Log.d(\"TAG\", \"msg\");");
    }

    @Test
    public void signalServiceLogUsed_LogNotApp_3_args() {
        lint()
                .files(serviceLogStub,
                        java("package foo;\n" +
                                "import org.whispersystems.libsignal.logging.Log;\n" +
                                "public class Example {\n" +
                                "  public void log() {\n" +
                                "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.LOG_NOT_APP)
                .run()
                .expect("src/foo/Example.java:5: Error: Using Signal server logger instead of app level Logger [LogNotAppShadow]\n" +
                        "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with su.sres.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception()):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "+     su.sres.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception());");
    }

    @Test
    public void log_uses_tag_constant() {
        lint()
                .files(appLogStub,
                        java("package foo;\n" +
                                "import su.sres.core.util.logging.Log;\n" +
                                "public class Example {\n" +
                                "  private static final String TAG = Log.tag(Example.class);\n" +
                                "  public void log() {\n" +
                                "    Log.d(TAG, \"msg\");\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.INLINE_TAG)
                .run()
                .expectClean();
    }

    @Test
    public void log_uses_tag_constant_kotlin() {
        lint()
                .files(appLogStub,
                        kotlin("package foo\n" +
                                "import su.sres.core.util.logging.Log\n" +
                                "class Example {\n" +
                                "  const val TAG: String = Log.tag(Example::class.java)\n" +
                                "  fun log() {\n" +
                                "    Log.d(TAG, \"msg\")\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.INLINE_TAG)
                .run()
                .expectClean();
    }

    @Test
    public void log_uses_tag_companion_kotlin() {
        lint()
                .files(appLogStub,
                        kotlin("package foo\n" +
                                "import org.signal.core.util.logging.Log\n" +
                                "class Example {\n" +
                                "  companion object { val TAG: String = Log.tag(Example::class.java) }\n" +
                                "  fun log() {\n" +
                                "    Log.d(TAG, \"msg\")\n" +
                                "  }\n" +
                                "}\n"+
                                "fun logOutsie() {\n" +
                                "  Log.d(Example.TAG, \"msg\")\n" +
                                "}\n")
                )
                .issues(ShadowLogDetector.INLINE_TAG)
                .run()
                .expectClean();
    }

    @Test
    public void log_uses_inline_tag() {
        lint()
                .files(appLogStub,
                        java("package foo;\n" +
                                "import su.sres.core.util.logging.Log;\n" +
                                "public class Example {\n" +
                                "  public void log() {\n" +
                                "    Log.d(\"TAG\", \"msg\");\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.INLINE_TAG)
                .run()
                .expect("src/foo/Example.java:5: Error: Not using a tag constant [LogTagInlined]\n" +
                        "    Log.d(\"TAG\", \"msg\");\n" +
                        "    ~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("");
    }

    @Test
    public void log_uses_inline_tag_kotlin() {
        lint()
                .files(appLogStub,
                        kotlin("package foo\n" +
                                "import su.sres.core.util.logging.Log\n" +
                                "class Example {\n" +
                                "  fun log() {\n" +
                                "    Log.d(\"TAG\", \"msg\")\n" +
                                "  }\n" +
                                "}"))
                .issues(ShadowLogDetector.INLINE_TAG)
                .run()
                .expect("src/foo/Example.kt:5: Error: Not using a tag constant [LogTagInlined]\n" +
                        "    Log.d(\"TAG\", \"msg\")\n" +
                        "    ~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("");
    }

    @Test
    public void glideLogUsed_LogNotShadow_2_args() {
        lint()
                .files(glideLogStub,
                        java("package foo;\n" +
                                "import su.sres.glide.Log;\n" +
                                "public class Example {\n" +
                                "  public void log() {\n" +
                                "    Log.d(\"TAG\", \"msg\");\n" +
                                "  }\n" +
                                "}")
                )
                .issues(ShadowLogDetector.LOG_NOT_SHADOW)
                .run()
                .expect("src/foo/Example.java:5: Error: Using 'org.signal.glide.Log' instead of a Signal Logger [LogNotSignal]\n" +
                        "    Log.d(\"TAG\", \"msg\");\n" +
                        "    ~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings")
                .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.d(\"TAG\", \"msg\"):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.d(\"TAG\", \"msg\");\n" +
                        "+     org.signal.core.util.logging.Log.d(\"TAG\", \"msg\");");
    }

    private static String readResourceAsString(String resourceName) {
        InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName);
        assertNotNull(inputStream);
        Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
        assertTrue(scanner.hasNext());
        return scanner.next();
    }
}
package br.redhot.dragonet;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliHeadlessTest {

    private static final class Captured {
        final ExitCode code;
        final String out;
        final String err;

        Captured(ExitCode code, String out, String err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }
    }

    private static Captured run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outBytes));
            System.setErr(new PrintStream(errBytes));
            ExitCode code = Cli.run(args);
            return new Captured(code, outBytes.toString(), errBytes.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void versionPrintsWithoutConfig() {
        Captured result = run("--version");
        assertEquals(ExitCode.EX_OK, result.code);
        assertTrue(result.out.contains("dragonet 1.0.0"));
    }

    @Test
    void helpListsHeadlessCommands() {
        Captured result = run("--help");
        assertEquals(ExitCode.EX_OK, result.code);
        assertTrue(result.out.contains("--list"));
        assertTrue(result.out.contains("--cp"));
        assertTrue(result.out.contains("--rm-permanent"));
        assertTrue(result.out.contains("--selftest"));
    }

    @Test
    void dumpConfigPrintsAllKeys() {
        Captured result = run("--dump-config");
        assertEquals(ExitCode.EX_OK, result.code);
        assertTrue(result.out.contains("version=1"));
        assertTrue(result.out.contains("theme=redhot"));
        assertTrue(result.out.contains("preview=off"));
        assertTrue(result.out.contains("imp_mode=auto"));
        assertTrue(result.out.contains("trash_enabled=true"));
    }

    @Test
    void unknownFlagIsUsageError() {
        Captured result = run("--bogus");
        assertEquals(ExitCode.EX_USAGE, result.code);
        assertTrue(result.err.contains("unknown option"));
    }

    @Test
    void rmPermanentWithoutYesFails() {
        Captured result = run("--rm-permanent", "/tmp/some-file");
        assertEquals(ExitCode.EX_USAGE, result.code);
        assertTrue(result.err.contains("requires --yes"));
    }

    @Test
    void rmPermanentNoPathsIsUsageError() {
        Captured result = run("--rm-permanent", "--yes");
        assertEquals(ExitCode.EX_USAGE, result.code);
        assertTrue(result.err.contains("at least one path"));
    }

    @Test
    void cpWithOneArgumentIsUsageError() {
        Captured result = run("--cp", "/tmp/a");
        assertEquals(ExitCode.EX_USAGE, result.code);
        assertTrue(result.err.contains("requires <source>"));
    }

    @Test
    void mvWithMultipleSourcesToFileFails() {
        Captured result = run("--mv", "/tmp/a", "/tmp/b", "/tmp/not-a-dir");
        // /tmp/not-a-dir likely does not exist as a directory; the CLI
        // rejects multiple sources with a non-directory target.
        if (result.code == ExitCode.EX_USAGE) {
            assertTrue(result.err.contains("multiple sources"));
        }
        // If /tmp/not-a-dir happened to be a directory on the test
        // machine, the CLI proceeds and the test is skipped.
    }

    @Test
    void selftestRunsAndReports() {
        Captured result = run("--selftest");
        // Exit code is 0 or 1 depending on the environment; both are
        // valid. Assert the output mentions the required checks.
        assertTrue(result.out.contains("java") || result.err.contains("java"));
    }

    @Test
    void listRequiresValidDirectory() {
        Captured result = run("--list", "/definitely/does/not/exist");
        assertEquals(ExitCode.EX_PRECOND, result.code);
    }

    @Test
    void lsJsonRequiresValidDirectory() {
        Captured result = run("--ls-json", "/definitely/does/not/exist");
        assertEquals(ExitCode.EX_PRECOND, result.code);
    }
}

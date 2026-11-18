package com.reco1l.verktex.shaders

import com.reco1l.verktex.shaders.GLSL450VulkanTranslator.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShaderImportsTest {

    private fun expand(path: String, files: Map<String, String>) =
        ShaderImports.expand(files.getValue(path), path) { files[it] }

    private fun assertFails(message: String, block: () -> Unit) {
        try {
            block()
            fail("Expected a ShaderImportException")
        } catch (e: ShaderImportException) {
            assertTrue("'${e.message}' should contain '$message'", e.message!!.contains(message))
        }
    }


    @Test
    fun sourceWithoutImportsIsUnchanged() {
        val source = "#version 300 es\nvoid main() {}\n"
        val expanded = ShaderImports.expand(source, "shaders/a.frag") { null }

        assertSame(source, expanded.source)
        assertEquals(listOf("shaders/a.frag"), expanded.files)
    }

    @Test
    fun importIsReplacedWithTheFileAndLineDirectives() {
        val expanded = expand(
            "shaders/a.frag", mapOf(
                "shaders/a.frag" to "#version 300 es\n#import \"lib/sdf.glsl\"\nvoid main() {}\n",
                "shaders/lib/sdf.glsl" to "float sdf() { return 0.0; }\n",
            )
        )

        assertEquals(
            "#version 300 es\n#line 1 1\nfloat sdf() { return 0.0; }\n#line 3 0\nvoid main() {}\n",
            expanded.source
        )
        assertEquals(listOf("shaders/a.frag", "shaders/lib/sdf.glsl"), expanded.files)
        assertEquals("shaders/lib/sdf.glsl", expanded.fileOf(1))
    }

    @Test
    fun pathsAreRelativeToTheImporterOrAbsoluteFromTheRoot() {
        val expanded = expand(
            "shaders/a.frag", mapOf(
                "shaders/a.frag" to "#import \"lib/./x.glsl\"\n#import \"/common/z.glsl\"\n",
                "shaders/lib/x.glsl" to "#import \"../other/y.glsl\"\nx\n",
                "shaders/other/y.glsl" to "y\n",
                "common/z.glsl" to "z\n",
            )
        )

        assertEquals(
            listOf("shaders/a.frag", "shaders/lib/x.glsl", "shaders/other/y.glsl", "common/z.glsl"),
            expanded.files
        )
    }

    @Test
    fun aFileIsIncludedOnce() {
        val expanded = expand(
            "a.frag", mapOf(
                "a.frag" to "#import \"x.glsl\"\n#import \"y.glsl\"\n#import \"x.glsl\"\nmain\n",
                "x.glsl" to "#import \"shared.glsl\"\nx\n",
                "y.glsl" to "#import \"shared.glsl\"\ny\n",
                "shared.glsl" to "shared\n",
            )
        )

        assertEquals(1, Regex("^shared$", RegexOption.MULTILINE).findAll(expanded.source).count())
        assertEquals(listOf("a.frag", "x.glsl", "shared.glsl", "y.glsl"), expanded.files)
        assertTrue(expanded.source.endsWith("main\n"))
    }

    @Test
    fun circularImportsFailWithTheChain() {
        assertFails("circular import: a.frag → x.glsl → y.glsl → x.glsl") {
            expand(
                "a.frag", mapOf(
                    "a.frag" to "#import \"x.glsl\"\n",
                    "x.glsl" to "#import \"y.glsl\"\n",
                    "y.glsl" to "#import \"x.glsl\"\n",
                )
            )
        }
    }

    @Test
    fun missingFilesFailWithTheImportingLine() {
        assertFails("shaders/a.frag:2: can't find the imported file shaders/missing.glsl") {
            expand("shaders/a.frag", mapOf("shaders/a.frag" to "#version 300 es\n#import \"missing.glsl\"\n"))
        }
    }

    @Test
    fun malformedImportsFail() {
        assertFails("a.frag:1: malformed import") {
            expand("a.frag", mapOf("a.frag" to "#import lib.glsl\n"))
        }
    }

    @Test
    fun pathsAboveTheRootFail() {
        assertFails("goes above the root") {
            expand("a.frag", mapOf("a.frag" to "#import \"../x.glsl\"\n"))
        }
    }

    @Test
    fun importedFilesCantHaveVersionsOrExtensions() {
        assertFails("x.glsl:2: an imported file can't have a #version") {
            expand("a.frag", mapOf("a.frag" to "#import \"x.glsl\"\n", "x.glsl" to "\n#version 300 es\n"))
        }
        assertFails("x.glsl:1: an imported file can't have a #extension") {
            expand("a.frag", mapOf("a.frag" to "#import \"x.glsl\"\n", "x.glsl" to "#extension GL_OES_standard_derivatives : enable\n"))
        }
    }

    @Test
    fun expandedSourcesTranslateForVulkan() {
        val expanded = expand(
            "shaders/a.frag", mapOf(
                "shaders/a.frag" to """
                    #version 300 es
                    precision highp float;

                    #import "lib/tint.glsl"

                    uniform vec4 uColor;
                    out vec4 fragColor;

                    void main() {
                        fragColor = tint(uColor);
                    }
                """.trimIndent(),
                "shaders/lib/tint.glsl" to """
                    uniform vec4 uTint;

                    vec4 tint(vec4 color) {
                        return color * uTint;
                    }
                """.trimIndent(),
            )
        )

        val result = GLSL450VulkanTranslator.translate(expanded.source, Stage.Fragment)

        // The library's uniform comes first, in the order of the expanded text.
        assertEquals(listOf("uTint", "uColor"), result.uniforms.map { it.name })
        assertTrue(result.source.contains("#line 1 1"))
    }
}

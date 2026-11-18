package com.reco1l.verktex.graphics

import com.reco1l.verktex.Provider
import com.reco1l.verktex.VerktexPlatformApi
import com.reco1l.verktex.shaders.ShaderImports

/**
 * Reads a shader program through [provider], expanding the imports of each stage with
 * [ShaderImports], the way the build does before compiling the SPIR-V. [readSpirv] returns the SPIR-V
 * compiled next to a stage's file, if any.
 *
 * @return null if a stage's file doesn't exist.
 * @throws com.reco1l.verktex.shaders.ShaderImportException if an import can't be expanded.
 */
@VerktexPlatformApi
fun readShaderSource(
    provider: Provider,
    vertexPath: String,
    fragmentPath: String,
    isExternal: Boolean,
    readSpirv: (path: String) -> ByteArray?,
): Shader.Source? {

    fun expand(path: String): ShaderImports.Expanded? {
        val text = provider.read(path, isExternal)?.decodeToString() ?: return null
        return ShaderImports.expand(text, path) { provider.read(it, isExternal)?.decodeToString() }
    }

    val vertex = expand(vertexPath) ?: return null
    val fragment = expand(fragmentPath) ?: return null

    return Shader.Source(
        vertex.source,
        fragment.source,
        spirvVertex = readSpirv(vertexPath),
        spirvFragment = readSpirv(fragmentPath),
        vertexFiles = vertex.files,
        fragmentFiles = fragment.files,
    )
}

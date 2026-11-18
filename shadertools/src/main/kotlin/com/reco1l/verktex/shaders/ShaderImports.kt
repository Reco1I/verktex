package com.reco1l.verktex.shaders

/**
 * Expands the `#import "path"` lines of a shader source, replacing each with the expanded text of
 * the file it names, so shaders can share code (e.g. `#import "lib/sdf.glsl"`).
 *
 * - A path is relative to the folder of the file that imports it, and absolute from the root the
 *   files are read from when it starts with `/`. `.` and `..` are resolved.
 * - A file is included once per expansion: a later import of it is left out, so two libraries can
 *   import a third one. A file importing itself, directly or not, is an error.
 * - An imported file can't have a `#version` or an `#extension`, which belong to the shader. It may
 *   declare functions, constants and uniforms: the expansion is the same wherever it runs, so the
 *   uniform layout derived from it is too. Inputs and outputs are better left to the shaders, their
 *   locations follow the order of the declarations.
 * - The text of an imported file is preceded by `#line 1 <n>`, `n` being its index in
 *   [Expanded.files], and followed by a `#line` back to the line after the import. Compile errors
 *   name the source string number and the line, which [Expanded.files] maps to a file.
 *
 * A source without imports is returned as it is.
 *
 * Expansion runs before any translation, at runtime as at build time: the SPIR-V compiled at build
 * time and the layout derived from the source at runtime must come from the same text.
 */
object ShaderImports {

    class Expanded(
        /**
         * The source with every import expanded.
         */
        val source: String,

        /**
         * The files of the expansion, the source string number of each in `#line` directives. The
         * first is the shader itself.
         */
        val files: List<String>,
    ) {

        /**
         * The file of the given source string number, e.g. to make a compile error point to it.
         */
        fun fileOf(sourceNumber: Int) = files.getOrNull(sourceNumber)
    }


    private val IMPORT = Regex("""^[ \t]*#import\s+"([^"]+)"[ \t]*(?://.*)?$""")
    private val ANY_IMPORT = Regex("""^[ \t]*#import\b""")
    private val FORBIDDEN_DIRECTIVE = Regex("""^[ \t]*#(version|extension)\b""")


    /**
     * Expands the imports of [source], the content of the file at [path]. [read] returns the content
     * of the file at a normalized path, or null if it doesn't exist.
     *
     * @throws ShaderImportException if an import can't be read, is malformed or imports itself.
     */
    fun expand(source: String, path: String, read: (String) -> String?): Expanded {
        if (source.lineSequence().none { ANY_IMPORT.containsMatchIn(it) }) {
            return Expanded(source, listOf(path))
        }

        val files = mutableListOf(path)
        val included = mutableSetOf(normalize(path, path))
        val output = StringBuilder()

        fun expandFile(text: String, filePath: String, fileIndex: Int, chain: List<String>) {
            val lines = text.split('\n')

            lines.forEachIndexed { index, rawLine ->
                val line = rawLine.removeSuffix("\r")
                val lineNumber = index + 1
                val isLast = index == lines.lastIndex

                if (!ANY_IMPORT.containsMatchIn(line)) {
                    output.append(rawLine)
                    if (!isLast) output.append('\n')
                    return@forEachIndexed
                }

                val match = IMPORT.matchEntire(line)
                    ?: throw ShaderImportException("$filePath:$lineNumber: malformed import, expected #import \"path\": ${line.trim()}")

                val target = resolve(filePath, match.groupValues[1], lineNumber)

                if (target in chain) {
                    throw ShaderImportException("$filePath:$lineNumber: circular import: ${(chain + target).joinToString(" → ")}")
                }

                // Already included: the line is kept empty, the following lines keep their numbers.
                if (!included.add(target)) {
                    if (!isLast) output.append('\n')
                    return@forEachIndexed
                }

                val content = read(target)
                    ?: throw ShaderImportException("$filePath:$lineNumber: can't find the imported file $target")

                content.lineSequence().forEachIndexed { importedIndex, importedLine ->
                    FORBIDDEN_DIRECTIVE.find(importedLine)?.let {
                        throw ShaderImportException(
                            "$target:${importedIndex + 1}: an imported file can't have a #${it.groupValues[1]}, it belongs to the shader that imports it"
                        )
                    }
                }

                val importedIndex = files.size
                files += target

                output.append("#line 1 ").append(importedIndex).append('\n')
                expandFile(content, target, importedIndex, chain + target)
                if (!output.endsWith('\n')) output.append('\n')

                // Back to the importing file, at the line after the import.
                output.append("#line ").append(lineNumber + 1).append(' ').append(fileIndex)
                if (!isLast) output.append('\n')
            }
        }

        expandFile(source, normalize(path, path), 0, listOf(normalize(path, path)))

        return Expanded(output.toString(), files)
    }

    private fun resolve(importer: String, importPath: String, line: Int): String {
        val joined = if (importPath.startsWith('/')) {
            importPath.removePrefix("/")
        } else {
            val folder = importer.substringBeforeLast('/', "")
            if (folder.isEmpty()) importPath else "$folder/$importPath"
        }

        return try {
            normalize(joined, importer)
        } catch (e: ShaderImportException) {
            throw ShaderImportException("$importer:$line: ${e.message}")
        }
    }

    private fun normalize(path: String, importer: String): String {
        val segments = ArrayDeque<String>()

        for (segment in path.replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull() ?: throw ShaderImportException("the path $path goes above the root, imported from $importer")
                else -> segments.addLast(segment)
            }
        }

        return segments.joinToString("/")
    }
}

/**
 * An import of a shader that can't be expanded, see [ShaderImports.expand].
 */
class ShaderImportException(message: String) : IllegalArgumentException(message)

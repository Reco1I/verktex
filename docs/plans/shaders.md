# Shaders

The built-in shaders are in `shared/assets/shaders/`: a program is a `.vert` and a `.frag` at the top
level of that folder (`BuiltIn.Shaders.Programs`), and the code they share is in `lib/`.

## The source language

Shaders are written in GLSL ES 3.00 (`#version 300 es`). Each backend translates them:

- OpenGL ES 3: as they are.
- OpenGL ES 2: `GLSLES100Translator` (androidMain), to GLSL ES 1.00. `flat` is dropped, `fwidth`
  enables `GL_OES_standard_derivatives`, `highp` falls back to `mediump` where it's missing.
- Desktop OpenGL 3.3: `GLSL330Translator` (desktopMain).
- Vulkan: `GLSL450VulkanTranslator` (`:shadertools`), to GLSL 4.50, compiled to SPIR-V at build time
  by the `compileVulkanShaders` tasks of `:android-natives` and `:desktop-natives`. At runtime the
  same translator reads the uniform layout from the GLSL: one std140 block per stage, of `float`,
  `int`, `vec2`, `vec3`, `vec4` and `mat4` uniforms, at most 256 bytes. See `vulkan-backend.md`.

## Imports

A line `#import "path"` is replaced by the file it names (`ShaderImports`, in `:shadertools`):

```glsl
#version 300 es
precision highp float;

out vec4 fragColor;

#import "lib/color.glsl"
#import "lib/clip.glsl"
```

- The path is relative to the importing file, or to the assets root when it starts with `/`
  (`/shaders/lib/sdf.glsl`).
- A file is included once: importing it again, from the shader or from another library, does
  nothing. A file importing itself, directly or not, fails with the chain of imports.
- An imported file can't have a `#version` or an `#extension`. It may declare functions, constants
  and uniforms. Avoid `in` and `out` there: their locations follow the order of the declarations.
- Imports go after the `precision` statement, which the imported functions need in fragment
  shaders, and after any `#extension`.
- Each imported file is preceded by `#line 1 <n>` and followed by a `#line` back to the importer, so
  compile errors give a source string number and a line in that file. The errors of the OpenGL
  backends list the files by number (`Shader.Source.describeFiles`).

The expansion happens before any translation, in the providers' `createShaderSource` at runtime
and in `compileShaders` at build time, so the SPIR-V and the layout read at runtime come from the
same text. Shader sources built from strings (`Shader.Source(vertex, fragment)`) aren't expanded.

Libraries end in `.glsl`, so the build doesn't compile them as shaders of their own:

| File | What it has |
|---|---|
| `lib/viewport.glsl` | `toNDC`: pixels from the top left to normalized device coordinates. |
| `lib/color.glsl` | `premultiply`: colors given to shaders have straight alpha, textures and the output are premultiplied. |
| `lib/sdf.glsl` | `roundedBoxSDF`: the signed distance to a box with a radius per corner. |
| `lib/clip.glsl` | `clipCoverage`: the coverage of the current clip, see below. |

## Clipping

`ClipMode` clips a component's content. A rectangle aligned to the screen is clipped with a scissor.
When the component has a corner radius, or a rotation or a skew, the shaders clip it instead
(`Renderer.pushClip`):

- The renderer keeps a stack of `ShaderClip`s: the clipped rectangle and its corner radii in the
  component's local space, and the matrix from the scene back to that space. A clip also pushes a
  scissor around it, so nothing is drawn past its bounding box and culling keeps working.
- Each draw command keeps the clip it was recorded under. Commands under the same clip merge as
  before, a different clip starts a new command.
- The backends give its uniforms (`uClipTransform`, `uClipTranslation`, `uClipRect`, `uClipRadii`)
  to the command's shader. Without a clip the defaults (`DefaultUniforms`) disable it.
- `lib/clip.glsl` brings the fragment (`gl_FragCoord`, flipped on OpenGL, whose origin is at the
  bottom left) back to the local space, and covers it by its distance to the rounded rectangle,
  antialiased over a pixel. It takes no derivatives, so shaders that don't otherwise use them don't
  need their extension on OpenGL ES 2.

Every built-in fragment shader except `blur.frag` multiplies its output by `clipCoverage()`, and
those that import it use `highp` floats: the clip works in pixels of the screen, which `mediump`
can't hold precisely. On OpenGL ES 2 `gl_FragCoord` itself is `mediump`, so clips far from the
origin of large screens can be off by a pixel there.

Limits:

- Only the innermost clip is applied by the shaders, an outer one limits what is drawn to its
  bounding box. Nested rounded clips are rare in UI, osu!framework makes the same trade-off.
- A shader pushed with `Renderer.pushShader` (`Canvas.shader`) is only clipped by the shape if it
  imports `lib/clip.glsl` and multiplies its output by `clipCoverage()`. Otherwise the scissor still
  limits it to the bounding box.
- Clipping is visual: presses on clipped content aren't affected.

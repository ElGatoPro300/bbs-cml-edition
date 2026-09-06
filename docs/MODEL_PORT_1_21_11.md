# Model rendering: 1.21.11 base pass

## Submission change

ModelInstance's ordinary geometry pass now submits BufferBuilder triangles through
BillboardRenderLayers. This binds the texture view, sampler, lightmap, overlay and
the vanilla entity shader's uniform buffers. Raw VAO/glUseProgram draws and the
compatibility BufferRenderer are no longer used for that base pass.

CubicLayerRenderer inherits CubicCubeRenderer's hierarchy transforms, cube pivots,
mesh transforms, normals and shape-key evaluation. It batches each group's geometry
by material, resolves the existing material textures and bone texture overrides, and
uses vertex colors for instance/form/group tint and alpha. Invisible groups and cubes
are omitted. Bone lighting and the model's culling setting are preserved.

BOBJModelVAO.renderLayer uses the existing CPU weighted skinning and processData hook
(including the simple-player joint deformation). It reads the resulting positions and
normals directly, without transform feedback or uploading legacy VBOs during that draw.
Triangles are split into runs by dominant bone for bone tint, lighting and texture
crossfades. The existing BOBJ root rotation and armature setup remain in ModelInstance.
GLTF/FBX models converted to BOBJ use the same route.

Form-level texture crossfades use the existing two-pass path for ordinary rendering;
the new vanilla shader cannot read the old TextureBlendActive uniform. Bone-level
crossfades likewise draw their two resolved textures explicitly.

Cached ModelForm thumbnails now scope a ModelPreviewRenderer target and orthographic
projection. The rendered image is copied into the existing thumbnail texture through
a temporary read framebuffer, whose binding is restored and whose object is deleted.
The preview target is closed when the cache is cleared. This prevents vanilla render
passes from ignoring the old raw scratch framebuffer and drawing into the world.

## Limits

This is a base rendering migration, not a completed migration of every model effect.
Picking/stencil and paint/color-grade overlay passes retain their legacy paths. Custom
per-fragment color grading, spatial masks, glow, PBR and Iris pack-specific behavior
are not certified by this change. Special effect redraws need further work.

CPU deformation and per-frame vertex upload prioritize restoring correct geometry.
Large meshes and many animated actors need performance measurement before considering
the port complete. Legacy VAO allocation still exists for the unmigrated passes.

## Validation

Final compileClientJava passed, and runClient initialized the latest build. The user's
screenshot shows player/alex geometry in the world and textured skin thumbnails.
This confirms base visibility, but does not establish animation or import correctness.
Next, animate arms/legs and check
the outer skin layer. Check a cubic model with shape keys, a model with multiple
materials, bone visibility/tint, texture crossfades and a skinned BOBJ/GLTF model.
Verify terrain occlusion and mirrored/nonuniform transforms separately from effects.
